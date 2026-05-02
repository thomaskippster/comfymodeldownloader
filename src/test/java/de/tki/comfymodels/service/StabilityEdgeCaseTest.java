package de.tki.comfymodels.service;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class StabilityEdgeCaseTest {

    @TempDir
    Path tempDir;

    private static class MockHttpResponse<T> implements HttpResponse<T> {
        private final int statusCode;
        private final T body;
        private final HttpHeaders headers;

        public MockHttpResponse(int statusCode, T body, long contentLength) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = HttpHeaders.of(Map.of("Content-Length", List.of(String.valueOf(contentLength))), (s1, s2) -> true);
        }

        @Override public int statusCode() { return statusCode; }
        @Override public HttpRequest request() { return null; }
        @Override public Optional<HttpResponse<T>> previousResponse() { return Optional.empty(); }
        @Override public HttpHeaders headers() { return headers; }
        @Override public T body() { return body; }
        @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
        @Override public java.net.URI uri() { return null; }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    }

    private static class StubConfigService extends ConfigService {
        private String modelsPath;
        private String archivePath;

        public StubConfigService(String modelsPath, String archivePath) {
            super(new EncryptionUtils(), new PathResolver());
            this.modelsPath = modelsPath;
            this.archivePath = archivePath;
        }

        @Override public String getModelsPath() { return modelsPath; }
        @Override public void setModelsPath(String path) { this.modelsPath = path; }
        @Override public String getArchivePath() { return archivePath; }
        @Override public String getAppDataPath() { return "."; }
    }

    @Test
    void testDownloadResume() throws Exception {
        DefaultDownloadManager downloadManager = new DefaultDownloadManager();
        StubConfigService configService = new StubConfigService(tempDir.toString(), tempDir.resolve("archive").toString());
        ReflectionTestUtils.setField(downloadManager, "configService", configService);
        ReflectionTestUtils.setField(downloadManager, "pathResolver", new PathResolver());

        Path targetFile = tempDir.resolve("checkpoints").resolve("resume.safetensors");
        Files.createDirectories(targetFile.getParent());
        
        byte[] existingData = new byte[20000];
        for (int i = 0; i < 20000; i++) existingData[i] = (byte) (i % 256);
        Files.write(targetFile, existingData);

        HttpClient mockClient = new HttpClientStub() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
                if ("HEAD".equals(request.method())) {
                    return (HttpResponse<T>) new MockHttpResponse<Void>(200, null, 40000);
                }
                byte[] remainingData = new byte[20000];
                for (int i = 0; i < 20000; i++) remainingData[i] = (byte) ((i + 20000) % 256);
                return (HttpResponse<T>) new MockHttpResponse<InputStream>(206, new ByteArrayInputStream(remainingData), 20000);
            }
        };
        ReflectionTestUtils.setField(downloadManager, "httpClient", mockClient);

        ModelInfo model = new ModelInfo("checkpoints", "resume.safetensors", "http://example.com/resume.safetensors");
        AtomicReference<String> lastStatus = new AtomicReference<>("");
        downloadManager.startQueue(Collections.singletonList(model), new boolean[]{true}, tempDir.toString(), 
            (idx, status) -> lastStatus.set(status), () -> {});

        long start = System.currentTimeMillis();
        while (!lastStatus.get().contains("✅ Finished") && !lastStatus.get().contains("Already exists") && !lastStatus.get().contains("❌") && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(100);
        }

        assertTrue(lastStatus.get().contains("✅ Finished") || lastStatus.get().contains("Already exists"), "Status was: " + lastStatus.get());
        Thread.sleep(500); 
        assertEquals(40000, Files.size(targetFile), "File size should be resumed to 40000");
    }

    @Test
    void testDiskFull() throws Exception {
        DefaultDownloadManager downloadManager = new DefaultDownloadManager();
        StubConfigService configService = new StubConfigService(tempDir.toString(), tempDir.resolve("archive").toString());
        ReflectionTestUtils.setField(downloadManager, "configService", configService);
        ReflectionTestUtils.setField(downloadManager, "pathResolver", new PathResolver());

        HttpClient mockClient = new HttpClientStub() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
                return (HttpResponse<T>) new MockHttpResponse<Void>(200, null, 1000L * 1024 * 1024 * 1024 * 1024);
            }
        };
        ReflectionTestUtils.setField(downloadManager, "httpClient", mockClient);

        ModelInfo model = new ModelInfo("checkpoints", "huge_model.safetensors", "http://example.com/huge.safetensors");
        AtomicReference<String> lastStatus = new AtomicReference<>("");
        downloadManager.startQueue(Collections.singletonList(model), new boolean[]{true}, tempDir.toString(), 
            (idx, status) -> lastStatus.set(status), () -> {});

        long start = System.currentTimeMillis();
        while (!lastStatus.get().contains("❌ No Space") && !lastStatus.get().contains("✅ Finished") && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(100);
        }

        assertTrue(lastStatus.get().contains("No Space"), "Expected No Space error. Status: " + lastStatus.get());
    }

    @Test
    void testCorruptPngMetadata() throws Exception {
        WorkflowService workflowService = new WorkflowService();
        File corruptPng = tempDir.resolve("corrupt.png").toFile();
        
        byte[] header = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        try (FileOutputStream fos = new FileOutputStream(corruptPng)) {
            fos.write(header);
            fos.write(new byte[]{0, 0, 0, 10, 't', 'E', 'X', 't'});
        }

        String result = workflowService.extractWorkflow(corruptPng);
        assertNull(result);
    }

    @Test
    void testConcurrencySettingsChange() throws Exception {
        DefaultDownloadManager downloadManager = new DefaultDownloadManager();
        StubConfigService configService = new StubConfigService(tempDir.toString(), tempDir.resolve("archive").toString());
        ReflectionTestUtils.setField(downloadManager, "configService", configService);
        ReflectionTestUtils.setField(downloadManager, "pathResolver", new PathResolver());

        PipedInputStream pin = new PipedInputStream();
        PipedOutputStream pout = new PipedOutputStream(pin);

        HttpClient mockClient = new HttpClientStub() {
            @Override
            public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
                if ("HEAD".equals(request.method())) {
                    return (HttpResponse<T>) new MockHttpResponse<Void>(200, null, 10000);
                }
                return (HttpResponse<T>) new MockHttpResponse<InputStream>(200, pin, 10000);
            }
        };
        ReflectionTestUtils.setField(downloadManager, "httpClient", mockClient);

        ModelInfo model = new ModelInfo("checkpoints", "concurrent.safetensors", "http://example.com/concurrent.safetensors");
        AtomicReference<String> lastStatus = new AtomicReference<>("");
        
        downloadManager.startQueue(Collections.singletonList(model), new boolean[]{true}, tempDir.toString(), 
            (idx, status) -> lastStatus.set(status), () -> {});

        // Simulate activity
        Thread.sleep(500);
        // Change settings during "active download" (blocked by PipedInputStream)
        configService.setModelsPath(tempDir.resolve("another_place").toString());
        
        // Unblock and finish
        pout.write(new byte[10000]);
        pout.close();

        long start = System.currentTimeMillis();
        while (!lastStatus.get().contains("✅ Finished") && System.currentTimeMillis() - start < 5000) {
            Thread.sleep(100);
        }
        assertTrue(lastStatus.get().contains("✅ Finished"), "Should finish despite setting change. Status: " + lastStatus.get());
    }

    @Test
    void testKiFallback() throws Exception {
        ComfyModelAnalyzer analyzer = new ComfyModelAnalyzer();
        GeminiAIService stubGemini = new GeminiAIService() {
            @Override public String analyzeModel(String name) { return null; }
        };
        LocalAIService stubLocal = new LocalAIService() {
            @Override public Prediction predictProvider(String name) {
                return new Prediction("LocalAuthor", 0.8);
            }
        };
        ReflectionTestUtils.setField(analyzer, "geminiService", stubGemini);
        ReflectionTestUtils.setField(analyzer, "aiService", stubLocal);

        String json = "{\"nodes\": [{\"type\": \"CheckpointLoaderSimple\", \"widgets_values\": [\"test_model.safetensors\"]}]}";
        List<ModelInfo> results = analyzer.analyze(json, "test.json");

        assertFalse(results.isEmpty());
        assertEquals("🧠 AI Verified: LocalAuthor", results.get(0).getPopularity());
    }

    private abstract static class HttpClientStub extends HttpClient {
        @Override public Optional<java.net.CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<java.time.Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<java.net.ProxySelector> proxy() { return Optional.empty(); }
        @Override public Optional<java.net.Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_2; }
        @Override public javax.net.ssl.SSLContext sslContext() { try { return javax.net.ssl.SSLContext.getDefault(); } catch (Exception e) { return null; } }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return new javax.net.ssl.SSLParameters(); }
        @Override public Optional<java.util.concurrent.Executor> executor() { return Optional.empty(); }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) { return null; }
        @Override public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return null; }
    }
}

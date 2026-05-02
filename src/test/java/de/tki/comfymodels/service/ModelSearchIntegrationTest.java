package de.tki.comfymodels.service;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.ModelSearchService;
import de.tki.comfymodels.service.impl.ModelListService;
import de.tki.comfymodels.service.impl.ModelHashRegistry;
import de.tki.comfymodels.service.impl.PathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@WireMockTest
public class ModelSearchIntegrationTest {

    private ConfigService configService;
    private GeminiAIService geminiService;
    private ModelListService modelListService;
    private ModelHashRegistry hashRegistry;
    private IModelValidator modelValidator;

    private ModelSearchService modelSearchService;
    private WireMockRuntimeInfo wmRuntimeInfo;

    // Manual Stubs to avoid Mockito/ByteBuddy issues on Java 25
    private static class ConfigServiceStub extends ConfigService {
        public ConfigServiceStub() { super(null, null); }
        @Override public String getHfToken() { return ""; }
        @Override public String getModelsPath() { return "mock_path"; }
    }

    private static class GeminiAIServiceStub extends GeminiAIService {
        @Override public String discoverBestRepo(String modelName, String fileName, String metadataContext) { return "UNKNOWN"; }
    }

    private static class ModelListServiceStub extends ModelListService {
        @Override public Optional<ModelInfo> findByFilename(String filename) { return Optional.empty(); }
    }

    private static class ModelHashRegistryStub extends ModelHashRegistry {
        @Override public String getOrCalculateHash(File file) { return null; }
    }

    private static class ModelValidatorStub implements IModelValidator {
        @Override public ValidationResult validateFile(File file) { return new ValidationResult(true, "OK", file.getAbsolutePath()); }
        @Override public String calculateHash(File file) { return null; }
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        this.wmRuntimeInfo = wmRuntimeInfo;
        configService = new ConfigServiceStub();
        geminiService = new GeminiAIServiceStub();
        modelListService = new ModelListServiceStub();
        hashRegistry = new ModelHashRegistryStub();
        modelValidator = new ModelValidatorStub();
        
        modelSearchService = new ModelSearchService() {
            @Override
            protected String getHfApiBaseUrl() {
                return wmRuntimeInfo.getHttpBaseUrl() + "/hf-api";
            }
            @Override
            protected String getCivitaiApiBaseUrl() {
                return wmRuntimeInfo.getHttpBaseUrl() + "/civitai-api";
            }
            @Override
            protected String getHfResolveBaseUrl() {
                return wmRuntimeInfo.getHttpBaseUrl() + "/hf-resolve";
            }
        };

        ReflectionTestUtils.setField(modelSearchService, "configService", configService);
        ReflectionTestUtils.setField(modelSearchService, "geminiService", geminiService);
        ReflectionTestUtils.setField(modelSearchService, "modelListService", modelListService);
        ReflectionTestUtils.setField(modelSearchService, "hashRegistry", hashRegistry);
        ReflectionTestUtils.setField(modelSearchService, "modelValidator", modelValidator);
    }

    @Test
    void testSearchOnline_CivitaiFound() throws InterruptedException {
        // GIVEN
        ModelInfo info = new ModelInfo("checkpoints", "test_model.safetensors", "MISSING");
        List<ModelInfo> models = Collections.singletonList(info);
        boolean[] selected = {true};

        stubFor(get(urlMatching("/civitai-api/models\\?query=.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"items\":[{\"modelVersions\":[{\"files\":[{\"name\":\"test_model.safetensors\",\"downloadUrl\":\"" + wmRuntimeInfo.getHttpBaseUrl() + "/mock-download/test.safetensors\"}]}]}]}")));

        stubFor(head(urlEqualTo("/mock-download/test.safetensors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Length", "1048576")));

        CountDownLatch latch = new CountDownLatch(1);

        // WHEN
        modelSearchService.searchOnline(models, selected, "{}", "test.json",
                (idx, status) -> {},
                (idx, foundInfo) -> {
                    if (foundInfo.getUrl().contains("/mock-download/test.safetensors")) {
                        latch.countDown();
                    }
                },
                () -> {}
        );

        // THEN
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Model should have been found on Civitai");
    }

    @Test
    void testSearchOnline_HuggingFaceFound() throws InterruptedException {
        // GIVEN
        ModelInfo info = new ModelInfo("checkpoints", "flux_test.safetensors", "MISSING");
        List<ModelInfo> models = Collections.singletonList(info);
        boolean[] selected = {true};

        stubFor(get(urlMatching("/hf-api/models\\?search=.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"id\":\"black-forest-labs/FLUX.1-schnell\"}]")));

        stubFor(get(urlEqualTo("/hf-api/models/black-forest-labs/FLUX.1-schnell/tree/main?recursive=true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[{\"path\":\"flux_test.safetensors\",\"type\":\"file\"}]")));

        stubFor(head(urlEqualTo("/hf-resolve/black-forest-labs/FLUX.1-schnell/resolve/main/flux_test.safetensors"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Length", "2097152")));

        CountDownLatch latch = new CountDownLatch(1);

        // WHEN
        modelSearchService.searchOnline(models, selected, "{}", "test.json",
                (idx, status) -> {},
                (idx, foundInfo) -> {
                    if (foundInfo.getUrl().contains("black-forest-labs/FLUX.1-schnell")) {
                        latch.countDown();
                    }
                },
                () -> {}
        );

        // THEN
        assertTrue(latch.await(5, TimeUnit.SECONDS), "Model should have been found on Hugging Face");
    }
}

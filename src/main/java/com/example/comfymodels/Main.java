package com.example.comfymodels;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main extends JFrame {
    private JTextField modelsPathField;
    private JTextArea jsonInputArea;
    private JTable modelTable;
    private DefaultTableModel tableModel;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton downloadButton;
    private JButton pauseButton;
    private JButton stopButton;
    
    private List<ModelInfo> modelsToDownload = new ArrayList<>();
    private final Set<String> processedModelNames = new HashSet<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

    private volatile boolean isPaused = false;
    private volatile boolean isStopped = false;
    private static final Pattern MODEL_FILE_PATTERN = Pattern.compile("([^\\\\/]+\\.(?:safetensors|ckpt|pt|bin|pth|onnx))", Pattern.CASE_INSENSITIVE);

    public Main() {
        setTitle("ComfyUI Model Download Manager (Pause/Resume/Stop)");
        setSize(1200, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Top Panel: Models Directory
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createTitledBorder("Models Directory"));
        modelsPathField = new JTextField("C:\\pinokio\\api\\comfy.git\\app\\models");
        JButton browsePathButton = new JButton("Browse...");
        browsePathButton.addActionListener(e -> chooseDirectory());
        topPanel.add(modelsPathField, BorderLayout.CENTER);
        topPanel.add(browsePathButton, BorderLayout.EAST);

        // Center Panel: JSON Input and Model List
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        
        // JSON Input
        JPanel jsonPanel = new JPanel(new BorderLayout());
        jsonPanel.setBorder(BorderFactory.createTitledBorder("ComfyUI JSON Workflow"));
        jsonInputArea = new JTextArea();
        JScrollPane jsonScroll = new JScrollPane(jsonInputArea);
        JPanel jsonButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadJsonButton = new JButton("Load File...");
        loadJsonButton.addActionListener(e -> chooseJsonFile());
        JButton analyzeButton = new JButton("Analyze Models");
        analyzeButton.addActionListener(e -> analyzeJson());
        jsonButtons.add(loadJsonButton);
        jsonButtons.add(analyzeButton);
        jsonPanel.add(jsonScroll, BorderLayout.CENTER);
        jsonPanel.add(jsonButtons, BorderLayout.SOUTH);

        // Model List
        String[] columnNames = {"Select", "Type", "Name", "URL", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        modelTable = new JTable(tableModel);
        modelTable.getColumnModel().getColumn(0).setMaxWidth(60);
        modelTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        modelTable.getColumnModel().getColumn(2).setPreferredWidth(250);
        modelTable.getColumnModel().getColumn(4).setPreferredWidth(300);
        JScrollPane tableScroll = new JScrollPane(modelTable);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Identified Models"));
        
        JPanel selectionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.addActionListener(e -> setAllSelected(true));
        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.addActionListener(e -> setAllSelected(false));
        selectionPanel.add(selectAllBtn);
        selectionPanel.add(deselectAllBtn);
        
        tablePanel.add(selectionPanel, BorderLayout.NORTH);
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        splitPane.setTopComponent(jsonPanel);
        splitPane.setBottomComponent(tablePanel);
        splitPane.setDividerLocation(300);

        // Bottom Panel: Actions and Progress
        JPanel bottomPanel = new JPanel(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        statusLabel = new JLabel("Ready");
        
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        downloadButton = new JButton("Start Queue");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> startDownloads());
        
        pauseButton = new JButton("Pause");
        pauseButton.setEnabled(false);
        pauseButton.addActionListener(e -> togglePause());
        
        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopDownloads());
        
        actionButtons.add(downloadButton);
        actionButtons.add(pauseButton);
        actionButtons.add(stopButton);
        
        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        progressPanel.add(statusLabel);
        progressPanel.add(progressBar);
        
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        add(topPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void togglePause() {
        isPaused = !isPaused;
        pauseButton.setText(isPaused ? "Resume" : "Pause");
        if (!isPaused) {
            statusLabel.setText("Resuming...");
        } else {
            statusLabel.setText("Paused.");
        }
    }

    private void stopDownloads() {
        isStopped = true;
        isPaused = false;
        pauseButton.setText("Pause");
        statusLabel.setText("Stopping queue...");
    }

    private void setAllSelected(boolean selected) {
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            tableModel.setValueAt(selected, i, 0);
        }
    }

    private void chooseDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            modelsPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void chooseJsonFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files", "json"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String content = Files.readString(chooser.getSelectedFile().toPath());
                jsonInputArea.setText(content);
                analyzeJson();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error reading file: " + e.getMessage());
            }
        }
    }

    private void analyzeJson() {
        String jsonText = jsonInputArea.getText();
        if (jsonText.isEmpty()) return;

        try {
            modelsToDownload.clear();
            processedModelNames.clear();
            tableModel.setRowCount(0);

            JSONObject jsonObject = new JSONObject(jsonText);
            findModelsMetadata(jsonObject);
            scanForModelFiles(jsonObject);

            for (ModelInfo info : modelsToDownload) {
                tableModel.addRow(new Object[]{true, info.type, info.name, info.url, "Pending"});
            }

            downloadButton.setEnabled(!modelsToDownload.isEmpty());
            statusLabel.setText("Found " + modelsToDownload.size() + " models.");

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error analyzing JSON: " + e.getMessage());
        }
    }

    private void findModelsMetadata(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            if (jo.has("models") && jo.get("models") instanceof JSONArray) {
                JSONArray models = jo.getJSONArray("models");
                for (int i = 0; i < models.length(); i++) {
                    Object mObj = models.get(i);
                    if (mObj instanceof JSONObject) {
                        JSONObject m = (JSONObject) mObj;
                        String name = m.optString("name");
                        if (name != null && !name.isEmpty()) {
                            String url = m.optString("url", "MISSING");
                            String dir = m.optString("directory", "checkpoints");
                            addModelInfo(new ModelInfo(dir, name, url));
                        }
                    }
                }
            }
            for (String key : jo.keySet()) {
                findModelsMetadata(jo.get(key));
            }
        } else if (obj instanceof JSONArray) {
            JSONArray ja = (JSONArray) obj;
            for (int i = 0; i < ja.length(); i++) {
                findModelsMetadata(ja.get(i));
            }
        }
    }

    private void scanForModelFiles(Object obj) {
        if (obj instanceof JSONObject) {
            JSONObject jo = (JSONObject) obj;
            for (String key : jo.keySet()) {
                Object val = jo.get(key);
                if (val instanceof String) {
                    checkAndAddModelFromFileName((String) val, inferTypeFromKey(key));
                } else {
                    scanForModelFiles(val);
                }
            }
        } else if (obj instanceof JSONArray) {
            JSONArray ja = (JSONArray) obj;
            for (int i = 0; i < ja.length(); i++) {
                Object val = ja.get(i);
                if (val instanceof String) {
                    checkAndAddModelFromFileName((String) val, "checkpoints");
                } else {
                    scanForModelFiles(val);
                }
            }
        }
    }

    private void checkAndAddModelFromFileName(String text, String type) {
        Matcher m = MODEL_FILE_PATTERN.matcher(text);
        while (m.find()) {
            String fileName = m.group(1);
            if (!processedModelNames.contains(fileName)) {
                addModelInfo(new ModelInfo(type, fileName, "MISSING (Needs manual URL)"));
            }
        }
    }

    private String inferTypeFromKey(String key) {
        if (key.contains("ckpt") || key.contains("checkpoint")) return "checkpoints";
        if (key.contains("lora")) return "loras";
        if (key.contains("vae")) return "vae";
        if (key.contains("control") && key.contains("net")) return "controlnet";
        if (key.contains("upscale")) return "upscale_models";
        if (key.contains("clip")) return "clip";
        if (key.contains("unet") || key.contains("diffusion")) return "unet";
        return "checkpoints";
    }

    private void addModelInfo(ModelInfo info) {
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo existing = modelsToDownload.get(i);
            if (existing.name.equals(info.name)) {
                if (existing.url.startsWith("MISSING") && !info.url.startsWith("MISSING")) {
                    modelsToDownload.set(i, info);
                }
                return;
            }
        }
        modelsToDownload.add(info);
        processedModelNames.add(info.name);
    }

    private void startDownloads() {
        String baseDir = modelsPathField.getText();
        if (baseDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please specify a models directory.");
            return;
        }

        isStopped = false;
        isPaused = false;
        downloadButton.setEnabled(false);
        pauseButton.setEnabled(true);
        stopButton.setEnabled(true);
        
        executor.submit(() -> {
            try {
                for (int i = 0; i < tableModel.getRowCount(); i++) {
                    if (isStopped) {
                        updateStatus(i, "Stopped");
                        continue;
                    }

                    boolean selected = (Boolean) tableModel.getValueAt(i, 0);
                    if (!selected) continue;

                    ModelInfo info = modelsToDownload.get(i);
                    if (info.url.startsWith("MISSING")) {
                        updateStatus(i, "Skipped (No URL)");
                        continue;
                    }

                    try {
                        processDownload(info, baseDir, i);
                    } catch (Exception e) {
                        updateStatus(i, "Error: " + e.getMessage());
                    }
                }
            } finally {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText(isStopped ? "Queue stopped." : "Queue finished.");
                    downloadButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                    stopButton.setEnabled(false);
                    pauseButton.setText("Pause");
                });
            }
        });
    }

    private void updateStatus(int row, String status) {
        SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, row, 4));
    }

    private void processDownload(ModelInfo info, String baseDir, int rowIndex) throws Exception {
        Path targetDir = Paths.get(baseDir, info.type);
        Files.createDirectories(targetDir);
        Path targetFile = targetDir.resolve(info.name);

        long existingSize = 0;
        if (Files.exists(targetFile)) {
            existingSize = Files.size(targetFile);
            long totalSize = getServerFileSize(info.url);
            
            if (totalSize != -1 && existingSize == totalSize) {
                updateStatus(rowIndex, "Already exists (Complete)");
                return;
            }

            final long fTotalSize = totalSize;
            final long fExistingSize = existingSize;
            
            int choice = showChoiceDialog(info.name, fExistingSize, fTotalSize);
            
            if (choice == 2) { // Skip
                updateStatus(rowIndex, "Skipped");
                return;
            } else if (choice == 1) { // Overwrite
                Files.delete(targetFile);
                existingSize = 0;
            }
        }

        downloadWithResume(info, targetFile, existingSize, rowIndex);
    }

    private long getServerFileSize(String url) {
        try {
            HttpRequest headRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
            return response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        } catch (Exception e) {
            return -1;
        }
    }

    private int showChoiceDialog(String name, long current, long total) throws Exception {
        final int[] result = new int[1];
        String totalStr = total == -1 ? "unknown" : (total / 1024 / 1024) + " MB";
        String currentStr = (current / 1024 / 1024) + " MB";
        
        SwingUtilities.invokeAndWait(() -> {
            String message = String.format("File '%s' already exists.\nLocal size: %s\nRemote size: %s", name, currentStr, totalStr);
            Object[] options = {"Resume", "Overwrite", "Skip"};
            result[0] = JOptionPane.showOptionDialog(this, message, "Existing File Found",
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        });
        return result[0];
    }

    private void downloadWithResume(ModelInfo info, Path targetFile, long startByte, int rowIndex) throws IOException, InterruptedException {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(URI.create(info.url));
        if (startByte > 0) {
            requestBuilder.header("Range", "bytes=" + startByte + "-");
        }
        
        HttpRequest request = requestBuilder.build();
        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200 && response.statusCode() != 206) {
            throw new IOException("HTTP " + response.statusCode());
        }

        long contentLen = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        long totalBytes = startByte + (contentLen != -1 ? contentLen : 0);

        try (InputStream is = response.body();
             OutputStream os = new FileOutputStream(targetFile.toFile(), startByte > 0)) {
            
            byte[] buffer = new byte[16384];
            long downloaded = startByte;
            long lastUpdate = System.currentTimeMillis();
            long lastDownloaded = startByte;
            int read;
            
            while ((read = is.read(buffer)) != -1) {
                if (isStopped) {
                    updateStatus(rowIndex, "Stopped at " + (downloaded * 100 / (totalBytes > 0 ? totalBytes : 1)) + "%");
                    return;
                }

                while (isPaused) {
                    Thread.sleep(500);
                    if (isStopped) return;
                }

                os.write(buffer, 0, read);
                downloaded += read;
                
                long now = System.currentTimeMillis();
                if (now - lastUpdate > 500) {
                    long diff = downloaded - lastDownloaded;
                    double speed = diff / ((now - lastUpdate) / 1000.0) / 1024.0 / 1024.0;
                    
                    final long fDownloaded = downloaded;
                    final double fSpeed = speed;
                    final int progress = totalBytes > 0 ? (int) (fDownloaded * 100 / totalBytes) : 0;
                    
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setValue(progress);
                        String statusText = String.format("Downloading: %d%% (%.2f MB/s)", progress, fSpeed);
                        tableModel.setValueAt(statusText, rowIndex, 4);
                        statusLabel.setText("Current: " + info.name + " [" + statusText + "]");
                    });
                    
                    lastUpdate = now;
                    lastDownloaded = downloaded;
                }
            }
        }
        if (!isStopped) updateStatus(rowIndex, "Finished");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Main main = new Main();
            main.setLocationRelativeTo(null);
            main.setVisible(true);
        });
    }

    static class ModelInfo {
        String type;
        String name;
        String url;

        public ModelInfo(String type, String name, String url) {
            this.type = type;
            this.name = name;
            this.url = url;
        }
    }
}

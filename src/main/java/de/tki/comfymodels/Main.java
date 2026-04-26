package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IWorkflowService;
import de.tki.comfymodels.service.IModelValidator;
import de.tki.comfymodels.service.IModelSearchService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.ModelListService;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelListener;
import javax.swing.event.TableModelEvent;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Component
public class Main extends JFrame {
    private final IModelAnalyzer analyzer;
    private final IDownloadManager downloadManager;
    private final IWorkflowService workflowService;
    private final IModelSearchService searchService;
    private final IModelValidator modelValidator;

    @Autowired
    private ConfigService configService;

    @Autowired
    private GeminiAIService geminiService;

    @Autowired
    private ModelListService modelListService;

    @Autowired
    private de.tki.comfymodels.service.impl.ModelHashRegistry hashRegistry;

    private JTextField modelsPathField;
    private JPasswordField geminiKeyField;
    private JPasswordField hfTokenField;
    private JCheckBox backgroundCheck;
    private JCheckBox shutdownCheck;
    private JCheckBox darkCheck;
    private JLabel activeAiModelLabel;
    private JTextArea jsonInputArea;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JButton downloadButton, pauseButton, stopButton;
    private List<ModelInfo> modelsToDownload;
    private String currentFileName = "input.json";

    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager,
                IWorkflowService workflowService, IModelSearchService searchService,
                IModelValidator modelValidator) {
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
    }

    public void launch(String[] args) {
        boolean backgroundArg = false;
        for (String arg : args) {
            if ("--background".equalsIgnoreCase(arg)) {
                backgroundArg = true;
                break;
            }
        }

        if (!promptForPassword()) {
            System.exit(0);
        }

        // Apply theme before UI initialization
        if (configService.isDarkMode()) {
            FlatDarkLaf.setup();
        } else {
            FlatLightLaf.setup();
        }
        FlatLaf.updateUI();
        
        // Modern UI tweaks
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        
        SwingUtilities.invokeLater(() -> {
            initUI();
            setupTrayIcon();
            loadSettingsIntoUI(); 
            updateAiModelDisplay();
            
            // Add WindowListener to handle background mode
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    if (configService.isBackgroundModeEnabled()) {
                        setVisible(false);
                    } else {
                        downloadManager.stop();
                        System.exit(0);
                    }
                }
            });
            
            setVisible(true);
        });
    }

    private void setupTrayIcon() {
        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
        // Use a simple colored square as fallback (Pink for ComfyUI style)
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(new Color(255, 105, 180)); // Hot Pink
        g2.fillRect(0, 0, 16, 16);
        g2.dispose();

        PopupMenu popup = new PopupMenu();
        MenuItem showItem = new MenuItem("Show UI");
        showItem.addActionListener(e -> setVisible(true));
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.addActionListener(e -> {
            downloadManager.stop();
            System.exit(0);
        });

        popup.add(showItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon trayIcon = new TrayIcon(image, "ComfyUI Model Downloader", popup);
        trayIcon.setImageAutoSize(true);
        trayIcon.addActionListener(e -> setVisible(true));

        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon could not be added.");
        }
    }

    private void updatePendingDownloadsPersistence() {
        if (modelsToDownload == null) return;
        org.json.JSONArray arr = new org.json.JSONArray();
        for (int i = 0; i < modelsToDownload.size(); i++) {
            // Only save if not finished
            String status = "";
            if (tableModel != null && tableModel.getRowCount() > i) {
                status = (String) tableModel.getValueAt(i, 7);
            }
            
            if (!status.contains("✅ Finished") && !status.contains("Already exists")) {
                ModelInfo info = modelsToDownload.get(i);
                org.json.JSONObject obj = new org.json.JSONObject();
                obj.put("name", info.getName());
                obj.put("url", info.getUrl());
                obj.put("type", info.getType());
                obj.put("save_path", info.getSave_path());
                arr.put(obj);
            }
        }
        configService.savePendingDownloads(arr.toString());
    }

    private boolean promptForPassword() {
        while (true) {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.add(new JLabel("Enter Vault Password (to unlock API Keys):"), BorderLayout.NORTH);
            JPasswordField pf = new JPasswordField();
            panel.add(pf, BorderLayout.CENTER);
            
            // Focus request
            SwingUtilities.invokeLater(() -> pf.requestFocusInWindow());
            
            int ok = JOptionPane.showConfirmDialog(null, panel, "Vault Unlock", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) return false;

            String pass = new String(pf.getPassword());
            if (pass.trim().isEmpty()) {
                JOptionPane.showMessageDialog(null, "Password cannot be empty.", "Validation", JOptionPane.WARNING_MESSAGE);
                continue;
            }

            try {
                configService.unlock(pass);
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Unlock Failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);        
            }
        }
    }

    private void loadSettingsIntoUI() {
        if (modelsPathField != null) {
            String path = configService.getModelsPath();
            modelsPathField.setText(path);
            System.out.println("GUI: Models Path loaded: " + path);
        }
        if (geminiKeyField != null) {
            String key = configService.getGeminiApiKey();
            geminiKeyField.setText(key);
            System.out.println("GUI: Gemini Key loaded (length: " + (key != null ? key.length() : 0) + ")");
        }
        if (hfTokenField != null) {
            String token = configService.getHfToken();
            hfTokenField.setText(token);
            System.out.println("GUI: HF Token loaded (length: " + (token != null ? token.length() : 0) + ")");
        }
        if (backgroundCheck != null) {
            backgroundCheck.setSelected(configService.isBackgroundModeEnabled());
        }
        if (shutdownCheck != null) {
            shutdownCheck.setSelected(configService.isShutdownAfterDownloadEnabled());
        }
        if (darkCheck != null) {
            darkCheck.setSelected(configService.isDarkMode());
        }
    }

    private void updateAiModelDisplay() {
        new Thread(() -> {
            String model = geminiService.discoverBestModel();
            SwingUtilities.invokeLater(() -> activeAiModelLabel.setText("Active AI: " + model));
        }).start();
    }

    private void initUI() {
        setTitle("ComfyUIModel-Downloader");
        setSize(1450, 1000);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // Enable FlatLaf window decorations for this frame
        getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);
        
        JPanel mainContainer = new JPanel(new BorderLayout(10, 10));
        mainContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(mainContainer);

        JPanel settingsPanel = new JPanel(new GridLayout(4, 1, 5, 5));

        JPanel pathRow = new JPanel(new BorderLayout());
        pathRow.setBorder(BorderFactory.createTitledBorder("Models Directory"));
        modelsPathField = new JTextField();
        JButton browsePathBtn = new JButton("Set Folder...");
        browsePathBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String current = modelsPathField.getText();
            if (current != null && !current.isEmpty()) {
                File dir = new File(current);
                if (dir.exists()) chooser.setCurrentDirectory(dir);
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                String selectedPath = chooser.getSelectedFile().getAbsolutePath();
                modelsPathField.setText(selectedPath);
                configService.setModelsPath(selectedPath);
            }
        });
        
        JButton savePathBtn = new JButton("Save Path");
        savePathBtn.addActionListener(e -> {
            configService.setModelsPath(modelsPathField.getText());
            statusLabel.setText("Models path saved to vault.");
        });

        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pathButtons.add(savePathBtn);
        pathButtons.add(browsePathBtn);

        pathRow.add(modelsPathField, BorderLayout.CENTER);
        pathRow.add(pathButtons, BorderLayout.EAST);

        JPanel apiRow = new JPanel(new BorderLayout());
        apiRow.setBorder(BorderFactory.createTitledBorder("Gemini AI API Key (Optional)"));
        geminiKeyField = new JPasswordField();
        JButton saveGeminiBtn = new JButton("Save & Discover");
        saveGeminiBtn.addActionListener(e -> {
            configService.setGeminiApiKey(new String(geminiKeyField.getPassword()));
            updateAiModelDisplay();
        });
        apiRow.add(geminiKeyField, BorderLayout.CENTER);
        apiRow.add(saveGeminiBtn, BorderLayout.EAST);

        JPanel hfRow = new JPanel(new BorderLayout());
        hfRow.setBorder(BorderFactory.createTitledBorder("Hugging Face Access Token (Optional)"));
        hfTokenField = new JPasswordField();
        JButton saveHfBtn = new JButton("Save HF Token");
        saveHfBtn.addActionListener(e -> {
            configService.setHfToken(new String(hfTokenField.getPassword()));
            statusLabel.setText("HF Token saved.");
            analyzeJsonContent();
        });
        hfRow.add(hfTokenField, BorderLayout.CENTER);
        hfRow.add(saveHfBtn, BorderLayout.EAST);

        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton helpBtn = new JButton("ℹ Help & Security Information");
        helpBtn.addActionListener(e -> showHelpDialog());
        
        JButton verifyBtn = new JButton("🔍 Fast Verify (Corruption)");
        verifyBtn.addActionListener(e -> verifyLocalModels(false));
        
        JButton optimizeBtn = new JButton("👯 Storage Optimizer (Duplicates)");
        optimizeBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, 
                "The Storage Optimizer calculates SHA-256 hashes for all local models.\n" +
                "This is EXTREMELY slow and resource-intensive for large libraries.\n\n" +
                "Do you want to proceed?", "Performance Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                verifyLocalModels(true);
            }
        });

        backgroundCheck = new JCheckBox("Stay in Background on Close");
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));

        shutdownCheck = new JCheckBox("Shutdown after Queue");
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));

        // Most important feature (irony): Dark mode for Reddit... or at least for this tool.
        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.addActionListener(e -> {
            boolean isDark = darkCheck.isSelected();
            configService.setDarkMode(isDark);
            
            FlatAnimatedLafChange.showSnapshot();
            if (isDark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            FlatLaf.updateUI();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        });

        infoRow.add(helpBtn);
        infoRow.add(verifyBtn);
        infoRow.add(optimizeBtn);
        infoRow.add(backgroundCheck);
        infoRow.add(shutdownCheck);
        infoRow.add(darkCheck);

        settingsPanel.add(pathRow);
        settingsPanel.add(apiRow);
        settingsPanel.add(hfRow);
        settingsPanel.add(infoRow);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JPanel jsonPanel = new JPanel(new BorderLayout());
        jsonPanel.setBorder(BorderFactory.createTitledBorder("Workflow (Drag & Drop JSON/PNG)"));
        jsonInputArea = new JTextArea();
        setupDragAndDrop(jsonInputArea);
        JScrollPane jsonScroll = new JScrollPane(jsonInputArea);

        JPanel jsonButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton loadJsonBtn = new JButton("Load Workflow...");
        loadJsonBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Workflow", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) loadFile(new File(fd.getDirectory(), fd.getFile()));
        });

        JButton importModelListBtn = new JButton("Import Model List (JSON)...");
        importModelListBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Model List JSON", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                try {
                    modelListService.importJson(new File(fd.getDirectory(), fd.getFile()));
                    JOptionPane.showMessageDialog(this, "Model list imported successfully!");
                    analyzeJsonContent();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());      
                }
            }
        });

        JButton analyzeBtn = new JButton("Deep Search (Strict Validation)");
        analyzeBtn.addActionListener(e -> {
            analyzeJsonContent();
            searchMissingOnline();
        });

        jsonButtons.add(loadJsonBtn);
        jsonButtons.add(importModelListBtn);
        jsonButtons.add(analyzeBtn);
        jsonPanel.add(jsonScroll, BorderLayout.CENTER);
        jsonPanel.add(jsonButtons, BorderLayout.SOUTH);

        String[] columnNames = {"Select", "Type", "Name", "Size", "AI Source", "Target Path", "URL", "Status"}; 
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }   
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };
        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                updateDownloadManagerSelection();
            }
        });
        JTable modelTable = new JTable(tableModel);
        modelTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        
        // Immediate termination of the editor on click to apply the value
        modelTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                if (modelTable.isEditing()) {
                    modelTable.getCellEditor().stopCellEditing();
                }
            }
        });

        modelTable.getColumnModel().getColumn(0).setMaxWidth(60);
        modelTable.getColumnModel().getColumn(4).setMinWidth(150);
        modelTable.getColumnModel().getColumn(5).setMinWidth(150);
        JScrollPane tableScroll = new JScrollPane(modelTable);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Identified Models"));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        splitPane.setTopComponent(jsonPanel);
        splitPane.setBottomComponent(tablePanel);
        splitPane.setDividerLocation(300);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready");

        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        downloadButton = new JButton("Start Queue");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> {
            int rowCount = tableModel.getRowCount();
            boolean[] selected = new boolean[rowCount];
            for (int i = 0; i < rowCount; i++) {
                selected[i] = (Boolean) tableModel.getValueAt(i, 0);
                String urlFromTable = (String) tableModel.getValueAt(i, 6);
                if (urlFromTable != null && !urlFromTable.equals("MISSING")) {
                    modelsToDownload.get(i).setUrl(urlFromTable);
                }
            }

            downloadButton.setEnabled(false);
            updatePendingDownloadsPersistence();
            downloadManager.startQueue(modelsToDownload, selected, modelsPathField.getText(),
                (idx, status) -> SwingUtilities.invokeLater(() -> {
                    tableModel.setValueAt(status, idx, 7);
                    if (status.contains("✅ Finished") || status.contains("Already exists")) {
                        updatePendingDownloadsPersistence();
                    }
                }),
                () -> SwingUtilities.invokeLater(() -> {
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Queue finished.");
                    updatePendingDownloadsPersistence();
                    
                    if (configService.isShutdownAfterDownloadEnabled()) {
                        performSystemShutdown();
                    }
                })
            );
        });

        pauseButton = new JButton("Pause");
        pauseButton.addActionListener(e -> {
            if (downloadManager.isPaused()) {
                updateDownloadManagerSelection();
            }
            downloadManager.togglePause();
            pauseButton.setText(downloadManager.isPaused() ? "Resume" : "Pause");
        });

        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> downloadManager.stop());

        actionButtons.add(downloadButton);
        actionButtons.add(pauseButton);
        actionButtons.add(stopButton);

        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        activeAiModelLabel = new JLabel("Active AI: Detecting...");
        activeAiModelLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        activeAiModelLabel.setForeground(Color.GRAY);
        progressPanel.add(activeAiModelLabel);
        progressPanel.add(statusLabel);
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        mainContainer.add(settingsPanel, BorderLayout.NORTH);
        mainContainer.add(splitPane, BorderLayout.CENTER);
        mainContainer.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void performSystemShutdown() {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            if (os.contains("win")) {
                Runtime.getRuntime().exec("shutdown /s /t 60");
                JOptionPane.showMessageDialog(this, "The system will shut down in 60 seconds. You can cancel this with 'shutdown /a' in the terminal.", "System Shutdown Scheduled", JOptionPane.WARNING_MESSAGE);
            } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
                Runtime.getRuntime().exec("shutdown -h +1");
                JOptionPane.showMessageDialog(this, "The system will shut down in 1 minute. You can cancel this with 'shutdown -c'.", "System Shutdown Scheduled", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Could not trigger system shutdown: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateDownloadManagerSelection() {
        int rowCount = tableModel.getRowCount();
        boolean[] selected = new boolean[rowCount];
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        }
        downloadManager.updateSelection(selected);
    }

    private void setupDragAndDrop(JTextArea area) {
        new DropTarget(area, new DropTargetListener() {
            public void dragEnter(DropTargetDragEvent dtde) {}
            public void dragOver(DropTargetDragEvent dtde) {}
            public void dropActionChanged(DropTargetDragEvent dtde) {}
            public void dragExit(DropTargetEvent dte) {}
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    java.util.List<File> files = (java.util.List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) loadFile(files.get(0));
                } catch (Exception e) {}
            }
        });
    }

    private void loadFile(File file) {
        try {
            currentFileName = file.getName();
            String content = workflowService.extractWorkflow(file);
            jsonInputArea.setText(content);
            analyzeJsonContent();
        } catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }

    private void analyzeJsonContent() {
        String text = jsonInputArea.getText();
        if (text.isEmpty()) return;
        modelsToDownload = analyzer.analyze(text, currentFileName);
        tableModel.setRowCount(0);
        String baseModelsPath = modelsPathField.getText();
        
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String type = info.getType() != null ? info.getType() : "checkpoints";
            String targetSubDir = "models" + File.separator + type;
            
            // Check if file already exists locally
            String status = info.getUrl().equals("MISSING") ? "Idle" : "✅ Known Good";
            boolean isSelected = true;
            Path localPath = Paths.get(baseModelsPath, info.getSave_path() != null ? info.getSave_path() : type, info.getName());
            boolean existsLocally = Files.exists(localPath);
            
            if (existsLocally) {
                status = "✅ Already exists";
                isSelected = false; // Deselect if already present
                try {
                    long localSizeBytes = Files.size(localPath);
                    info.setSize(searchService.formatSize(localSizeBytes));
                } catch (IOException e) {
                    info.setSize("Local (Error)");
                }
            }
            
            tableModel.addRow(new Object[]{isSelected, info.getType(), info.getName(), info.getSize(), info.getPopularity(), targetSubDir, info.getUrl(), status});
            
            // Only fetch remote size if NOT already existing locally
            if (!existsLocally && !info.getUrl().equals("MISSING")) {
                fetchSizeInBackground(info, i);
            }
        }
        downloadButton.setEnabled(!modelsToDownload.isEmpty());
    }

    private void searchMissingOnline() {
        if (modelsToDownload == null) return;
        int rowCount = tableModel.getRowCount();
        boolean[] selectedForSearch = new boolean[rowCount];
        boolean anyMissingSelected = false;
        for (int i = 0; i < rowCount; i++) {
            boolean isChecked = (Boolean) tableModel.getValueAt(i, 0);
            String currentUrl = (String) tableModel.getValueAt(i, 6);
            if (isChecked && (currentUrl == null || currentUrl.equals("MISSING") || currentUrl.isEmpty())) {    
                selectedForSearch[i] = true;
                anyMissingSelected = true;
            } else {
                selectedForSearch[i] = false;
            }
        }
        if (!anyMissingSelected) {
            JOptionPane.showMessageDialog(this, "No missing models selected for Deep Search.");
            return;
        }
        statusLabel.setText("Deep Search in progress...");
        searchService.searchOnline(modelsToDownload, selectedForSearch, jsonInputArea.getText(), currentFileName,
            (idx, status) -> SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, idx, 7)),
            (idx, info) -> SwingUtilities.invokeLater(() -> {
                String targetSubDir = "models" + File.separator + (info.getType() != null ? info.getType() : "checkpoints");
                tableModel.setValueAt(info.getSize(), idx, 3);
                tableModel.setValueAt(info.getPopularity(), idx, 4);
                tableModel.setValueAt(targetSubDir, idx, 5);
                tableModel.setValueAt(info.getUrl(), idx, 6);
                tableModel.setValueAt("✅ Found", idx, 7);
            }),
            () -> SwingUtilities.invokeLater(() -> statusLabel.setText("Deep Search finished."))
        );
    }

    private void verifyLocalModels(boolean checkDuplicates) {
        String baseDir = modelsPathField.getText();
        if (baseDir == null || baseDir.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please set a models directory first.");
            return;
        }

        File root = new File(baseDir);
        if (!root.exists() || !root.isDirectory()) {
            JOptionPane.showMessageDialog(this, "Invalid models directory.");
            return;
        }

        String taskName = checkDuplicates ? "Verifying models & checking for duplicates" : "Verifying models (Fast Check)";
        statusLabel.setText(taskName + "... please wait.");
        new Thread(() -> {
            try {
                List<IModelValidator.ValidationResult> errors = new ArrayList<>();
                Map<String, List<Path>> hashToPaths = new HashMap<>();
                
                List<Path> allFiles = Files.walk(root.toPath())
                        .filter(Files::isRegularFile)
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".safetensors") || n.endsWith(".sft") || n.endsWith(".ckpt") || n.endsWith(".pth") || n.endsWith(".pt") || n.endsWith(".bin");
                        })
                        .collect(Collectors.toList());

                int total = allFiles.size();
                for (int i = 0; i < total; i++) {
                    Path p = allFiles.get(i);
                    final int current = i + 1;
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Checking (" + current + "/" + total + "): " + p.getFileName()));
                    
                    IModelValidator.ValidationResult res = modelValidator.validateFile(p.toFile());
                    if (!res.ok) {
                        errors.add(res);
                    } else if (checkDuplicates) {
                        String hash = hashRegistry.getOrCalculateHash(p.toFile());
                        if (hash != null) {
                            hashToPaths.computeIfAbsent(hash, k -> new ArrayList<>()).add(p);
                        }
                    }
                }

                Map<String, List<Path>> duplicates = checkDuplicates ? hashToPaths.entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)) : new HashMap<>();

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Scan finished. Found " + errors.size() + " issues and " + duplicates.size() + " duplicate sets.");
                    
                    if (errors.isEmpty() && duplicates.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "All " + total + " models verified successfully!", "Verification Complete", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    StringBuilder sb = new StringBuilder();
                    if (!errors.isEmpty()) {
                        sb.append("⚠️ CORRUPTED FILES (").append(errors.size()).append("):\n");
                        for (IModelValidator.ValidationResult err : errors) {
                            sb.append("- ").append(new File(err.filePath).getName()).append(" (").append(err.message).append(")\n");
                        }
                        sb.append("\n");
                    }

                    if (!duplicates.isEmpty()) {
                        sb.append("👯 DUPLICATES FOUND (").append(duplicates.size()).append(" sets):\n");
                        for (Map.Entry<String, List<Path>> entry : duplicates.entrySet()) {
                            sb.append("Hash: ").append(entry.getKey().substring(0, 12)).append("...\n");
                            for (Path p : entry.getValue()) {
                                sb.append("  - ").append(p.toFile().getAbsolutePath()).append(" (").append(formatSize(p.toFile().length())).append(")\n");
                            }
                            sb.append("\n");
                        }
                    }
                    
                    JTextArea textArea = new JTextArea(sb.toString());
                    textArea.setEditable(false);
                    JScrollPane scrollPane = new JScrollPane(textArea);
                    scrollPane.setPreferredSize(new Dimension(850, 550));
                    
                    Object[] options = {"OK", "Delete All Corrupted", "Manage Duplicates..."};
                    int choice = JOptionPane.showOptionDialog(this, scrollPane, "Verification & Duplicate Results", 
                            JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                    
                    if (choice == 1) { // Delete All Corrupted
                        handleDeleteCorrupted(errors);
                    } else if (choice == 2) { // Manage Duplicates
                        handleManageDuplicates(duplicates);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error: " + e.getMessage()));
            }
        }).start();
    }

    private void handleDeleteCorrupted(List<IModelValidator.ValidationResult> errors) {
        int confirm = JOptionPane.showConfirmDialog(this, "Delete " + errors.size() + " corrupted files?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            int deleted = 0;
            for (IModelValidator.ValidationResult err : errors) {
                File f = new File(err.filePath);
                if (f.exists() && f.delete()) {
                    hashRegistry.unregister(f);
                    deleted++;
                }
            }
            JOptionPane.showMessageDialog(this, "Deleted " + deleted + " files.");
        }
    }

    private void handleManageDuplicates(Map<String, List<Path>> duplicates) {
        long totalSavings = 0;
        for (List<Path> paths : duplicates.values()) {
            for (int i = 1; i < paths.size(); i++) totalSavings += paths.get(i).toFile().length();
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Keep the first file of each set and delete others?\nEstimated savings: " + formatSize(totalSavings), 
            "Storage Optimizer", JOptionPane.YES_NO_OPTION);
            
        if (confirm == JOptionPane.YES_OPTION) {
            int deletedCount = 0;
            for (List<Path> paths : duplicates.values()) {
                for (int i = 1; i < paths.size(); i++) {
                    File f = paths.get(i).toFile();
                    if (f.exists() && f.delete()) {
                        hashRegistry.unregister(f);
                        deletedCount++;
                    }
                }
            }
            JOptionPane.showMessageDialog(this, "Freed " + formatSize(totalSavings) + ".");
        }
    }

    private void showHelpDialog() {
        String helpText = "<html><body style='width: 600px; padding: 10px;'>" +
                "<h1 style='color: #ff69b4;'>ComfyUI Model Downloader Help</h1>" +
                "<p>This tool automates the process of finding and downloading models required for your ComfyUI workflows.</p>" +
                
                "<h3>🚀 Core Features</h3>" +
                "<ul>" +
                "  <li><b>Smart Extraction:</b> Drag and drop <b>JSON</b> or <b>PNG</b> workflow files. The tool extracts models even from PNG metadata.</li>" +
                "  <li><b>Deep Search:</b> Automatically finds URLs on <b>Hugging Face</b> (Official & Community) and <b>Civitai</b>.</li>" +
                "  <li><b>AI Scouting:</b> Uses Gemini AI to understand the workflow context and find the exact right model versions.</li>" +
                "  <li><b>Auto-Organization:</b> Automatically sorts models into correct subfolders (<code>checkpoints</code>, <code>loras</code>, <code>vae</code>, <code>controlnet</code>, etc.).</li>" +
                "  <li><b>Existing File Detection:</b> Detects models you already have, calculates their local size, and prevents redundant downloads.</li>" +
                "</ul>" +

                "<h3>🛡️ Quality & Security</h3>" +
                "<ul>" +
                "  <li><b>Model Verification:</b> Use the <b>Verify</b> button to scan your local models for corrupted files (e.g., interrupted downloads or broken Safetensors).</li>" +
                "  <li><b>Encrypted Vault:</b> Your optional API keys are stored in <code>settings.vault</code> using <b>AES-256</b> encryption, protected by your master password.</li>" +
                "  <li><b>Privacy First:</b> No data is ever sent to third-party servers except for official API requests to Google, Hugging Face, or Civitai.</li>" +
                "</ul>" +

                "<h3>⚙️ Advanced Settings</h3>" +
                "<ul>" +
                "  <li><b>Background Mode:</b> Close the window to keep the downloader running in the <b>System Tray</b>.</li>" +
                "  <li><b>Auto-Shutdown:</b> Automatically shut down your PC after the download queue is finished.</li>" +
                "  <li><b>Dark Mode:</b> Modern dark interface for better workflow integration.</li>" +
                "  <li><b>Model List Import:</b> Import your own JSON/CSV lists to prioritize your trusted sources.</li>" +
                "</ul>" +

                "<h3>🔑 API Keys (Optional)</h3>" +
                "<ul>" +
                "  <li><b>Gemini Key:</b> Recommended for the best 'AI Scouting' results and repository discovery.</li>" +
                "  <li><b>HF Token:</b> Required to download <b>Gated Models</b> (like FLUX.1-dev or certain SD3 versions).</li>" +
                "</ul>" +
                "</body></html>";

        JOptionPane.showMessageDialog(this, helpText, "Application Help & Security", JOptionPane.INFORMATION_MESSAGE);
    }

    private void fetchSizeInBackground(ModelInfo info, int rowIndex) {
        new Thread(() -> {
            long size = searchService.getRemoteSize(info.getUrl());
            String s = formatSize(size);
            info.setSize(s);
            SwingUtilities.invokeLater(() -> tableModel.setValueAt(s, rowIndex, 3));
        }).start();
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "Unknown";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    public static void main(String[] args) {
        // Enable modern window decorations BEFORE anything else
        com.formdev.flatlaf.FlatLaf.setUseNativeWindowDecorations(true);

        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);   
        Main main = context.getBean(Main.class);
        main.launch(args);
    }

    @Configuration
    @ComponentScan("de.tki.comfymodels")
    public static class AppConfig {}
}
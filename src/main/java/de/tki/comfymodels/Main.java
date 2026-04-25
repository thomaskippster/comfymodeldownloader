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
        
        // Modern UI tweaks
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
        apiRow.setBorder(BorderFactory.createTitledBorder("Gemini AI API Key"));
        geminiKeyField = new JPasswordField();
        JButton saveGeminiBtn = new JButton("Save & Discover");
        saveGeminiBtn.addActionListener(e -> {
            configService.setGeminiApiKey(new String(geminiKeyField.getPassword()));
            updateAiModelDisplay();
        });
        apiRow.add(geminiKeyField, BorderLayout.CENTER);
        apiRow.add(saveGeminiBtn, BorderLayout.EAST);

        JPanel hfRow = new JPanel(new BorderLayout());
        hfRow.setBorder(BorderFactory.createTitledBorder("Hugging Face Access Token"));
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
        
        JButton verifyBtn = new JButton("🔍 Verify Local Models (Corruption Check)");
        verifyBtn.addActionListener(e -> verifyLocalModels());

        backgroundCheck = new JCheckBox("Stay in Background on Close");
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));

        shutdownCheck = new JCheckBox("Shutdown after Queue");
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));

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
        
        // Sofortiger Abbruch des Editors bei Klick, um Wert zu übernehmen
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
            if (Files.exists(localPath)) {
                status = "✅ Already exists";
                isSelected = false; // Deselect if already present
            }
            
            tableModel.addRow(new Object[]{isSelected, info.getType(), info.getName(), info.getSize(), info.getPopularity(), targetSubDir, info.getUrl(), status});
            if (!info.getUrl().equals("MISSING")) fetchSizeInBackground(info, i);
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

    private void verifyLocalModels() {
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

        statusLabel.setText("Verifying models... please wait.");
        new Thread(() -> {
            try {
                List<IModelValidator.ValidationResult> errors = new ArrayList<>();
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
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Verification finished. Found " + errors.size() + " issues.");
                    if (errors.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "All " + total + " models verified successfully!", "Verification Complete", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        StringBuilder sb = new StringBuilder("The following " + errors.size() + " files appear to be corrupted or invalid:\n\n");
                        for (IModelValidator.ValidationResult err : errors) {
                            sb.append("- ").append(new File(err.filePath).getName())
                              .append(" (").append(err.message).append(")\n")
                              .append("  Path: ").append(err.filePath).append("\n\n");
                        }
                        
                        JTextArea textArea = new JTextArea(sb.toString());
                        textArea.setEditable(false);
                        JScrollPane scrollPane = new JScrollPane(textArea);
                        scrollPane.setPreferredSize(new Dimension(800, 500));
                        
                        Object[] options = {"OK", "Delete All Corrupted Files"};
                        int choice = JOptionPane.showOptionDialog(this, scrollPane, "Verification Results - Issues Found", 
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                        
                        if (choice == 1) { // Delete All Corrupted Files
                            int confirm = JOptionPane.showConfirmDialog(this, 
                                "Are you sure you want to delete these " + errors.size() + " files?\nThis action cannot be undone.",
                                "Confirm Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                            
                            if (confirm == JOptionPane.YES_OPTION) {
                                int deletedCount = 0;
                                for (IModelValidator.ValidationResult err : errors) {
                                    File f = new File(err.filePath);
                                    if (f.exists() && f.delete()) {
                                        deletedCount++;
                                    }
                                }
                                JOptionPane.showMessageDialog(this, "Deleted " + deletedCount + " files.");
                                statusLabel.setText("Cleanup finished. Deleted " + deletedCount + " files.");
                            }
                        }
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error during verification: " + e.getMessage()));
            }
        }).start();
    }

    private void showHelpDialog() {
        String helpText = "<html><body style='width: 500px; padding: 10px;'>" +
                "<h1>ComfyUIModel-Downloader Help</h1>" +
                "<h3>Core Features</h3>" +
                "<ul>" +
                "  <li><b>Workflow Analysis:</b> Drag and drop ComfyUI JSON or PNG files to extract required models.</li>" +
                "  <li><b>Deep Search:</b> Uses AI and multi-platform searching (Hugging Face, Civitai) to find missing model URLs.</li>" +
                "  <li><b>Model List Integration:</b> Match models against your own known-good local lists.</li>" +
                "  <li><b>Queue Management:</b> Download multiple models simultaneously with pause/resume support.</li>" +
                "</ul>" +
                "<h3>API Keys & Security</h3>" +
                "<ul>" +
                "  <li><b>Gemini API Key:</b> Required for the 'AI Scouting' feature. Gemini analyzes your workflow context " +
                "      to predict the best repositories for obscure models.</li>" +
                "  <li><b>Hugging Face Token:</b> Recommended to avoid rate limits and to access gated repositories " +
                "      (like some FLUX or StabilityAI models).</li>" +
                "  <li><b>Local Storage (Vault):</b> All keys and settings are stored <b>locally</b> on your machine " +
                "      in an encrypted file (<code>settings.vault</code>). They are never sent to any server except " +
                "      the official Google/Hugging Face APIs during requests.</li>" +
                "  <li><b>Password Protection:</b> Your vault is protected by the password you choose at startup. " +
                "      Without this password, the API keys cannot be decrypted.</li>" +
                "</ul>" +
                "</body></html>";

        JOptionPane.showMessageDialog(this, helpText, "Application Help & Security", JOptionPane.INFORMATION_MESSAGE);
    }

    private void fetchSizeInBackground(ModelInfo info, int rowIndex) {
        new Thread(() -> {
            long size = searchService.getRemoteSize(info.getUrl());
            String s = searchService.formatSize(size);
            info.setSize(s);
            SwingUtilities.invokeLater(() -> tableModel.setValueAt(s, rowIndex, 3));
        }).start();
    }

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);   
        Main main = context.getBean(Main.class);
        main.launch(args);
    }

    @Configuration
    @ComponentScan("de.tki.comfymodels")
    public static class AppConfig {}
}
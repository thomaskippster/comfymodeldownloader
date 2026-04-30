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
import de.tki.comfymodels.service.impl.ModelHashRegistry;
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
import java.nio.file.StandardCopyOption;
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
    private final de.tki.comfymodels.service.impl.RestBridgeService restBridge;

    @Autowired
    private ConfigService configService;

    @Autowired
    private GeminiAIService geminiService;

    @Autowired
    private ModelListService modelListService;

    @Autowired
    private ModelHashRegistry hashRegistry;

    private JTextField modelsPathField;
    private JTextField comfyPathField;
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
                IModelValidator modelValidator, de.tki.comfymodels.service.impl.RestBridgeService restBridge) {
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
        this.modelValidator = modelValidator;
        this.restBridge = restBridge;
    }

    public void launch(String[] args) {
        // Initialize REST Bridge consumer EARLY
        restBridge.setWorkflowConsumer(workflowJson -> {
            SwingUtilities.invokeLater(() -> {
                if (jsonInputArea != null) {
                    jsonInputArea.setText(workflowJson);
                    currentFileName = "remote_workflow.json";
                    analyzeJsonContent();
                }
                setVisible(true);
                toFront();
                requestFocus();
            });
        });
        
        // Load settings to get/generate the API Token
        if (configService.isUnlocked()) {
            restBridge.setApiToken(configService.getApiToken());
        }
        restBridge.startServer();

        if (!promptForPassword()) {
            System.exit(0);
        }

        // Apply theme before UI initialization
        try {
            if (configService.isDarkMode()) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            System.err.println("Theme setup failed: " + e.getMessage());
        }
        FlatLaf.updateUI();
        
        // Modern UI tweaks
        UIManager.put("TitlePane.unifiedBackground", true);
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("ProgressBar.arc", 8);
        
        SwingUtilities.invokeLater(() -> {
            try {
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
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, "Critical UI Error: " + e.getMessage());
            }
        });
    }

    private void setupTrayIcon() {
        if (!SystemTray.isSupported()) return;

        SystemTray tray = SystemTray.getSystemTray();
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

    private boolean promptForPassword() {
        while (true) {
            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.add(new JLabel("Enter Vault Password (to unlock API Keys):"), BorderLayout.NORTH);
            JPasswordField pf = new JPasswordField();
            panel.add(pf, BorderLayout.CENTER);
            
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
                restBridge.setApiToken(configService.getApiToken());
                return true;
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Unlock Failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);        
            }
        }
    }

    private void loadSettingsIntoUI() {
        if (modelsPathField != null) modelsPathField.setText(configService.getModelsPath());
        if (comfyPathField != null) comfyPathField.setText(configService.getComfyUIPath());
        if (geminiKeyField != null) geminiKeyField.setText(configService.getGeminiApiKey());
        if (hfTokenField != null) hfTokenField.setText(configService.getHfToken());
        if (backgroundCheck != null) backgroundCheck.setSelected(configService.isBackgroundModeEnabled());
        if (shutdownCheck != null) shutdownCheck.setSelected(configService.isShutdownAfterDownloadEnabled());
        if (darkCheck != null) darkCheck.setSelected(configService.isDarkMode());
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

        getRootPane().putClientProperty("flatlaf.useWindowDecorations", true);
        
        JPanel mainContainer = new JPanel(new BorderLayout(10, 10));
        mainContainer.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setContentPane(mainContainer);

        JPanel settingsPanel = new JPanel(new GridLayout(5, 1, 5, 5));

        // Row 1: Models Directory
        JPanel pathRow = new JPanel(new BorderLayout());
        pathRow.setBorder(BorderFactory.createTitledBorder("Models Directory"));
        modelsPathField = new JTextField();
        JButton browsePathBtn = new JButton("Set Folder...");
        browsePathBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                modelsPathField.setText(chooser.getSelectedFile().getAbsolutePath());
                configService.setModelsPath(modelsPathField.getText());
            }
        });
        JButton savePathBtn = new JButton("Save Path");
        savePathBtn.addActionListener(e -> {
            configService.setModelsPath(modelsPathField.getText());
            statusLabel.setText("Models path saved.");
        });
        JPanel pathButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pathButtons.add(savePathBtn);
        pathButtons.add(browsePathBtn);
        pathRow.add(modelsPathField, BorderLayout.CENTER);
        pathRow.add(pathButtons, BorderLayout.EAST);

        // Row 2: ComfyUI Installation (Simplified in Main)
        JPanel comfyRow = new JPanel(new BorderLayout());
        comfyRow.setBorder(BorderFactory.createTitledBorder("ComfyUI Integration"));
        JButton openInstallDialogBtn = new JButton("🚀 Install/Update Bridge...");
        openInstallDialogBtn.addActionListener(e -> showInstallationDialog());
        comfyRow.add(openInstallDialogBtn, BorderLayout.CENTER);

        // Row 3: Gemini API Key
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

        // Row 4: Hugging Face Token
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

        // Row 5: Options & Info
        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        activeAiModelLabel = new JLabel("Active AI: Loading...");
        JButton helpBtn = new JButton("ℹ Help");
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

        backgroundCheck = new JCheckBox("Stay in Background");
        backgroundCheck.addActionListener(e -> configService.setBackgroundModeEnabled(backgroundCheck.isSelected()));
        shutdownCheck = new JCheckBox("Shutdown after Queue");
        shutdownCheck.addActionListener(e -> configService.setShutdownAfterDownloadEnabled(shutdownCheck.isSelected()));
        darkCheck = new JCheckBox("Dark Mode");
        darkCheck.addActionListener(e -> {
            configService.setDarkMode(darkCheck.isSelected());
            FlatAnimatedLafChange.showSnapshot();
            if (darkCheck.isSelected()) FlatDarkLaf.setup(); else FlatLightLaf.setup();
            FlatLaf.updateUI();
            FlatAnimatedLafChange.hideSnapshotWithAnimation();
        });
        infoRow.add(new JSeparator(JSeparator.VERTICAL));
        infoRow.add(helpBtn);
        infoRow.add(verifyBtn);
        infoRow.add(optimizeBtn);
        infoRow.add(backgroundCheck);
        infoRow.add(shutdownCheck);
        infoRow.add(darkCheck);

        settingsPanel.add(pathRow);
        settingsPanel.add(comfyRow);
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

        JButton importModelListBtn = new JButton("Import Model List...");
        importModelListBtn.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Select Model List JSON", FileDialog.LOAD);
            fd.setVisible(true);
            if (fd.getFile() != null) {
                try {
                    modelListService.importJson(new File(fd.getDirectory(), fd.getFile()));
                    JOptionPane.showMessageDialog(this, "Model list imported successfully! (" + modelListService.getModels().size() + " models)");
                    analyzeJsonContent();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
                }
            }
        });

        JButton analyzeBtn = new JButton("Deep Search");
        analyzeBtn.addActionListener(e -> { analyzeJsonContent(); searchMissingOnline(); });
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
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) updateDownloadManagerSelection();
        });
        JTable modelTable = new JTable(tableModel);
        modelTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        JScrollPane tableScroll = new JScrollPane(modelTable);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Identified Models"));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        splitPane.setTopComponent(jsonPanel);
        splitPane.setBottomComponent(tablePanel);
        splitPane.setDividerLocation(300);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        statusLabel = new JLabel("Ready");
        
        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        activeAiModelLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        activeAiModelLabel.setForeground(Color.GRAY);
        progressPanel.add(statusLabel);
        progressPanel.add(activeAiModelLabel);
        
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        downloadButton = new JButton("Start Queue");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> startDownloadQueue());
        pauseButton = new JButton("Pause");
        pauseButton.addActionListener(e -> {
            downloadManager.togglePause();
            pauseButton.setText(downloadManager.isPaused() ? "Resume" : "Pause");
        });
        stopButton = new JButton("Stop");
        stopButton.addActionListener(e -> downloadManager.stop());
        actionButtons.add(downloadButton);
        actionButtons.add(pauseButton);
        actionButtons.add(stopButton);
        
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        mainContainer.add(settingsPanel, BorderLayout.NORTH);
        mainContainer.add(splitPane, BorderLayout.CENTER);
        mainContainer.add(bottomPanel, BorderLayout.SOUTH);
    }

    private void startDownloadQueue() {
        int rowCount = tableModel.getRowCount();
        if (rowCount == 0) return;
        boolean[] selected = new boolean[rowCount];
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
            String url = (String) tableModel.getValueAt(i, 6);
            if (url != null && !url.equals("MISSING")) modelsToDownload.get(i).setUrl(url);
        }
        downloadButton.setEnabled(false);
        downloadManager.startQueue(modelsToDownload, selected, modelsPathField.getText(),
            (idx, status) -> SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, idx, 7)),
            () -> SwingUtilities.invokeLater(() -> {
                downloadButton.setEnabled(true);
                statusLabel.setText("Queue finished.");
                if (configService.isShutdownAfterDownloadEnabled()) performSystemShutdown();
            })
        );
    }

    private void showInstallationDialog() {
        JDialog dialog = new JDialog(this, "ComfyUI Bridge Installation", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(650, 360);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 15, 0);

        // Explanation text
        JTextArea infoArea = new JTextArea(
            "This installer will set up the ComfyUI-Model-Downloader bridge.\n\n" +
            "1. Select your ComfyUI main directory.\n" +
            "2. Old or conflicting bridge files will be cleaned up.\n" +
            "3. A link or copy of the UI extension will be created.\n\n" +
            "Important: Restart ComfyUI after the installation is finished."
        );
        infoArea.setEditable(false);
        infoArea.setFocusable(false);
        infoArea.setBackground(content.getBackground());
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        content.add(infoArea, gbc);

        // Path selection header
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 5, 0);
        content.add(new JLabel("ComfyUI Main Directory:"), gbc);

        // Path selection row
        gbc.gridy++;
        gbc.insets = new Insets(0, 0, 10, 0);
        JPanel pathRow = new JPanel(new BorderLayout(5, 0));
        JTextField pathField = new JTextField(configService.getComfyUIPath());
        JButton browseBtn = new JButton("Browse...");
        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(pathField, BorderLayout.CENTER);
        pathRow.add(browseBtn, BorderLayout.EAST);
        content.add(pathRow, gbc);

        // Spacer to push everything to the top
        gbc.gridy++;
        gbc.weighty = 1.0;
        content.add(new JPanel(), gbc);

        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.addActionListener(e -> dialog.dispose());
        JButton installBtn = new JButton("🚀 Start Installation");
        installBtn.addActionListener(e -> {
            String selectedPath = pathField.getText().trim();
            if (selectedPath.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Please select a path first.");
                return;
            }
            installComfyUIBridge(selectedPath, dialog);
        });
        buttonPanel.add(cancelBtn);
        buttonPanel.add(installBtn);

        dialog.add(content, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private void installComfyUIBridge(String comfyPath, JDialog parentDialog) {
        File inputPath = new File(comfyPath);
        if (!inputPath.exists()) {
            JOptionPane.showMessageDialog(parentDialog, "The provided path does not exist: " + comfyPath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Systematically search for custom_nodes
        File customNodesDir = null;
        
        // Candidate 0: Specific Desktop App nested structure (High Priority)
        File c0 = new File(inputPath, "resources" + File.separator + "ComfyUI" + File.separator + "custom_nodes");
        // Candidate 1: inputPath/custom_nodes (Standard)
        File c1 = new File(inputPath, "custom_nodes");
        // Candidate 2: inputPath/ComfyUI/custom_nodes (Portable Root)
        File c2 = new File(inputPath, "ComfyUI" + File.separator + "custom_nodes");
        // Candidate 3: inputPath itself (User selected custom_nodes directly)
        File c3 = inputPath;

        if (c0.exists() && c0.isDirectory()) customNodesDir = c0;
        else if (c1.exists() && c1.isDirectory()) customNodesDir = c1;
        else if (c2.exists() && c2.isDirectory()) customNodesDir = c2;
        else if (c3.getName().equalsIgnoreCase("custom_nodes") && c3.isDirectory()) customNodesDir = c3;
        
        // --- IMPROVEMENT: DEEP SEARCH (if candidates fail) ---
        if (customNodesDir == null) {
            System.out.println("Standard candidates failed. Starting deep search in: " + inputPath.getAbsolutePath());
            customNodesDir = findCustomNodesDeep(inputPath, 0);
        }

        if (customNodesDir == null) {
            String msg = "Could not find 'custom_nodes' folder in the selected directory.\n\n" +
                         "Checked locations (among others):\n" +
                         "- " + c0.getAbsolutePath() + "\n" +
                         "- " + c1.getAbsolutePath() + "\n" +
                         "- " + c2.getAbsolutePath() + "\n\n" +
                         "Please ensure you select the folder that contains 'custom_nodes' or the main ComfyUI folder.";
            JOptionPane.showMessageDialog(parentDialog, msg, "Installation Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // --- IMPROVEMENT 1: CLEANUP LEGACY/CONFLICTING FILES ---
        String[] conflictingFiles = {"comfyui_to_downloader.py", "comfyui-model-downloader.py"};
        for (String conflict : conflictingFiles) {
            File conflictFile = new File(customNodesDir, conflict);
            if (conflictFile.exists()) {
                System.out.println("Cleaning up legacy file: " + conflictFile.getAbsolutePath());
                conflictFile.delete();
            }
        }

        configService.setComfyUIPath(comfyPath);
        
        File targetLink = new File(customNodesDir, "comfyui-model-downloader");
        File sourceDir = new File(System.getProperty("user.dir"), "comfyui-model-downloader");
        
        // --- IMPROVEMENT 2: SOURCE VERIFICATION ---
        if (!sourceDir.exists()) {
             File parentSource = new File(new File(System.getProperty("user.dir")).getParent(), "comfyui-model-downloader");
             if (parentSource.exists()) sourceDir = parentSource;
        }

        if (!sourceDir.exists()) {
             File altSource = new File(System.getProperty("user.dir"));
             if (new File(altSource, "__init__.py").exists() && new File(altSource, "web").exists()) {
                 sourceDir = altSource;
             }
        }
        
        if (!sourceDir.exists() || !new File(sourceDir, "__init__.py").exists()) {
            JOptionPane.showMessageDialog(parentDialog, "Source extension folder is invalid or incomplete!\nLooked in: " + sourceDir.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            // --- IMPROVEMENT 3: ROBUST DELETION OF OLD DIRECTORY/LINK ---
            if (targetLink.exists()) {
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    Runtime.getRuntime().exec("cmd /c rmdir \"" + targetLink.getAbsolutePath() + "\"").waitFor();
                    if (targetLink.exists()) Runtime.getRuntime().exec("cmd /c del /F /Q \"" + targetLink.getAbsolutePath() + "\"").waitFor();
                }
                if (targetLink.exists()) deleteDirectory(targetLink);
            }

            boolean success = false;
            String method = "Unknown";

            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                String cmdJ = String.format("cmd /c mklink /J \"%s\" \"%s\"", targetLink.getAbsolutePath(), sourceDir.getAbsolutePath());
                if (Runtime.getRuntime().exec(cmdJ).waitFor() == 0) {
                    success = true; method = "Junction";
                } else {
                    String cmdS = String.format("cmd /c mklink /D \"%s\" \"%s\"", targetLink.getAbsolutePath(), sourceDir.getAbsolutePath());
                    if (Runtime.getRuntime().exec(cmdS).waitFor() == 0) {
                        success = true; method = "Symlink";
                    } else {
                        copyDirectory(sourceDir, targetLink);
                        success = targetLink.exists(); method = "Copy";
                    }
                }
            } else {
                java.nio.file.Files.createSymbolicLink(targetLink.toPath(), sourceDir.toPath());
                success = true; method = "Symlink";
            }

            if (success) {
                writeExtensionConfig(targetLink.exists() && method.equals("Copy") ? targetLink : sourceDir);
                if (new File(targetLink, "__init__.py").exists()) {
                    String msg = "🚀 ComfyUI Bridge installed successfully!\n\n" +
                                 "Method: " + method + "\n" +
                                 "Location: " + targetLink.getAbsolutePath() + "\n\n" +
                                 "Please RESTART ComfyUI now to see the rocket icon.";
                    JOptionPane.showMessageDialog(parentDialog, msg, "Success", JOptionPane.INFORMATION_MESSAGE);
                    parentDialog.dispose();
                } else {
                    throw new Exception("Installation successful but __init__.py not found at target!");
                }
            } else {
                throw new Exception("All installation methods failed.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(parentDialog, "Critical failure during installation: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private File findCustomNodesDeep(File dir, int depth) {
        if (depth > 2) return null; // Limit depth to avoid performance issues
        File[] files = dir.listFiles();
        if (files == null) return null;

        // Check immediate children first
        for (File f : files) {
            if (f.isDirectory() && f.getName().equalsIgnoreCase("custom_nodes")) {
                return f;
            }
        }

        // Recurse
        for (File f : files) {
            if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equalsIgnoreCase("node_modules")) {
                File found = findCustomNodesDeep(f, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void writeExtensionConfig(File dir) {
        try {
            File webDir = new File(dir, "web");
            if (!webDir.exists()) webDir.mkdirs();
            JSONObject config = new JSONObject();
            config.put("token", configService.getApiToken());
            Files.writeString(new File(webDir, "config.json").toPath(), config.toString(4));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) deleteDirectory(f);
        dir.delete();
    }

    private void copyDirectory(File s, File d) throws IOException {
        if (s.isDirectory()) {
            if (!d.exists()) d.mkdirs();
            String[] children = s.list();
            if (children != null) for (String c : children) copyDirectory(new File(s, c), new File(d, c));
        } else Files.copy(s.toPath(), d.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void loadFile(File file) {
        try {
            currentFileName = file.getName();
            jsonInputArea.setText(workflowService.extractWorkflow(file));
            analyzeJsonContent();
        } catch (IOException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }

    private void analyzeJsonContent() {
        String text = jsonInputArea.getText();
        if (text == null || text.isEmpty()) return;
        modelsToDownload = analyzer.analyze(text, currentFileName);
        tableModel.setRowCount(0);
        String base = modelsPathField.getText();
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String type = info.getType() != null ? info.getType() : "checkpoints";
            Path local = Paths.get(base, info.getSave_path() != null ? info.getSave_path() : type, info.getName());
            boolean exists = Files.exists(local);
            String status = exists ? "✅ Already exists" : (info.getUrl().equals("MISSING") ? "Idle" : "✅ Known Good");
            tableModel.addRow(new Object[]{!exists, info.getType(), info.getName(), info.getSize(), info.getPopularity(), "models/" + type, info.getUrl(), status});
        }
        downloadButton.setEnabled(!modelsToDownload.isEmpty());
    }

    private void searchMissingOnline() {
        if (modelsToDownload == null) return;
        statusLabel.setText("Searching...");
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
        searchService.searchOnline(modelsToDownload, selected, jsonInputArea.getText(), currentFileName,
            (idx, status) -> SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, idx, 7)),
            (idx, info) -> SwingUtilities.invokeLater(() -> {
                tableModel.setValueAt(info.getSize(), idx, 3);
                tableModel.setValueAt(info.getUrl(), idx, 6);
                tableModel.setValueAt("✅ Found", idx, 7);
            }),
            () -> SwingUtilities.invokeLater(() -> statusLabel.setText("Search finished."))
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

                    if (!errors.isEmpty()) {
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
                    
                    if (!duplicates.isEmpty()) {
                        showDuplicatesDialog(duplicates);
                    }
                });
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error during verification: " + e.getMessage()));
            }
        }).start();
    }

    private void showDuplicatesDialog(Map<String, List<Path>> duplicates) {
        StringBuilder sb = new StringBuilder("Storage Optimizer - Duplicate Models Found:\n\n");
        sb.append("The following files have identical content (SHA-256 match).\n");
        sb.append("You might want to delete redundant copies to save space.\n\n");

        for (Map.Entry<String, List<Path>> entry : duplicates.entrySet()) {
            sb.append("SHA-256: ").append(entry.getKey()).append("\n");
            for (Path p : entry.getValue()) {
                sb.append("  -> ").append(p.toAbsolutePath()).append("\n");
            }
            sb.append("\n");
        }

        JTextArea textArea = new JTextArea(sb.toString());
        textArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(900, 600));
        JOptionPane.showMessageDialog(this, scrollPane, "Duplicates Found", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateDownloadManagerSelection() {
        if (downloadManager == null || tableModel == null) return;
        boolean[] selected = new boolean[tableModel.getRowCount()];
        for (int i = 0; i < selected.length; i++) selected[i] = (Boolean) tableModel.getValueAt(i, 0);
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

    private void performSystemShutdown() {
        try { Runtime.getRuntime().exec("shutdown /s /t 60"); } catch (IOException ignored) {}
    }

    private void showHelpDialog() {
        JOptionPane.showMessageDialog(this, "ComfyUI Model Downloader\n\n1. Set your Models path\n2. Load or Drag&Drop a Workflow\n3. Click Start Queue", "Help", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        FlatLaf.setUseNativeWindowDecorations(true);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);   
        context.getBean(Main.class).launch(args);
    }

    @Configuration @ComponentScan("de.tki.comfymodels") public static class AppConfig {}
}

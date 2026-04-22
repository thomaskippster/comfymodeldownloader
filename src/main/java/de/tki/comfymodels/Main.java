package de.tki.comfymodels;

import de.tki.comfymodels.domain.ModelInfo;
import de.tki.comfymodels.service.IDownloadManager;
import de.tki.comfymodels.service.IModelAnalyzer;
import de.tki.comfymodels.service.IWorkflowService;
import de.tki.comfymodels.service.IModelSearchService;
import de.tki.comfymodels.service.impl.ConfigService;
import de.tki.comfymodels.service.impl.GeminiAIService;
import de.tki.comfymodels.service.impl.ModelListService;
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
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;

@Component
public class Main extends JFrame {
    private final IModelAnalyzer analyzer;
    private final IDownloadManager downloadManager;
    private final IWorkflowService workflowService;
    private final IModelSearchService searchService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private GeminiAIService geminiService;

    @Autowired
    private ModelListService modelListService;

    private JTextField modelsPathField;
    private JPasswordField geminiKeyField;
    private JPasswordField hfTokenField;
    private JLabel activeAiModelLabel;
    private JTextArea jsonInputArea;
    private DefaultTableModel tableModel;
    private JLabel statusLabel;
    private JButton downloadButton, pauseButton, stopButton;
    private List<ModelInfo> modelsToDownload;
    private String currentFileName = "input.json";

    public Main(IModelAnalyzer analyzer, IDownloadManager downloadManager,
                IWorkflowService workflowService, IModelSearchService searchService) {
        this.analyzer = analyzer;
        this.downloadManager = downloadManager;
        this.workflowService = workflowService;
        this.searchService = searchService;
    }

    public void launch() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {}

        if (!promptForPassword()) {
            System.exit(0);
        }
        
        SwingUtilities.invokeLater(() -> {
            initUI();
            loadSettingsIntoUI(); 
            updateAiModelDisplay();
            
            // Add WindowListener to stop downloads on close
            addWindowListener(new java.awt.event.WindowAdapter() {
                @Override
                public void windowClosing(java.awt.event.WindowEvent e) {
                    downloadManager.stop();
                    System.exit(0);
                }
            });
            
            setVisible(true);
        });
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
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel settingsPanel = new JPanel(new GridLayout(4, 1));

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
        infoRow.add(helpBtn);

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
            downloadManager.startQueue(modelsToDownload, selected, modelsPathField.getText(),
                (idx, status) -> SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, idx, 7)),       
                () -> SwingUtilities.invokeLater(() -> {
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Queue finished.");
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

        add(settingsPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
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
        for (int i = 0; i < modelsToDownload.size(); i++) {
            ModelInfo info = modelsToDownload.get(i);
            String targetSubDir = "models" + File.separator + (info.getType() != null ? info.getType() : "checkpoints");
            tableModel.addRow(new Object[]{true, info.getType(), info.getName(), info.getSize(), info.getPopularity(), targetSubDir, info.getUrl(), info.getUrl().equals("MISSING") ? "Idle" : "✅ Known Good"});
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
        main.launch();
    }

    @Configuration
    @ComponentScan("de.tki.comfymodels")
    public static class AppConfig {}
}
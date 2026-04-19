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
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

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
    private JProgressBar progressBar;
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

    @Autowired
    public void start() {
        initUI();
        updateAiModelDisplay();
        SwingUtilities.invokeLater(() -> setVisible(true));
    }

    private void updateAiModelDisplay() {
        new Thread(() -> {
            String model = geminiService.discoverBestModel();
            SwingUtilities.invokeLater(() -> activeAiModelLabel.setText("Active AI: " + model));
        }).start();
    }

    private void initUI() {
        setTitle("ComfyUI Downloader (ULTRA-VALIDATION)");
        setSize(1450, 1000);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JPanel settingsPanel = new JPanel(new GridLayout(4, 1));
        
        JPanel pathRow = new JPanel(new BorderLayout());
        pathRow.setBorder(BorderFactory.createTitledBorder("Models Directory"));
        modelsPathField = new JTextField("C:\\pinokio\\api\\comfy.git\\app\\models");
        JButton browsePathBtn = new JButton("Set Folder...");
        browsePathBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                modelsPathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        pathRow.add(modelsPathField, BorderLayout.CENTER);
        pathRow.add(browsePathBtn, BorderLayout.EAST);

        JPanel apiRow = new JPanel(new BorderLayout());
        apiRow.setBorder(BorderFactory.createTitledBorder("Gemini AI API Key"));
        geminiKeyField = new JPasswordField(configService.getGeminiApiKey());
        JButton saveGeminiBtn = new JButton("Save & Discover");
        saveGeminiBtn.addActionListener(e -> {
            configService.setGeminiApiKey(new String(geminiKeyField.getPassword()));
            updateAiModelDisplay();
        });
        apiRow.add(geminiKeyField, BorderLayout.CENTER);
        apiRow.add(saveGeminiBtn, BorderLayout.EAST);

        JPanel hfRow = new JPanel(new BorderLayout());
        hfRow.setBorder(BorderFactory.createTitledBorder("Hugging Face Access Token"));
        hfTokenField = new JPasswordField(configService.getHfToken());
        JButton saveHfBtn = new JButton("Save HF Token");
        saveHfBtn.addActionListener(e -> {
            configService.setHfToken(new String(hfTokenField.getPassword()));
            statusLabel.setText("HF Token saved.");
            analyzeJsonContent(); // Re-validate sizes with token
        });
        hfRow.add(hfTokenField, BorderLayout.CENTER);
        hfRow.add(saveHfBtn, BorderLayout.EAST);

        JPanel infoRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        activeAiModelLabel = new JLabel("Active AI: Detecting...");
        activeAiModelLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        activeAiModelLabel.setForeground(new Color(0, 100, 200));
        infoRow.add(activeAiModelLabel);

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
                    JOptionPane.showMessageDialog(this, "Model list imported successfully! (" + modelListService.getModels().size() + " models)");
                    analyzeJsonContent(); // Re-analyze current workflow with new list
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "Error importing model list: " + ex.getMessage());
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

        String[] columnNames = {"Select", "Type", "Name", "Size", "AI Trust & Source", "URL", "Status"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override public Class<?> getColumnClass(int c) { return c == 0 ? Boolean.class : String.class; }
            @Override public boolean isCellEditable(int r, int c) { return c == 0; }
        };
        JTable modelTable = new JTable(tableModel);
        modelTable.getColumnModel().getColumn(0).setMaxWidth(60);
        modelTable.getColumnModel().getColumn(4).setMinWidth(250);
        JScrollPane tableScroll = new JScrollPane(modelTable);
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBorder(BorderFactory.createTitledBorder("Identified Models"));
        tablePanel.add(tableScroll, BorderLayout.CENTER);

        splitPane.setTopComponent(jsonPanel);
        splitPane.setBottomComponent(tablePanel);
        splitPane.setDividerLocation(300);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        progressBar = new JProgressBar(0, 100);
        statusLabel = new JLabel("Ready");
        
        JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        downloadButton = new JButton("Start Queue");
        downloadButton.setEnabled(false);
        downloadButton.addActionListener(e -> {
            downloadButton.setEnabled(false);
            downloadManager.startQueue(modelsToDownload, modelsPathField.getText(), 
                (idx, status) -> SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, idx, 6)),
                () -> SwingUtilities.invokeLater(() -> {
                    downloadButton.setEnabled(true);
                    statusLabel.setText("Queue finished.");
                })
            );
        });

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

        JPanel progressPanel = new JPanel(new GridLayout(2, 1));
        progressPanel.add(statusLabel);
        progressPanel.add(progressBar);
        bottomPanel.add(progressPanel, BorderLayout.CENTER);
        bottomPanel.add(actionButtons, BorderLayout.EAST);

        add(settingsPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
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
                    List<File> files = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
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
            tableModel.addRow(new Object[]{true, info.getType(), info.getName(), info.getSize(), info.getPopularity(), info.getUrl(), info.getUrl().equals("MISSING") ? "Idle" : "✅ Known Good"});
            if (!info.getUrl().equals("MISSING")) fetchSizeInBackground(info, i);
        }
        downloadButton.setEnabled(!modelsToDownload.isEmpty());
    }

    private void searchMissingOnline() {
        if (modelsToDownload == null) return;
        
        int rowCount = tableModel.getRowCount();
        boolean[] selected = new boolean[rowCount];
        boolean anySelected = false;
        for (int i = 0; i < rowCount; i++) {
            selected[i] = (Boolean) tableModel.getValueAt(i, 0);
            if (selected[i]) anySelected = true;
        }

        if (!anySelected) {
            JOptionPane.showMessageDialog(this, "Please select at least one model in the table to search.");
            return;
        }

        statusLabel.setText("Deep Search in progress...");
        searchService.searchOnline(modelsToDownload, selected, jsonInputArea.getText(), currentFileName,
            (idx, status) -> SwingUtilities.invokeLater(() -> tableModel.setValueAt(status, idx, 6)),
            (idx, info) -> SwingUtilities.invokeLater(() -> {
                tableModel.setValueAt(info.getSize(), idx, 3);
                tableModel.setValueAt(info.getPopularity(), idx, 4);
                tableModel.setValueAt(info.getUrl(), idx, 5);
                tableModel.setValueAt("✅ Found", idx, 6);
            })
        );
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
        context.getBean(Main.class);
    }

    @Configuration
    @ComponentScan("de.tki.comfymodels")
    public static class AppConfig {}
}

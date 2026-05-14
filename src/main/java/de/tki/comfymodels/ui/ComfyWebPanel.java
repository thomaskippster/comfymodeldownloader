package de.tki.comfymodels.ui;

import javax.swing.*;
import java.awt.*;
import java.net.URI;

/**
 * A simplified panel that provides a link to open ComfyUI in the system browser.
 * Replaces the problematic WebView for better compatibility with modern ComfyUI frontends.
 */
public class ComfyWebPanel extends JPanel {

    private String currentUrl;

    public ComfyWebPanel(String initialUrl) {
        this.currentUrl = initialUrl;
        setLayout(new GridBagLayout());
        initComponents();
    }

    private void initComponents() {
        removeAll();
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);

        JLabel iconLabel = new JLabel("🎨");
        iconLabel.setFont(new Font("SansSerif", Font.PLAIN, 64));
        add(iconLabel, gbc);

        gbc.gridy++;
        JLabel titleLabel = new JLabel("ComfyUI Interface is Ready");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        add(titleLabel, gbc);

        gbc.gridy++;
        JLabel infoLabel = new JLabel("The modern ComfyUI v1 interface works best in a full browser.");
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        infoLabel.setForeground(Color.GRAY);
        add(infoLabel, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(30, 10, 10, 10);
        JButton openBtn = new JButton("🌍 Open ComfyUI in Browser");
        openBtn.setFont(new Font("SansSerif", Font.BOLD, 16));
        openBtn.setPreferredSize(new Dimension(300, 50));
        openBtn.addActionListener(e -> openInBrowser());
        add(openBtn, gbc);

        gbc.gridy++;
        gbc.insets = new Insets(5, 10, 10, 10);
        JLabel urlLabel = new JLabel(currentUrl);
        urlLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        urlLabel.setForeground(new Color(100, 150, 255));
        add(urlLabel, gbc);

        revalidate();
        repaint();
    }

    private void openInBrowser() {
        try {
            Desktop.getDesktop().browse(new URI(currentUrl));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not open browser: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Updates the URL and refreshes the UI.
     */
    public void loadUrl(String url) {
        this.currentUrl = url;
        initComponents();
    }

    /**
     * For compatibility with previous calls, though no longer strictly needed for WebView.
     */
    public void reload() {
        initComponents();
    }
}

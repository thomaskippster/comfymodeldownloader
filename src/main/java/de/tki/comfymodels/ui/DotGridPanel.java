package de.tki.comfymodels.ui;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import java.awt.*;

/**
 * A custom JPanel that draws a dot grid background, 
 * similar to the ComfyUI workspace.
 * Now theme-aware and robust for LookAndFeel changes.
 */
public class DotGridPanel extends JPanel {
    private static final int GRID_SPACING = 25;
    private static final int DOT_SIZE = 2;

    public DotGridPanel(LayoutManager layout) {
        super(layout);
        setOpaque(true);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        // Reset background to null to let the new LookAndFeel default take over
        setBackground(null);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        
        // Use a subtle dot grid based on foreground or a calculated color
        Color fg = UIManager.getColor("Separator.foreground");
        if (fg == null) fg = new Color(128, 128, 128, 60);
        else {
            // Make it semi-transparent for the grid effect
            fg = new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 40);
        }
        
        g2.setColor(fg);
        
        int width = getWidth();
        int height = getHeight();

        for (int x = 0; x < width; x += GRID_SPACING) {
            for (int y = 0; y < height; y += GRID_SPACING) {
                g2.fillOval(x, y, DOT_SIZE, DOT_SIZE);
            }
        }
        
        g2.dispose();
    }
}

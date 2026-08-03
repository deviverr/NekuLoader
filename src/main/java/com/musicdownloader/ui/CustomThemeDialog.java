/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.ui;

import com.musicdownloader.utils.LanguageManager;
import com.musicdownloader.utils.Settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;

public class CustomThemeDialog
extends JDialog {
    private Settings settings;
    private LanguageManager languageManager;
    private Color bgStart;
    private Color bgEnd;
    private Color titleColor;
    private Color textColor;
    private Color starColor;
    private Color menuBarColor;
    private Color buttonColor;
    private Color progressBarColor;
    private Color frameBackgroundColor;
    private JSlider transparencySlider;
    private JSlider snowSpeedSlider;
    private JSlider snowBoldnessSlider;
    private JSlider snowTransparencySlider;
    private JTextField backgroundImagePathField;
    private boolean accepted = false;

    public CustomThemeDialog(JFrame parent, Settings settings, LanguageManager languageManager) {
        super(parent, "Custom Theme Configurator", true);
        this.settings = settings;
        this.languageManager = languageManager;
        this.loadCurrentColors();
        this.setSize(500, 600);
        this.setLocationRelativeTo(parent);
        this.setResizable(false);
        this.initComponents();
    }

    private void loadCurrentColors() {
        this.bgStart = this.parseColorOrDefault(this.settings.getCustomBgStart(), new Color(30, 20, 40));
        this.bgEnd = this.parseColorOrDefault(this.settings.getCustomBgEnd(), new Color(60, 40, 80));
        this.titleColor = this.parseColorOrDefault(this.settings.getCustomTitleColor(), new Color(255, 200, 255));
        this.textColor = this.parseColorOrDefault(this.settings.getCustomTextColor(), new Color(255, 255, 255));
        this.starColor = this.parseColorOrDefault(this.settings.getCustomStarColor(), new Color(255, 255, 200));
        this.menuBarColor = this.parseColorOrDefault(this.settings.getCustomMenuBarColor(), new Color(40, 30, 60));
        this.buttonColor = this.parseColorOrDefault(this.settings.getCustomButtonColor(), new Color(80, 50, 100));
        this.progressBarColor = this.parseColorOrDefault(this.settings.getProgressBarColor(), new Color(100, 200, 100));
        this.frameBackgroundColor = this.parseColorOrDefault(this.settings.getFrameBackgroundColor(), new Color(40, 30, 50));
    }

    private Color parseColorOrDefault(String rgb, Color defaultColor) {
        if (rgb == null || rgb.isEmpty()) {
            return defaultColor;
        }
        try {
            String[] parts = rgb.split(",");
            if (parts.length == 3) {
                int r = Integer.parseInt(parts[0].trim());
                int g = Integer.parseInt(parts[1].trim());
                int b = Integer.parseInt(parts[2].trim());
                return new Color(r, g, b);
            }
        }
        catch (NumberFormatException numberFormatException) {
            // empty catch block
        }
        return defaultColor;
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, 1));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(20, 15, 30));
        JLabel titleLabel = new JLabel("\ud83c\udfa8 " + this.languageManager.get("customTheme"));
        titleLabel.setFont(new Font("Dialog", 1, 18));
        titleLabel.setForeground(new Color(255, 200, 255));
        titleLabel.setAlignmentX(0.5f);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        mainPanel.add(this.createColorRow(this.languageManager.get("bgStartColor"), this.bgStart, color -> {
            this.bgStart = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow(this.languageManager.get("bgEndColor"), this.bgEnd, color -> {
            this.bgEnd = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow(this.languageManager.get("titleColor"), this.titleColor, color -> {
            this.titleColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow(this.languageManager.get("textColor"), this.textColor, color -> {
            this.textColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow(this.languageManager.get("starColor"), this.starColor, color -> {
            this.starColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow("Menu Bar Color", this.menuBarColor, color -> {
            this.menuBarColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow("Button Color", this.buttonColor, color -> {
            this.buttonColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow("Progress Bar Color", this.progressBarColor, color -> {
            this.progressBarColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        JPanel bgImagePanel = new JPanel(new FlowLayout(0));
        bgImagePanel.setBackground(new Color(20, 15, 30));
        JLabel bgImageLabel = new JLabel("Background Image: ");
        bgImageLabel.setForeground(Color.WHITE);
        this.backgroundImagePathField = new JTextField(this.settings.getBackgroundImagePath(), 20);
        this.backgroundImagePathField.setEditable(false);
        JButton browseButton = new JButton("Browse...");
        browseButton.setBackground(new Color(100, 60, 140));
        browseButton.setForeground(Color.WHITE);
        browseButton.setFocusPainted(false);
        browseButton.addActionListener(e -> this.browseForBackgroundImage());
        JButton clearButton = new JButton("Clear");
        clearButton.setBackground(new Color(140, 60, 60));
        clearButton.setForeground(Color.WHITE);
        clearButton.setFocusPainted(false);
        clearButton.addActionListener(e -> {
            this.backgroundImagePathField.setText("");
            this.updatePreview();
        });
        bgImagePanel.add(bgImageLabel);
        bgImagePanel.add(this.backgroundImagePathField);
        bgImagePanel.add(browseButton);
        bgImagePanel.add(clearButton);
        mainPanel.add(bgImagePanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        JPanel transparencyPanel = new JPanel(new FlowLayout(0));
        transparencyPanel.setBackground(new Color(20, 15, 30));
        JLabel transparencyLabel = new JLabel("Frame Transparency: ");
        transparencyLabel.setForeground(Color.WHITE);
        this.transparencySlider = new JSlider(0, 100, this.settings.getFrameTransparency());
        this.transparencySlider.setMajorTickSpacing(25);
        this.transparencySlider.setMinorTickSpacing(5);
        this.transparencySlider.setPaintTicks(true);
        this.transparencySlider.setPaintLabels(true);
        JLabel transparencyValue = new JLabel(this.settings.getFrameTransparency() + "%");
        transparencyValue.setForeground(Color.WHITE);
        this.transparencySlider.addChangeListener(e -> transparencyValue.setText(this.transparencySlider.getValue() + "%"));
        transparencyPanel.add(transparencyLabel);
        transparencyPanel.add(this.transparencySlider);
        transparencyPanel.add(transparencyValue);
        mainPanel.add(transparencyPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        JPanel snowSpeedPanel = new JPanel(new FlowLayout(0));
        snowSpeedPanel.setBackground(new Color(20, 15, 30));
        JLabel snowSpeedLabel = new JLabel("Snow/Particles Speed: ");
        snowSpeedLabel.setForeground(Color.WHITE);
        this.snowSpeedSlider = new JSlider(1, 10, this.settings.getSnowSpeed());
        this.snowSpeedSlider.setMajorTickSpacing(3);
        this.snowSpeedSlider.setMinorTickSpacing(1);
        this.snowSpeedSlider.setPaintTicks(true);
        this.snowSpeedSlider.setPaintLabels(true);
        JLabel snowSpeedValue = new JLabel(String.valueOf(this.settings.getSnowSpeed()));
        snowSpeedValue.setForeground(Color.WHITE);
        this.snowSpeedSlider.addChangeListener(e -> snowSpeedValue.setText(String.valueOf(this.snowSpeedSlider.getValue())));
        snowSpeedPanel.add(snowSpeedLabel);
        snowSpeedPanel.add(this.snowSpeedSlider);
        snowSpeedPanel.add(snowSpeedValue);
        mainPanel.add(snowSpeedPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        JPanel snowBoldnessPanel = new JPanel(new FlowLayout(0));
        snowBoldnessPanel.setBackground(new Color(20, 15, 30));
        JLabel snowBoldnessLabel = new JLabel("Snow/Particles Boldness: ");
        snowBoldnessLabel.setForeground(Color.WHITE);
        this.snowBoldnessSlider = new JSlider(1, 5, this.settings.getSnowBoldness());
        this.snowBoldnessSlider.setMajorTickSpacing(1);
        this.snowBoldnessSlider.setMinorTickSpacing(1);
        this.snowBoldnessSlider.setPaintTicks(true);
        this.snowBoldnessSlider.setPaintLabels(true);
        JLabel snowBoldnessValue = new JLabel(String.valueOf(this.settings.getSnowBoldness()));
        snowBoldnessValue.setForeground(Color.WHITE);
        this.snowBoldnessSlider.addChangeListener(e -> snowBoldnessValue.setText(String.valueOf(this.snowBoldnessSlider.getValue())));
        snowBoldnessPanel.add(snowBoldnessLabel);
        snowBoldnessPanel.add(this.snowBoldnessSlider);
        snowBoldnessPanel.add(snowBoldnessValue);
        mainPanel.add(snowBoldnessPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        JPanel snowTransparencyPanel = new JPanel(new FlowLayout(0));
        snowTransparencyPanel.setBackground(new Color(20, 15, 30));
        JLabel snowTransparencyLabel = new JLabel("Snow/Particles Transparency: ");
        snowTransparencyLabel.setForeground(Color.WHITE);
        this.snowTransparencySlider = new JSlider(0, 100, this.settings.getSnowTransparency());
        this.snowTransparencySlider.setMajorTickSpacing(25);
        this.snowTransparencySlider.setMinorTickSpacing(5);
        this.snowTransparencySlider.setPaintTicks(true);
        this.snowTransparencySlider.setPaintLabels(true);
        JLabel snowTransparencyValue = new JLabel(this.settings.getSnowTransparency() + "%");
        snowTransparencyValue.setForeground(Color.WHITE);
        this.snowTransparencySlider.addChangeListener(e -> snowTransparencyValue.setText(this.snowTransparencySlider.getValue() + "%"));
        snowTransparencyPanel.add(snowTransparencyLabel);
        snowTransparencyPanel.add(this.snowTransparencySlider);
        snowTransparencyPanel.add(snowTransparencyValue);
        mainPanel.add(snowTransparencyPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        mainPanel.add(this.createColorRow("Frame Background Color", this.frameBackgroundColor, color -> {
            this.frameBackgroundColor = color;
            this.updatePreview();
        }));
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        JPanel previewPanel = this.createPreviewPanel();
        mainPanel.add(previewPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        JPanel buttonPanel = new JPanel(new FlowLayout(1, 10, 0));
        buttonPanel.setBackground(new Color(20, 15, 30));
        Color buttonBg = new Color(100, 60, 140);
        Color buttonFg = Color.WHITE;
        JButton saveButton = new JButton("\ufffd " + this.languageManager.get("save"));
        saveButton.setBackground(buttonBg);
        saveButton.setForeground(buttonFg);
        saveButton.setFocusPainted(false);
        saveButton.addActionListener(e -> {
            this.saveColors();
            this.accepted = true;
            this.dispose();
        });
        JButton cancelButton = new JButton("\u274c " + this.languageManager.get("cancel"));
        cancelButton.setBackground(buttonBg);
        cancelButton.setForeground(buttonFg);
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> this.dispose());
        JButton exportThemeButton = new JButton("\ud83d\udce4 Export Theme");
        exportThemeButton.setBackground(buttonBg);
        exportThemeButton.setForeground(buttonFg);
        exportThemeButton.setFocusPainted(false);
        exportThemeButton.addActionListener(e -> this.saveThemeDialog());
        JButton importThemeButton = new JButton("\ud83d\udce5 Import Theme");
        importThemeButton.setBackground(buttonBg);
        importThemeButton.setForeground(buttonFg);
        importThemeButton.setFocusPainted(false);
        importThemeButton.addActionListener(e -> this.loadThemeDialog());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        buttonPanel.add(exportThemeButton);
        buttonPanel.add(importThemeButton);
        mainPanel.add(buttonPanel);
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(20);
        scrollPane.setHorizontalScrollBarPolicy(31);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.add(scrollPane);
    }

    private JPanel createColorRow(String label, Color initialColor, ColorChangeListener listener) {
        JPanel rowPanel = new JPanel(new BorderLayout(10, 0));
        rowPanel.setMaximumSize(new Dimension(450, 35));
        rowPanel.setBackground(new Color(20, 15, 30));
        JLabel nameLabel = new JLabel(label);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setPreferredSize(new Dimension(150, 30));
        JPanel colorPanel = new JPanel();
        colorPanel.setBackground(initialColor);
        colorPanel.setPreferredSize(new Dimension(100, 30));
        colorPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 2));
        JButton chooseButton = new JButton("\ud83c\udfa8");
        chooseButton.setBackground(new Color(80, 50, 100));
        chooseButton.setForeground(Color.WHITE);
        chooseButton.setFocusPainted(false);
        chooseButton.addActionListener(e -> {
            Color newColor = JColorChooser.showDialog(this, this.languageManager.get("chooseColor") + " - " + label, initialColor);
            if (newColor != null) {
                colorPanel.setBackground(newColor);
                listener.onColorChanged(newColor);
            }
        });
        rowPanel.add((Component)nameLabel, "West");
        rowPanel.add((Component)colorPanel, "Center");
        rowPanel.add((Component)chooseButton, "East");
        return rowPanel;
    }

    private JPanel createPreviewPanel() {
        JPanel previewContainer = new JPanel(new BorderLayout());
        previewContainer.setMaximumSize(new Dimension(450, 150));
        previewContainer.setBackground(new Color(20, 15, 30));
        previewContainer.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Color.WHITE, 1), this.languageManager.get("preview"), 0, 0, null, Color.WHITE));
        JPanel preview = new JPanel(){

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D)g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0.0f, 0.0f, CustomThemeDialog.this.bgStart, 0.0f, this.getHeight(), CustomThemeDialog.this.bgEnd);
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
                g2d.setColor(CustomThemeDialog.this.starColor);
                for (int i = 0; i < 10; ++i) {
                    int x = i * 40 % this.getWidth();
                    int y = i * 35 % this.getHeight();
                    g2d.fillRect(x, y, 2, 2);
                }
                g2d.setColor(CustomThemeDialog.this.titleColor);
                g2d.setFont(new Font("Dialog", 1, 16));
                g2d.drawString(CustomThemeDialog.this.languageManager.get("title"), 10, 25);
                g2d.setColor(CustomThemeDialog.this.textColor);
                g2d.setFont(new Font("Dialog", 0, 12));
                g2d.drawString(CustomThemeDialog.this.languageManager.get("previewText"), 10, 50);
            }
        };
        preview.setPreferredSize(new Dimension(430, 120));
        previewContainer.add((Component)preview, "Center");
        return previewContainer;
    }

    private void updatePreview() {
        this.repaint();
    }

    private void saveColors() {
        this.settings.setCustomBgStart(this.colorToRgbString(this.bgStart));
        this.settings.setCustomBgEnd(this.colorToRgbString(this.bgEnd));
        this.settings.setCustomTitleColor(this.colorToRgbString(this.titleColor));
        this.settings.setCustomTextColor(this.colorToRgbString(this.textColor));
        this.settings.setCustomStarColor(this.colorToRgbString(this.starColor));
        this.settings.setCustomMenuBarColor(this.colorToRgbString(this.menuBarColor));
        this.settings.setCustomButtonColor(this.colorToRgbString(this.buttonColor));
        this.settings.setProgressBarColor(this.colorToRgbString(this.progressBarColor));
        this.settings.setFrameBackgroundColor(this.colorToRgbString(this.frameBackgroundColor));
        this.settings.setBackgroundImagePath(this.backgroundImagePathField.getText());
        this.settings.setFrameTransparency(this.transparencySlider.getValue());
        this.settings.setSnowSpeed(this.snowSpeedSlider.getValue());
        this.settings.setSnowBoldness(this.snowBoldnessSlider.getValue());
        this.settings.setSnowTransparency(this.snowTransparencySlider.getValue());
    }

    private void browseForBackgroundImage() {
        int result;
        File currentFile;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter(){

            @Override
            public boolean accept(File f) {
                if (f.isDirectory()) {
                    return true;
                }
                String name = f.getName().toLowerCase();
                return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".webp");
            }

            @Override
            public String getDescription() {
                return "Image Files (*.png, *.jpg, *.jpeg, *.gif, *.webp)";
            }
        });
        String currentPath = this.backgroundImagePathField.getText();
        if (!currentPath.isEmpty() && (currentFile = new File(currentPath)).exists()) {
            fileChooser.setCurrentDirectory(currentFile.getParentFile());
        }
        if ((result = fileChooser.showOpenDialog(this)) == 0) {
            File selectedFile = fileChooser.getSelectedFile();
            this.backgroundImagePathField.setText(selectedFile.getAbsolutePath());
            this.updatePreview();
        }
    }

    private String colorToRgbString(Color color) {
        return color.getRed() + "," + color.getGreen() + "," + color.getBlue();
    }

    public boolean isAccepted() {
        return this.accepted;
    }

    private void saveThemeDialog() {
        String themeName = JOptionPane.showInputDialog(this, "Enter theme name:", "Save Custom Theme", -1);
        if (themeName != null && !themeName.trim().isEmpty()) {
            if (!(themeName = themeName.trim()).matches("[a-zA-Z0-9 -]+")) {
                JOptionPane.showMessageDialog(this, "Theme name can only contain letters, numbers, spaces, and hyphens.", "Invalid Name", 0);
                return;
            }
            this.saveColors();
            boolean success = this.settings.saveCustomTheme(themeName);
            if (success) {
                JOptionPane.showMessageDialog(this, "Theme '" + themeName + "' saved successfully!", "Success", 1);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to save theme. Please try again.", "Error", 0);
            }
        }
    }

    private void loadThemeDialog() {
        List<String> themeNames = this.settings.getSavedThemeNames();
        if (themeNames.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No saved themes found. Save a theme first!", "No Themes", 1);
            return;
        }
        Object[] themes = themeNames.toArray(new String[0]);
        String selectedTheme = (String)JOptionPane.showInputDialog(this, "Select a theme to load:", "Load Custom Theme", -1, null, themes, themes[0]);
        if (selectedTheme != null) {
            boolean success = this.settings.loadCustomTheme(selectedTheme);
            if (success) {
                this.bgStart = this.settings.parseColor(this.settings.getCustomBgStart());
                this.bgEnd = this.settings.parseColor(this.settings.getCustomBgEnd());
                this.titleColor = this.settings.parseColor(this.settings.getCustomTitleColor());
                this.textColor = this.settings.parseColor(this.settings.getCustomTextColor());
                this.starColor = this.settings.parseColor(this.settings.getCustomStarColor());
                this.menuBarColor = this.settings.parseColor(this.settings.getCustomMenuBarColor());
                this.buttonColor = this.settings.parseColor(this.settings.getCustomButtonColor());
                this.updatePreview();
                JOptionPane.showMessageDialog(this, "Theme '" + selectedTheme + "' loaded successfully!", "Success", 1);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to load theme. Theme may be corrupted.", "Error", 0);
            }
        }
    }

    @FunctionalInterface
    private static interface ColorChangeListener {
        public void onColorChanged(Color var1);
    }
}

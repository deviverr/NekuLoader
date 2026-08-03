/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.ui;

import com.musicdownloader.ui.CustomThemeDialog;
import com.musicdownloader.ui.PixelButton;
import com.musicdownloader.utils.LanguageManager;
import com.musicdownloader.utils.Settings;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.util.ArrayList;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

public class SettingsDialog
extends JDialog {
    private Settings settings;
    private LanguageManager languageManager;
    private JFrame parentFrame;
    private JComboBox<String> themeComboBox;
    private JComboBox<String> appearanceComboBox;
    private JComboBox<String> languageComboBox;
    private JComboBox<String> outputColorComboBox;
    private JCheckBox folderIconCheckBox;
    private JCheckBox animateBackgroundCheckBox;
    private JCheckBox catModeCheckBox;
    private JSlider snowSpeedSlider;
    private JSlider snowBoldnessSlider;
    private JSlider snowTransparencySlider;
    private boolean settingsChanged = false;

    public SettingsDialog(JFrame parent, Settings settings, LanguageManager languageManager) {
        super(parent, "Settings", true);
        this.settings = settings;
        this.languageManager = languageManager;
        this.parentFrame = parent;
        this.setSize(520, 580);
        this.setMinimumSize(new Dimension(480, 530));
        this.setLocationRelativeTo(parent);
        this.setResizable(true);
        this.initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        Color panelBackground = this.settings.isModernAppearance() ? new Color(245, 247, 249) : new Color(40, 30, 50);
        Color panelForeground = this.settings.isModernAppearance() ? new Color(32, 39, 45) : Color.WHITE;
        mainPanel.setBackground(panelBackground);
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, 1));
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPanel.setBackground(panelBackground);
        JLabel titleLabel = new JLabel("\u2699 Settings");
        titleLabel.setFont(new Font("Monospaced", 1, 20));
        titleLabel.setForeground(this.settings.isModernAppearance() ? new Color(27, 92, 99) : new Color(255, 200, 255));
        titleLabel.setAlignmentX(0.0f);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(15));
        this.appearanceComboBox = new JComboBox<String>(new String[]{Settings.APPEARANCE_LEGACY, Settings.APPEARANCE_MODERN});
        this.appearanceComboBox.setSelectedItem(this.settings.getAppearance());
        this.appearanceComboBox.setFont(new Font(this.settings.isModernAppearance() ? "SansSerif" : "Monospaced", 0, 12));
        contentPanel.add(this.createSettingPanel("Appearance:", this.appearanceComboBox));
        contentPanel.add(Box.createVerticalStrut(15));
        this.themeComboBox = new JComboBox<String>(new String[]{"Classic", "Dark", "Light", "System", "Cyberpunk", "Neon", "Custom"});
        this.themeComboBox.setSelectedItem(this.settings.getTheme());
        this.themeComboBox.setFont(new Font("Monospaced", 0, 12));
        this.themeComboBox.setRenderer(new DefaultListCellRenderer(){

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index == -1 && value != null) {
                    this.setText("Current: " + value.toString());
                }
                return this;
            }
        });
        JPanel themePanel = this.createSettingPanel("Theme:", this.themeComboBox);
        JButton customizeThemeButton = new JButton("\ud83c\udfa8");
        customizeThemeButton.setBackground(new Color(80, 50, 100));
        customizeThemeButton.setForeground(Color.WHITE);
        customizeThemeButton.setFocusPainted(false);
        customizeThemeButton.setToolTipText(this.languageManager.get("customizeTheme"));
        customizeThemeButton.addActionListener(e -> {
            CustomThemeDialog dialog = new CustomThemeDialog(this.parentFrame, this.settings, this.languageManager);
            dialog.setVisible(true);
            if (dialog.isAccepted()) {
                this.themeComboBox.setSelectedItem("Custom");
            }
        });
        themePanel.add(customizeThemeButton);
        contentPanel.add(themePanel);
        contentPanel.add(Box.createVerticalStrut(15));
        this.languageComboBox = new JComboBox<String>(new String[]{"English", "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430", "Espa\u00f1ol", "Fran\u00e7ais", "Deutsch", "\u0420\u0443\u0441\u0441\u043a\u0438\u0439"});
        this.languageComboBox.setSelectedItem(this.settings.getLanguage());
        this.languageComboBox.setFont(new Font("Monospaced", 0, 12));
        this.languageComboBox.setRenderer(new DefaultListCellRenderer(){

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index == -1 && value != null) {
                    this.setText("Current: " + value.toString());
                }
                return this;
            }
        });
        JPanel languagePanel = this.createSettingPanel("Language:", this.languageComboBox);
        contentPanel.add(languagePanel);
        contentPanel.add(Box.createVerticalStrut(15));
        this.outputColorComboBox = new JComboBox<String>(new String[]{"Green", "Blue", "Cyan", "Yellow", "Orange", "Pink", "Red", "White"});
        String currentColor = this.settings.getOutputColor();
        this.outputColorComboBox.setSelectedItem(currentColor.substring(0, 1).toUpperCase() + currentColor.substring(1));
        this.outputColorComboBox.setFont(new Font("Monospaced", 0, 12));
        this.outputColorComboBox.setRenderer(new DefaultListCellRenderer(){

            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (index == -1 && value != null) {
                    this.setText("Current: " + value.toString());
                }
                return this;
            }
        });
        JPanel colorPanel = this.createSettingPanel("Output Color:", this.outputColorComboBox);
        contentPanel.add(colorPanel);
        contentPanel.add(Box.createVerticalStrut(15));
        JPanel iconPanel = new JPanel(new FlowLayout(0, 0, 0));
        iconPanel.setOpaque(false);
        iconPanel.setAlignmentX(0.0f);
        iconPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        this.folderIconCheckBox = new JCheckBox("Customize folder icons with album covers");
        this.folderIconCheckBox.setSelected(this.settings.isCustomFolderIconsEnabled());
        this.folderIconCheckBox.setForeground(panelForeground);
        this.folderIconCheckBox.setFont(new Font("Monospaced", 0, 12));
        this.folderIconCheckBox.setOpaque(false);
        this.folderIconCheckBox.setFocusPainted(false);
        iconPanel.add(this.folderIconCheckBox);
        contentPanel.add(iconPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        JPanel animPanel = new JPanel(new FlowLayout(0, 0, 0));
        animPanel.setOpaque(false);
        animPanel.setAlignmentX(0.0f);
        animPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        this.animateBackgroundCheckBox = new JCheckBox("Animate background stars");
        this.animateBackgroundCheckBox.setSelected(this.settings.isAnimateBackground());
        this.animateBackgroundCheckBox.setForeground(panelForeground);
        this.animateBackgroundCheckBox.setFont(new Font("Monospaced", 0, 12));
        this.animateBackgroundCheckBox.setOpaque(false);
        this.animateBackgroundCheckBox.setFocusPainted(false);
        animPanel.add(this.animateBackgroundCheckBox);
        contentPanel.add(animPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        JPanel catModePanel = new JPanel(new FlowLayout(0, 0, 0));
        catModePanel.setOpaque(false);
        catModePanel.setAlignmentX(0.0f);
        catModePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        this.catModeCheckBox = new JCheckBox(":3");
        this.catModeCheckBox.setSelected(this.settings.isCatModeEnabled());
        this.catModeCheckBox.setForeground(panelForeground);
        this.catModeCheckBox.setFont(new Font("Monospaced", 0, 12));
        this.catModeCheckBox.setOpaque(false);
        this.catModeCheckBox.setFocusPainted(false);
        catModePanel.add(this.catModeCheckBox);
        contentPanel.add(catModePanel);
        contentPanel.add(Box.createVerticalStrut(15));
        JPanel snowSpeedPanel = new JPanel(new FlowLayout(0, 0, 0));
        snowSpeedPanel.setOpaque(false);
        snowSpeedPanel.setAlignmentX(0.0f);
        JLabel snowSpeedLabel = new JLabel("Snow/Particles Speed: ");
        snowSpeedLabel.setForeground(panelForeground);
        snowSpeedLabel.setFont(new Font("Monospaced", 0, 12));
        this.snowSpeedSlider = new JSlider(1, 10, this.settings.getSnowSpeed());
        this.snowSpeedSlider.setMajorTickSpacing(3);
        this.snowSpeedSlider.setPaintTicks(true);
        this.snowSpeedSlider.setPreferredSize(new Dimension(200, 40));
        snowSpeedPanel.add(snowSpeedLabel);
        snowSpeedPanel.add(this.snowSpeedSlider);
        contentPanel.add(snowSpeedPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        JPanel snowBoldnessPanel = new JPanel(new FlowLayout(0, 0, 0));
        snowBoldnessPanel.setOpaque(false);
        snowBoldnessPanel.setAlignmentX(0.0f);
        JLabel snowBoldnessLabel = new JLabel("Snow/Particles Boldness: ");
        snowBoldnessLabel.setForeground(panelForeground);
        snowBoldnessLabel.setFont(new Font("Monospaced", 0, 12));
        this.snowBoldnessSlider = new JSlider(1, 5, this.settings.getSnowBoldness());
        this.snowBoldnessSlider.setMajorTickSpacing(1);
        this.snowBoldnessSlider.setPaintTicks(true);
        this.snowBoldnessSlider.setPreferredSize(new Dimension(200, 40));
        snowBoldnessPanel.add(snowBoldnessLabel);
        snowBoldnessPanel.add(this.snowBoldnessSlider);
        contentPanel.add(snowBoldnessPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        JPanel snowTransparencyPanel = new JPanel(new FlowLayout(0, 0, 0));
        snowTransparencyPanel.setOpaque(false);
        snowTransparencyPanel.setAlignmentX(0.0f);
        JLabel snowTransparencyLabel = new JLabel("Snow/Particles Transparency: ");
        snowTransparencyLabel.setForeground(panelForeground);
        snowTransparencyLabel.setFont(new Font("Monospaced", 0, 12));
        this.snowTransparencySlider = new JSlider(0, 100, this.settings.getSnowTransparency());
        this.snowTransparencySlider.setMajorTickSpacing(25);
        this.snowTransparencySlider.setPaintTicks(true);
        this.snowTransparencySlider.setPreferredSize(new Dimension(200, 40));
        snowTransparencyPanel.add(snowTransparencyLabel);
        snowTransparencyPanel.add(this.snowTransparencySlider);
        contentPanel.add(snowTransparencyPanel);
        contentPanel.add(Box.createVerticalStrut(10));
        JTextArea infoText = new JTextArea("Note: Folder icon customization works on Windows.\nAlbum covers will be set as folder icons automatically\nwhen downloading albums or playlists.\n\nBackground animation shows falling stars (may use more CPU).");
        infoText.setEditable(false);
        infoText.setFocusable(false);
        infoText.setFont(new Font("Monospaced", 0, 10));
        infoText.setForeground(new Color(180, 180, 180));
        infoText.setBackground(panelBackground);
        infoText.setBorder(null);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        infoText.setAlignmentX(0.0f);
        infoText.setMaximumSize(new Dimension(450, 80));
        contentPanel.add(infoText);
        contentPanel.add(Box.createVerticalStrut(20));
        JPanel buttonPanel = new JPanel(new FlowLayout(0, 10, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setAlignmentX(0.0f);
        buttonPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        PixelButton saveButton = new PixelButton("\u2713 SAVE");
        saveButton.addActionListener(e -> this.saveSettings());
        PixelButton cancelButton = new PixelButton("\u2717 CANCEL");
        cancelButton.addActionListener(e -> this.dispose());
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        contentPanel.add(buttonPanel);
        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(31);
        mainPanel.add((Component)scrollPane, "Center");
        this.add(mainPanel);
    }

    private JPanel createSettingPanel(String label, JComboBox<String> comboBox) {
        JPanel panel = new JPanel(new FlowLayout(0, 5, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(0.0f);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
        JLabel settingLabel = new JLabel(label);
        settingLabel.setForeground(this.settings.isModernAppearance() ? new Color(32, 39, 45) : Color.WHITE);
        settingLabel.setFont(new Font("Monospaced", 1, 14));
        settingLabel.setPreferredSize(new Dimension(130, 30));
        comboBox.setPreferredSize(new Dimension(300, 30));
        panel.add(settingLabel);
        panel.add(comboBox);
        return panel;
    }

    private void saveSettings() {
        boolean needsRestart;
        String oldTheme = this.settings.getTheme();
        String oldLanguage = this.settings.getLanguage();
        String oldAppearance = this.settings.getAppearance();
        boolean oldAnimateBg = this.settings.isAnimateBackground();
        String oldOutputColor = this.settings.getOutputColor();
        boolean oldCatMode = this.settings.isCatModeEnabled();
        String newTheme = (String)this.themeComboBox.getSelectedItem();
        String newLanguage = (String)this.languageComboBox.getSelectedItem();
        String newAppearance = (String)this.appearanceComboBox.getSelectedItem();
        boolean newAnimateBg = this.animateBackgroundCheckBox.isSelected();
        boolean newCatMode = this.catModeCheckBox.isSelected();
        String newOutputColor = ((String)this.outputColorComboBox.getSelectedItem()).toLowerCase();
        this.settings.setTheme(newTheme);
        this.settings.setLanguage(newLanguage);
        this.settings.setAppearance(newAppearance);
        this.settings.setCustomFolderIcons(this.folderIconCheckBox.isSelected());
        this.settings.setAnimateBackground(newAnimateBg);
        this.settings.setCatMode(newCatMode);
        this.settings.setOutputColor(newOutputColor);
        this.settings.setSnowSpeed(this.snowSpeedSlider.getValue());
        this.settings.setSnowBoldness(this.snowBoldnessSlider.getValue());
        this.settings.setSnowTransparency(this.snowTransparencySlider.getValue());
        this.settings.saveSettings();
        this.settingsChanged = true;
        boolean bl = needsRestart = !oldTheme.equals(newTheme) || !oldLanguage.equals(newLanguage) || !oldAppearance.equals(newAppearance) || oldAnimateBg != newAnimateBg || oldCatMode != newCatMode || !oldOutputColor.equals(newOutputColor);
        if (needsRestart) {
            Object[] options = new Object[]{"Restart Now", "Restart Later"};
            int choice = JOptionPane.showOptionDialog(this, "Settings changed!\n\nThe application needs to restart to apply changes.\nDo you want to restart now?", "Restart Required", 0, 3, null, options, options[0]);
            if (choice == 0) {
                this.restartApplication();
            } else {
                JOptionPane.showMessageDialog(this, "Settings saved!\nPlease restart the application manually to apply changes.", "Settings Saved", 1);
            }
        } else {
            JOptionPane.showMessageDialog(this, "Settings saved successfully!", "Settings Saved", 1);
        }
        this.dispose();
    }

    private void restartApplication() {
        try {
            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            String jarPath = new File(SettingsDialog.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getPath();
            if (jarPath.endsWith(".jar")) {
                ArrayList<String> command = new ArrayList<String>();
                command.add(javaBin);
                command.add("-jar");
                command.add(jarPath);
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.start();
                System.exit(0);
            } else {
                JOptionPane.showMessageDialog(this, "Running from IDE. Please restart manually.", "Manual Restart Required", 1);
            }
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not restart automatically.\nPlease restart the application manually.\n\nError: " + e.getMessage(), "Restart Failed", 0);
        }
    }

    public boolean isSettingsChanged() {
        return this.settingsChanged;
    }

}

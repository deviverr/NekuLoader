/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.json.JSONObject
 */
package com.nekuloader.packager;

import com.musicdownloader.AppInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;
import org.json.JSONObject;

public class PackagerApp
extends JFrame {
    private JTextField nameField;
    private JTextField versionField;
    private JTextField vendorField;
    private JTextField descriptionField;
    private JTextField authorField;
    private JTextField websiteField;
    private JTextField iconPathField;
    private JCheckBox includeJRECheckBox;
    private JCheckBox includePythonCheckBox;
    private JCheckBox includeFFmpegCheckBox;
    private JCheckBox windowsExeCheckBox;
    private JCheckBox macAppCheckBox;
    private JCheckBox linuxDebCheckBox;
    private JTextArea logArea;
    private JButton packageButton;
    private JSONObject config;

    public PackagerApp() {
        this.setTitle(AppInfo.NAME + " Packager - Create Installers");
        this.setSize(900, 800);
        this.setDefaultCloseOperation(3);
        this.setLocationRelativeTo(null);
        this.loadConfig();
        this.initComponents();
    }

    private void loadConfig() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("packager-config.json", new String[0])));
            this.config = new JSONObject(content);
        }
        catch (Exception e) {
            this.config = this.createDefaultConfig();
        }
    }

    private JSONObject createDefaultConfig() {
        JSONObject cfg = new JSONObject();
        cfg.put("app", (Object)new JSONObject().put("name", (Object)AppInfo.NAME).put("version", (Object)AppInfo.VERSION).put("vendor", (Object)AppInfo.VENDOR));
        return cfg;
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(40, 30, 50));
        JLabel titleLabel = new JLabel("\ud83d\udce6 NekuLoader Packager & Installer Creator", 0);
        titleLabel.setFont(new Font("Monospaced", 1, 20));
        titleLabel.setForeground(new Color(255, 200, 255));
        mainPanel.add((Component)titleLabel, "North");
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Monospaced", 0, 12));
        JPanel metadataPanel = this.createMetadataPanel();
        tabbedPane.addTab("\ud83d\udcdd Metadata", metadataPanel);
        JPanel dependenciesPanel = this.createDependenciesPanel();
        tabbedPane.addTab("\ud83d\udce6 Dependencies", dependenciesPanel);
        JPanel outputPanel = this.createOutputPanel();
        tabbedPane.addTab("\ud83d\ude80 Build", outputPanel);
        mainPanel.add((Component)tabbedPane, "Center");
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
        bottomPanel.setBackground(new Color(40, 30, 50));
        JLabel logLabel = new JLabel("\ud83d\udccb Build Log:");
        logLabel.setForeground(Color.WHITE);
        logLabel.setFont(new Font("Monospaced", 1, 12));
        bottomPanel.add((Component)logLabel, "North");
        this.logArea = new JTextArea(8, 50);
        this.logArea.setEditable(false);
        this.logArea.setFont(new Font("Consolas", 0, 11));
        this.logArea.setBackground(new Color(20, 15, 25));
        this.logArea.setForeground(new Color(0, 255, 100));
        JScrollPane scrollPane = new JScrollPane(this.logArea);
        bottomPanel.add((Component)scrollPane, "Center");
        mainPanel.add((Component)bottomPanel, "South");
        this.add(mainPanel);
    }

    private JPanel createMetadataPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(40, 30, 50));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = 2;
        gbc.insets = new Insets(5, 5, 5, 5);
        JSONObject app = this.config.optJSONObject("app");
        JSONObject metadata = this.config.optJSONObject("metadata");
        int row = 0;
        int n = row++;
        this.nameField = this.createTextField(app != null ? app.optString("name", AppInfo.NAME) : AppInfo.NAME);
        this.addLabelField(panel, gbc, n, "Application Name:", this.nameField);
        int n2 = row++;
        this.versionField = this.createTextField(app != null ? app.optString("version", AppInfo.VERSION) : AppInfo.VERSION);
        this.addLabelField(panel, gbc, n2, "Version:", this.versionField);
        int n3 = row++;
        this.vendorField = this.createTextField(app != null ? app.optString("vendor", AppInfo.VENDOR) : AppInfo.VENDOR);
        this.addLabelField(panel, gbc, n3, "Vendor:", this.vendorField);
        int n4 = row++;
        this.descriptionField = this.createTextField(app != null ? app.optString("description", "") : "");
        this.addLabelField(panel, gbc, n4, "Description:", this.descriptionField);
        int n5 = row++;
        this.authorField = this.createTextField(metadata != null ? metadata.optString("author", "deviver") : "deviver");
        this.addLabelField(panel, gbc, n5, "Author:", this.authorField);
        int n6 = row++;
        this.websiteField = this.createTextField(metadata != null ? metadata.optString("website", "https://deviver.art") : "https://deviver.art");
        this.addLabelField(panel, gbc, n6, "Website:", this.websiteField);
        gbc.gridx = 0;
        gbc.gridy = row++;
        gbc.gridwidth = 1;
        JLabel iconLabel = new JLabel("Icon Path:");
        iconLabel.setForeground(Color.WHITE);
        iconLabel.setFont(new Font("Monospaced", 1, 12));
        panel.add((Component)iconLabel, gbc);
        JPanel iconPanel = new JPanel(new BorderLayout(5, 5));
        iconPanel.setOpaque(false);
        this.iconPathField = this.createTextField("installer/icon.ico");
        JButton browseButton = new JButton("Browse...");
        browseButton.setBackground(new Color(80, 50, 100));
        browseButton.setForeground(Color.WHITE);
        browseButton.setFocusPainted(false);
        browseButton.addActionListener(e -> this.browseForIcon());
        iconPanel.add((Component)this.iconPathField, "Center");
        iconPanel.add((Component)browseButton, "East");
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        panel.add((Component)iconPanel, gbc);
        return panel;
    }

    private JPanel createDependenciesPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(40, 30, 50));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        JPanel checkBoxPanel = new JPanel(new GridLayout(0, 1, 5, 10));
        checkBoxPanel.setOpaque(false);
        this.includeJRECheckBox = new JCheckBox("", true);
        JPanel jrePanel = this.createDependencyPanel("\u2615 Include Java Runtime (JRE 21)", "Bundle Java 21 with installer - Required for app to run", this.includeJRECheckBox);
        checkBoxPanel.add(jrePanel);
        this.includePythonCheckBox = new JCheckBox("", false);
        this.includePythonCheckBox.setEnabled(false);
        JPanel pythonPanel = this.createDependencyPanel("\ud83d\udc0d Include Python tools", "Planned for a later self-contained package; install yt-dlp and SpotDL separately in 3.1", this.includePythonCheckBox);
        checkBoxPanel.add(pythonPanel);
        this.includeFFmpegCheckBox = new JCheckBox("", false);
        this.includeFFmpegCheckBox.setEnabled(false);
        JPanel ffmpegPanel = this.createDependencyPanel("\ud83c\udfac Include FFmpeg", "Planned for a later self-contained package; install FFmpeg separately in 3.1", this.includeFFmpegCheckBox);
        checkBoxPanel.add(ffmpegPanel);
        panel.add((Component)checkBoxPanel, "North");
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(new Color(30, 20, 40));
        infoPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(100, 70, 130), 2), new EmptyBorder(10, 10, 10, 10)));
        JTextArea infoText = new JTextArea("Dependency status\n\nThe Java runtime is bundled by jpackage.\n\nNekuLoader 3.1 currently discovers yt-dlp, SpotDL, FFmpeg, and ADB from the system or app-adjacent tool folders. The packager will not claim those tools are bundled until their platform-specific archives and notices are verified.");
        infoText.setEditable(false);
        infoText.setFont(new Font("Monospaced", 0, 11));
        infoText.setForeground(new Color(200, 200, 255));
        infoText.setBackground(new Color(30, 20, 40));
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        infoPanel.add(infoText);
        panel.add((Component)infoPanel, "Center");
        return panel;
    }

    private JPanel createOutputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBackground(new Color(40, 30, 50));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        JPanel platformPanel = new JPanel(new GridLayout(0, 1, 5, 10));
        platformPanel.setOpaque(false);
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("windows");
        boolean isMac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        boolean isLinux = System.getProperty("os.name", "").toLowerCase().contains("linux");
        this.windowsExeCheckBox = new JCheckBox("", isWindows);
        this.windowsExeCheckBox.setEnabled(isWindows);
        JPanel winPanel = this.createPlatformPanel("\ud83e\ude9f Windows (.exe)", "Create a native Windows installer with jpackage (must run on Windows)", this.windowsExeCheckBox);
        platformPanel.add(winPanel);
        this.macAppCheckBox = new JCheckBox("", isMac);
        this.macAppCheckBox.setEnabled(isMac);
        JPanel macPanel = this.createPlatformPanel("\ud83c\udf4e macOS (.dmg)", "Create a native macOS disk image with jpackage", this.macAppCheckBox);
        platformPanel.add(macPanel);
        this.linuxDebCheckBox = new JCheckBox("", isLinux);
        this.linuxDebCheckBox.setEnabled(isLinux);
        JPanel linuxPanel = this.createPlatformPanel("\ud83d\udc27 Linux (.deb)", "Create a native Debian package with jpackage", this.linuxDebCheckBox);
        platformPanel.add(linuxPanel);
        panel.add((Component)platformPanel, "North");
        this.packageButton = new JButton("\ud83d\ude80 Build Installer(s)");
        this.packageButton.setFont(new Font("Monospaced", 1, 16));
        this.packageButton.setBackground(new Color(100, 50, 150));
        this.packageButton.setForeground(Color.WHITE);
        this.packageButton.setFocusPainted(false);
        this.packageButton.setPreferredSize(new Dimension(200, 50));
        this.packageButton.addActionListener(e -> this.startPackaging());
        JPanel buttonPanel = new JPanel(new FlowLayout(1));
        buttonPanel.setOpaque(false);
        buttonPanel.add(this.packageButton);
        panel.add((Component)buttonPanel, "South");
        return panel;
    }

    private JPanel createDependencyPanel(String title, String description, JCheckBox checkBox) {
        JPanel panel = new JPanel(new BorderLayout(10, 5));
        panel.setBackground(new Color(50, 35, 60));
        panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(80, 60, 100), 1), new EmptyBorder(10, 10, 10, 10)));
        checkBox.setOpaque(false);
        checkBox.setForeground(Color.WHITE);
        checkBox.setFont(new Font("Monospaced", 1, 13));
        checkBox.setText(title);
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Monospaced", 0, 10));
        descLabel.setForeground(new Color(180, 180, 200));
        panel.add((Component)checkBox, "North");
        panel.add((Component)descLabel, "Center");
        return panel;
    }

    private JPanel createPlatformPanel(String title, String description, JCheckBox checkBox) {
        return this.createDependencyPanel(title, description, checkBox);
    }

    private JTextField createTextField(String text) {
        JTextField field = new JTextField(text);
        field.setFont(new Font("Monospaced", 0, 12));
        field.setBackground(new Color(60, 45, 70));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(100, 70, 130), 1), new EmptyBorder(5, 8, 5, 8)));
        return field;
    }

    private void addLabelField(JPanel panel, GridBagConstraints gbc, int row, String label, JTextField field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0.3;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(Color.WHITE);
        lbl.setFont(new Font("Monospaced", 1, 12));
        panel.add((Component)lbl, gbc);
        gbc.gridx = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.7;
        panel.add((Component)field, gbc);
    }

    private void browseForIcon() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileFilter(){

            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".ico") || f.getName().toLowerCase().endsWith(".png") || f.getName().toLowerCase().endsWith(".icns");
            }

            @Override
            public String getDescription() {
                return "Icon files (*.ico, *.png, *.icns)";
            }
        });
        if (chooser.showOpenDialog(this) == 0) {
            this.iconPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void startPackaging() {
        if (!this.windowsExeCheckBox.isSelected() && !this.macAppCheckBox.isSelected() && !this.linuxDebCheckBox.isSelected()) {
            JOptionPane.showMessageDialog(this, "Select the package for this operating system.", "Nothing to Build", JOptionPane.WARNING_MESSAGE);
            return;
        }
        this.packageButton.setEnabled(false);
        this.logArea.setText("");
        this.log("\ud83d\ude80 Starting packaging process...\n");
        new Thread(() -> {
            try {
                this.updateConfig();
                this.saveConfig();
                if (this.windowsExeCheckBox.isSelected()) {
                    this.buildWindowsInstaller();
                }
                if (this.macAppCheckBox.isSelected()) {
                    this.buildMacInstaller();
                }
                if (this.linuxDebCheckBox.isSelected()) {
                    this.buildLinuxInstaller();
                }
                this.log("\n\u2705 Packaging complete!\n");
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Installers created successfully!\nCheck the 'dist' folder.", "Success", 1);
                    this.packageButton.setEnabled(true);
                });
            }
            catch (Exception e) {
                this.log("\n\u274c Error: " + e.getMessage() + "\n");
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    JOptionPane.showMessageDialog(this, "Error during packaging:\n" + e.getMessage(), "Error", 0);
                    this.packageButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void updateConfig() {
        JSONObject app = this.config.optJSONObject("app");
        if (app == null) {
            app = new JSONObject();
            this.config.put("app", (Object)app);
        }
        app.put("name", (Object)this.nameField.getText());
        app.put("version", (Object)this.versionField.getText());
        app.put("vendor", (Object)this.vendorField.getText());
        app.put("description", (Object)this.descriptionField.getText());
        JSONObject metadata = this.config.optJSONObject("metadata");
        if (metadata == null) {
            metadata = new JSONObject();
            this.config.put("metadata", (Object)metadata);
        }
        metadata.put("author", (Object)this.authorField.getText());
        metadata.put("website", (Object)this.websiteField.getText());
        JSONObject icon = this.config.optJSONObject("icon");
        if (icon == null) {
            icon = new JSONObject();
            this.config.put("icon", (Object)icon);
        }
        icon.put("windows", (Object)this.iconPathField.getText());
        JSONObject installer = this.config.optJSONObject("installer");
        if (installer == null) {
            installer = new JSONObject();
            this.config.put("installer", (Object)installer);
        }
        installer.put("includeJRE", this.includeJRECheckBox.isSelected());
        JSONObject deps = installer.optJSONObject("dependencies");
        if (deps == null) {
            deps = new JSONObject();
            installer.put("dependencies", (Object)deps);
        }
        deps.put("includePython", this.includePythonCheckBox.isSelected());
        deps.put("includeFFmpeg", this.includeFFmpegCheckBox.isSelected());
    }

    private void saveConfig() throws IOException {
        Files.write(Paths.get("packager-config.json", new String[0]), this.config.toString(2).getBytes(), new OpenOption[0]);
        this.log("\ud83d\udcdd Configuration saved\n");
    }

    private void buildWindowsInstaller() throws Exception {
        this.runPackagingScript("exe");
    }

    private void buildMacInstaller() throws Exception {
        this.runPackagingScript("dmg");
    }

    private void buildLinuxInstaller() throws Exception {
        this.runPackagingScript("deb");
    }

    private void runPackagingScript(String packageType) throws Exception {
        this.log("\nBuilding native " + packageType + " package...\n");
        ProcessBuilder builder;
        if (System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            builder = new ProcessBuilder("bash", "package.sh", packageType);
        } else {
            builder = new ProcessBuilder("./package.sh", packageType);
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                this.log(line + "\n");
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("jpackage failed with exit code " + exitCode);
        }
    }

    private void createNSISScript() throws IOException {
        this.log("   NSIS script created at installer/windows/setup.nsi\n");
    }

    private void downloadJRE(String platform) {
        this.log("   \ud83d\udce5 Downloading JRE 21 for " + platform + "...\n");
        this.log("   \u2705 JRE downloaded\n");
    }

    private void downloadPython(String platform) {
        this.log("   \ud83d\udce5 Downloading Python 3.11 for " + platform + "...\n");
        this.log("   \ud83d\udce5 Installing yt-dlp...\n");
        this.log("   \ud83d\udce5 Installing spotdl...\n");
        this.log("   \u2705 Python setup complete\n");
    }

    private void downloadFFmpeg(String platform) {
        this.log("   \ud83d\udce5 Downloading FFmpeg for " + platform + "...\n");
        this.log("   \u2705 FFmpeg downloaded\n");
    }

    private void compileNSIS() {
        this.log("   \ud83d\udd28 Compiling NSIS installer...\n");
        this.log("   \u2705 Installer compiled successfully\n");
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            this.logArea.append(message);
            this.logArea.setCaretPosition(this.logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> {
            PackagerApp app = new PackagerApp();
            app.setVisible(true);
        });
    }
}

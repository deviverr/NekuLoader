/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.ui;

import com.musicdownloader.AppInfo;
import com.musicdownloader.ui.PixelButton;
import java.awt.Color;
import java.awt.Font;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

public class AboutDialog
extends JDialog {
    public AboutDialog(JFrame parent) {
        super(parent, "About", true);
        this.setSize(500, 550);
        this.setLocationRelativeTo(parent);
        this.setResizable(false);
        this.initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, 1));
        mainPanel.setBorder(new EmptyBorder(30, 30, 30, 30));
        mainPanel.setBackground(new Color(40, 30, 50));
        JLabel titleLabel = new JLabel("\u266b " + AppInfo.NAME + " \u266b");
        titleLabel.setFont(new Font("Monospaced", 1, 20));
        titleLabel.setForeground(new Color(255, 200, 255));
        titleLabel.setAlignmentX(0.5f);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        this.addInfoLabel(mainPanel, "Version: " + AppInfo.VERSION, 16, true);
        mainPanel.add(Box.createVerticalStrut(20));
        JLabel sysInfoLabel = new JLabel("System Information");
        sysInfoLabel.setFont(new Font("Monospaced", 1, 14));
        sysInfoLabel.setForeground(new Color(200, 255, 200));
        sysInfoLabel.setAlignmentX(0.5f);
        mainPanel.add(sysInfoLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        String javaVersion = System.getProperty("java.version");
        String javaVendor = System.getProperty("java.vendor");
        this.addInfoLabel(mainPanel, "Java: " + javaVersion + " (" + javaVendor + ")", 11, false);
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        this.addInfoLabel(mainPanel, "OS: " + osName + " " + osVersion + " (" + osArch + ")", 11, false);
        mainPanel.add(Box.createVerticalStrut(20));
        JLabel featuresLabel = new JLabel("Features");
        featuresLabel.setFont(new Font("Monospaced", 1, 14));
        featuresLabel.setForeground(new Color(200, 255, 200));
        featuresLabel.setAlignmentX(0.5f);
        mainPanel.add(featuresLabel);
        mainPanel.add(Box.createVerticalStrut(10));
        JTextArea featuresText = new JTextArea("\u2713 Multiple music sources (Spotify, YouTube, SoundCloud, etc.)\n\u2713 Various audio formats (FLAC, MP3, OPUS, WAV, etc.)\n\u2713 Automatic metadata and cover art embedding\n\u2713 Custom folder icons for albums\n\u2713 Multi-language support (6 languages)\n\u2713 Multiple themes (6 themes)\n\u2713 Track number formatting\n\u2713 Batch download support");
        featuresText.setEditable(false);
        featuresText.setFocusable(false);
        featuresText.setFont(new Font("Monospaced", 0, 11));
        featuresText.setForeground(Color.WHITE);
        featuresText.setBackground(new Color(40, 30, 50));
        featuresText.setBorder(null);
        featuresText.setAlignmentX(0.5f);
        mainPanel.add(featuresText);
        mainPanel.add(Box.createVerticalStrut(20));
        this.addInfoLabel(mainPanel, "Dependencies", 12, true);
        this.addInfoLabel(mainPanel, "yt-dlp, spotdl, FFmpeg", 10, false);
        mainPanel.add(Box.createVerticalStrut(20));
        JLabel soundLabel = new JLabel("Sound Effects");
        soundLabel.setFont(new Font("Monospaced", 1, 12));
        soundLabel.setForeground(new Color(255, 200, 150));
        soundLabel.setAlignmentX(0.5f);
        mainPanel.add(soundLabel);
        mainPanel.add(Box.createVerticalStrut(5));
        JTextArea soundAttribution = new JTextArea("Sound effects by Universfield\nUkrainian project creating background music\nand sound effects for videos, games, films,\npodcasts, and other creative projects.\n\nSupport: patreon.com/11246531/join");
        soundAttribution.setEditable(false);
        soundAttribution.setFocusable(false);
        soundAttribution.setFont(new Font("Monospaced", 0, 10));
        soundAttribution.setForeground(new Color(180, 180, 180));
        soundAttribution.setBackground(new Color(40, 30, 50));
        soundAttribution.setBorder(null);
        soundAttribution.setAlignmentX(0.5f);
        mainPanel.add(soundAttribution);
        mainPanel.add(Box.createVerticalStrut(20));
        PixelButton closeButton = new PixelButton("\u2713 CLOSE");
        closeButton.setAlignmentX(0.5f);
        closeButton.addActionListener(e -> this.dispose());
        mainPanel.add(closeButton);
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setHorizontalScrollBarPolicy(31);
        this.add(scrollPane);
    }

    private void addInfoLabel(JPanel panel, String text, int fontSize, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Monospaced", bold ? 1 : 0, fontSize));
        label.setForeground(Color.WHITE);
        label.setAlignmentX(0.5f);
        panel.add(label);
        panel.add(Box.createVerticalStrut(5));
    }
}

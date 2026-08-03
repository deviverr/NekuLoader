/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.ui;

import com.musicdownloader.downloader.DeviceTransferManager;
import com.musicdownloader.downloader.DownloadEstimator;
import com.musicdownloader.downloader.DownloadManager;
import com.musicdownloader.downloader.ProviderCapabilities;
import com.musicdownloader.ui.AboutDialog;
import com.musicdownloader.ui.AnimatedCat;
import com.musicdownloader.ui.CustomThemeDialog;
import com.musicdownloader.ui.PixelButton;
import com.musicdownloader.ui.SettingsDialog;
import com.musicdownloader.utils.LanguageManager;
import com.musicdownloader.utils.DownloadHistoryStore;
import com.musicdownloader.utils.Settings;
import com.musicdownloader.utils.SoundManager;
import com.musicdownloader.utils.ThemeManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;

public class MainFrame
extends JFrame {
    private JTextField urlField;
    private JTextArea logArea;
    private JLabel statusLabel;
    private JLabel etaLabel;
    private JButton downloadButton;
    private JButton stopButton;
    private JButton deviceTransferButton;
    private JCheckBox trackNumbersCheckBox;
    private JComboBox<String> formatComboBox;
    private JComboBox<String> parseModeComboBox;
    private DownloadManager downloadManager;
    private AnimatedCat animatedCat;
    private String outputFolder;
    private JComboBox<String> sourceComboBox;
    private JComboBox<String> typeComboBox;
    private volatile boolean isDownloading = false;
    private long downloadStartTime;
    private Settings settings;
    private ThemeManager themeManager;
    private LanguageManager languageManager;
    private SoundManager soundManager;
    private JPanel mainPanel;
    private Timer starAnimationTimer;
    private Timer etaUpdateTimer;
    private int starAnimationOffset = 0;
    private float[] snowX = new float[50];
    private float[] snowY = new float[50];
    private float[] snowSpeed = new float[50];
    private String tempDeviceDownloadPath = null;
    private String tempDeviceName = null;
    private String tempDeviceId = null;
    private HashMap<String, Long> deviceDownloadSnapshot = new HashMap<String, Long>();
    private JLabel deviceLabel;
    private JScrollPane logScrollPane;
    private JPanel centerPanel;
    private boolean isLogVisible = true;
    private JProgressBar progressBar;
    private List<String> downloadHistory;
    private final DownloadHistoryStore downloadHistoryStore = new DownloadHistoryStore();
    private boolean isShowingHistory = false;
    private String originalLogContent = "";
    private JButton historyButton;
    private JButton outputButton;
    private JButton toggleLogButton;
    private JButton queueButton;
    private JLabel queueCountLabel;
    private static final int MIN_WIDTH = 760;
    private static final int MIN_HEIGHT = 640;
    private static final int LOG_HIDE_THRESHOLD = 700;
    private boolean isAdjustingSize = false;
    private boolean logHiddenDueToResize = false;
    private int lastWindowHeight = 760;
    private final List<QueuedDownload> downloadQueue = new ArrayList<QueuedDownload>();
    private boolean activeDownloadFromQueue = false;

    public MainFrame() {
        this.settings = new Settings();
        this.themeManager = new ThemeManager(this.settings.getTheme(), this.settings.getOutputColor(), this.settings);
        this.themeManager.loadBackgroundImage();
        this.languageManager = new LanguageManager(this.settings.getLanguage());
        this.soundManager = new SoundManager(this.settings);
        this.downloadHistory = new ArrayList<String>(this.downloadHistoryStore.load());
        Random rand = new Random();
        for (int i = 0; i < 50; ++i) {
            this.snowX[i] = rand.nextInt(960);
            this.snowY[i] = rand.nextInt(760) - 200;
            this.snowSpeed[i] = 0.5f + rand.nextFloat() * 1.5f;
        }
        this.setTitle(this.languageManager.get("title"));
        this.setDefaultCloseOperation(3);
        this.setSize(960, 760);
        this.setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        this.setLocationRelativeTo(null);
        this.outputFolder = this.settings.getOutputFolder();
        this.downloadManager = new DownloadManager(this.outputFolder, this.settings);
        PixelButton.setModernStyle(this.settings.isModernAppearance());
        this.initComponents();
        this.applyPixelArtStyle();
        this.addComponentListener(new ComponentAdapter(){

            @Override
            public void componentResized(ComponentEvent evt) {
                if (MainFrame.this.isAdjustingSize) {
                    return;
                }
                int currentHeight = MainFrame.this.getHeight();
                if (currentHeight < MainFrame.this.lastWindowHeight) {
                    if (MainFrame.this.isLogVisible && currentHeight < LOG_HIDE_THRESHOLD) {
                        MainFrame.this.isLogVisible = false;
                        MainFrame.this.logHiddenDueToResize = true;
                        MainFrame.this.centerPanel.setVisible(false);
                        if (MainFrame.this.toggleLogButton != null) {
                            MainFrame.this.toggleLogButton.setText("Show Log");
                        }
                        MainFrame.this.mainPanel.revalidate();
                        MainFrame.this.mainPanel.repaint();
                    }
                } else if (currentHeight > MainFrame.this.lastWindowHeight && !MainFrame.this.isLogVisible && MainFrame.this.logHiddenDueToResize && currentHeight >= LOG_HIDE_THRESHOLD) {
                    MainFrame.this.isLogVisible = true;
                    MainFrame.this.logHiddenDueToResize = false;
                    MainFrame.this.centerPanel.setVisible(true);
                    if (MainFrame.this.toggleLogButton != null) {
                        MainFrame.this.toggleLogButton.setText("Hide Log");
                    }
                    MainFrame.this.mainPanel.revalidate();
                    MainFrame.this.mainPanel.repaint();
                }
                MainFrame.this.lastWindowHeight = currentHeight;
            }
        });
        this.addWindowListener(new WindowAdapter(){

            @Override
            public void windowClosing(WindowEvent e) {
                MainFrame.this.cleanup();
            }
        });
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(MIN_WIDTH, MIN_HEIGHT);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        if (this.isAdjustingSize) {
            super.setBounds(x, y, width, height);
            return;
        }
        this.isAdjustingSize = true;
        super.setBounds(x, y, Math.max(width, MIN_WIDTH), Math.max(height, MIN_HEIGHT));
        this.isAdjustingSize = false;
    }

    @Override
    public void setBounds(Rectangle r) {
        this.setBounds(r.x, r.y, r.width, r.height);
    }

    @Override
    public void setSize(int width, int height) {
        if (this.isAdjustingSize) {
            super.setSize(width, height);
            return;
        }
        this.isAdjustingSize = true;
        super.setSize(Math.max(width, MIN_WIDTH), Math.max(height, MIN_HEIGHT));
        this.isAdjustingSize = false;
    }

    @Override
    public void setSize(Dimension d) {
        this.setSize(d.width, d.height);
    }

    private void updateToggleLogButtonText() {
        if (this.toggleLogButton != null) {
            this.toggleLogButton.setText(this.isLogVisible ? "Hide Log" : "Show Log");
        }
    }

    private void cleanup() {
        if (this.starAnimationTimer != null && this.starAnimationTimer.isRunning()) {
            this.starAnimationTimer.stop();
        }
        if (this.isDownloading) {
            this.downloadManager.stopDownload();
        }
        if (this.animatedCat != null) {
            this.animatedCat.setWalking(false);
            this.animatedCat.cleanup();
        }
    }

    private void initComponents() {
        this.createMenuBar();
        this.mainPanel = new JPanel(){

            @Override
            protected void paintComponent(Graphics g) {
                block9: {
                    super.paintComponent(g);
                    Graphics2D g2d = (Graphics2D)g;
                    if (MainFrame.this.themeManager.hasBackgroundImage()) {
                        if (MainFrame.this.themeManager.getBackgroundGif() != null) {
                            ImageIcon gif = MainFrame.this.themeManager.getBackgroundGif();
                            g2d.drawImage(gif.getImage(), 0, 0, this.getWidth(), this.getHeight(), this);
                        } else if (MainFrame.this.themeManager.getBackgroundImage() != null) {
                            g2d.drawImage(MainFrame.this.themeManager.getBackgroundImage(), 0, 0, this.getWidth(), this.getHeight(), this);
                        }
                    } else {
                        GradientPaint gradient = MainFrame.this.themeManager.getBackgroundGradient(this.getWidth(), this.getHeight());
                        g2d.setPaint(gradient);
                        g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
                    }
                    if (MainFrame.this.themeManager.hasBackgroundImage() && !MainFrame.this.settings.isAnimateBackground()) break block9;
                    if (MainFrame.this.settings.isAnimateBackground()) {
                        int boldness = MainFrame.this.settings.getSnowBoldness();
                        int transparency = MainFrame.this.settings.getSnowTransparency();
                        Color starColor = MainFrame.this.themeManager.getStarColor();
                        int alpha = (int)(255.0 * ((double)transparency / 100.0));
                        g2d.setColor(new Color(starColor.getRed(), starColor.getGreen(), starColor.getBlue(), alpha));
                        for (int i = 0; i < 50; ++i) {
                            g2d.fillRect((int)MainFrame.this.snowX[i], (int)MainFrame.this.snowY[i], boldness, boldness);
                        }
                    } else {
                        g2d.setColor(MainFrame.this.themeManager.getStarColor());
                        for (int i = 0; i < 50; ++i) {
                            int x = i * 123 % this.getWidth();
                            int y = i * 456 % (this.getHeight() - 150);
                            g2d.fillRect(x, y, 2, 2);
                        }
                    }
                }
            }
        };
        this.mainPanel.setLayout(new BorderLayout(10, 10));
        this.mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        if (this.settings.isAnimateBackground()) {
            this.starAnimationTimer = new Timer(30, e -> {
                float speedMultiplier = (float)this.settings.getSnowSpeed() / 5.0f;
                int windowWidth = Math.max(this.getWidth(), 100);
                int windowHeight = Math.max(this.getHeight(), 100);
                for (int i = 0; i < 50; ++i) {
                    int n = i;
                    this.snowY[n] = this.snowY[n] + this.snowSpeed[i] * speedMultiplier;
                    if (this.snowY[i] > (float)(windowHeight + 10) || this.snowX[i] < -10.0f || this.snowX[i] > (float)(windowWidth + 10)) {
                        this.snowY[i] = -10.0f - (float)(Math.random() * 50.0);
                        this.snowX[i] = (float)(Math.random() * (double)windowWidth);
                    }
                    if (this.snowX[i] < 0.0f) {
                        this.snowX[i] = 0.0f;
                    }
                    if (!(this.snowX[i] > (float)windowWidth)) continue;
                    this.snowX[i] = windowWidth;
                }
                this.mainPanel.repaint();
            });
            this.starAnimationTimer.start();
        }
        JPanel topPanel = this.createTopPanel();
        this.mainPanel.add((Component)topPanel, "North");
        JPanel centerBottomContainer = new JPanel(new GridBagLayout());
        centerBottomContainer.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        this.centerPanel = this.createCenterPanel();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = 1;
        centerBottomContainer.add((Component)this.centerPanel, gbc);
        JPanel progressPanel = this.createProgressPanel();
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = 2;
        centerBottomContainer.add((Component)progressPanel, gbc);
        JPanel statusPanel = this.createStatusPanel();
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = 2;
        centerBottomContainer.add((Component)statusPanel, gbc);
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 1.0;
        gbc.weighty = 0.1;
        gbc.fill = 1;
        centerBottomContainer.add((Component)spacer, gbc);
        this.animatedCat = new AnimatedCat();
        this.animatedCat.setMinimumSize(new Dimension(100, 80));
        this.animatedCat.setPreferredSize(new Dimension(800, 80));
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        gbc.fill = 1;
        gbc.ipady = 0;
        centerBottomContainer.add((Component)this.animatedCat, gbc);
        this.mainPanel.add((Component)centerBottomContainer, "Center");
        this.add(this.mainPanel);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(){

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D)g;
                int transparencyPercent = MainFrame.this.settings.getFrameTransparency();
                int alpha = (int)((double)(255 * (100 - transparencyPercent)) / 100.0);
                Color bgColor = MainFrame.this.themeManager.getBgStart();
                Color transparentBg = new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), alpha);
                g2d.setColor(transparentBg);
                g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
            }
        };
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, 1));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        JLabel titleLabel = new JLabel(this.languageManager.get("title"));
        titleLabel.setFont(new Font("Monospaced", 1, 24));
        titleLabel.setForeground(this.themeManager.getTitleColor());
        titleLabel.setAlignmentX(0.5f);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(20));
        JPanel sourcePanel = new JPanel(new FlowLayout(0));
        sourcePanel.setOpaque(false);
        JLabel sourceLabel = new JLabel(this.languageManager.get("source"));
        sourceLabel.setForeground(this.themeManager.getTopPanelTextColor());
        sourceLabel.setFont(new Font("Monospaced", 0, 14));
        this.sourceComboBox = new JComboBox<String>(new String[]{"Spotify", "YouTube Music", "YouTube", "SoundCloud", "Deezer", "Apple Music"});
        this.sourceComboBox.setFont(new Font("Monospaced", 0, 12));
        sourcePanel.add(sourceLabel);
        sourcePanel.add(this.sourceComboBox);
        JLabel typeLabel = new JLabel(this.cat(this.languageManager.get("type")));
        typeLabel.setForeground(this.themeManager.getTopPanelTextColor());
        typeLabel.setFont(new Font("Monospaced", 0, 14));
        this.typeComboBox = new JComboBox<String>(new String[]{this.cat(this.languageManager.get("single_track")), this.cat(this.languageManager.get("album")), this.cat(this.languageManager.get("playlist"))});
        this.typeComboBox.setFont(new Font("Monospaced", 0, 12));
        sourcePanel.add(Box.createHorizontalStrut(20));
        sourcePanel.add(typeLabel);
        sourcePanel.add(this.typeComboBox);
        JLabel parseModeLabel = new JLabel(this.cat("Parse:"));
        parseModeLabel.setForeground(this.themeManager.getTopPanelTextColor());
        parseModeLabel.setFont(new Font("Monospaced", 0, 14));
        this.parseModeComboBox = new JComboBox<String>(new String[]{this.cat("Automatic Match"), this.cat("Spotify Metadata Match"), this.cat("YouTube Music Search")});
        this.parseModeComboBox.setFont(new Font("Monospaced", 0, 11));
        this.parseModeComboBox.setSelectedIndex(this.settings.getParseMode().equals("spotify") ? 1 : (this.settings.getParseMode().equals("youtube-music") ? 2 : 0));
        this.parseModeComboBox.setVisible(false);
        this.parseModeComboBox.addActionListener(e -> {
            int selected = this.parseModeComboBox.getSelectedIndex();
            String mode = selected == 1 ? "spotify" : (selected == 2 ? "youtube-music" : "dual");
            this.settings.setParseMode(mode);
        });
        sourcePanel.add(Box.createHorizontalStrut(10));
        sourcePanel.add(parseModeLabel);
        sourcePanel.add(this.parseModeComboBox);
        this.sourceComboBox.addActionListener(e -> {
            String source = (String)this.sourceComboBox.getSelectedItem();
            boolean showParseMode = source.equals("Apple Music") || source.equals("Deezer");
            parseModeLabel.setVisible(showParseMode);
            this.parseModeComboBox.setVisible(showParseMode);
            if (source.equals("Apple Music") && this.typeComboBox.getItemCount() == 3) {
                this.typeComboBox.removeItemAt(2);
            } else if (!source.equals("Apple Music") && this.typeComboBox.getItemCount() == 2) {
                this.typeComboBox.addItem(this.cat(this.languageManager.get("playlist")));
            }
            panel.revalidate();
            panel.repaint();
        });
        panel.add(sourcePanel);
        panel.add(Box.createVerticalStrut(10));
        JPanel urlPanel = new JPanel(new BorderLayout(5, 5));
        urlPanel.setOpaque(false);
        JLabel urlLabel = new JLabel(this.cat(this.languageManager.get("url")));
        urlLabel.setForeground(this.themeManager.getTopPanelTextColor());
        urlLabel.setFont(new Font("Monospaced", 0, 14));
        this.urlField = new JTextField();
        this.urlField.setFont(new Font("Monospaced", 0, 12));
        this.urlField.setBackground(this.themeManager.getLogBgColor());
        this.urlField.setForeground(this.themeManager.getTextColor());
        this.urlField.setCaretColor(this.themeManager.getTextColor());
        urlPanel.add((Component)urlLabel, "West");
        urlPanel.add((Component)this.urlField, "Center");
        panel.add(urlPanel);
        panel.add(Box.createVerticalStrut(10));
        JPanel formatPanel = new JPanel(new FlowLayout(0));
        formatPanel.setOpaque(false);
        JLabel formatLabel = new JLabel(this.cat(this.languageManager.get("format")));
        formatLabel.setForeground(this.themeManager.getTopPanelTextColor());
        formatLabel.setFont(new Font("Monospaced", 0, 14));
        String[] formats = new String[]{this.cat("MP3 320kbps (Compressed)"), this.cat("FLAC (Lossless)"), this.cat("OPUS (High Quality)"), this.cat("M4A/AAC (Apple)"), this.cat("WAV (Uncompressed)"), this.cat("ALAC (Apple Lossless)"), this.cat("OGG Vorbis")};
        this.formatComboBox = new JComboBox<String>(formats);
        this.formatComboBox.setFont(new Font("Monospaced", 0, 11));
        this.formatComboBox.setSelectedIndex(0);
        this.formatComboBox.addActionListener(e -> this.updateFormatInfo());
        formatPanel.add(formatLabel);
        formatPanel.add(this.formatComboBox);
        panel.add(formatPanel);
        panel.add(Box.createVerticalStrut(5));
        JPanel optionsPanel = new JPanel(new FlowLayout(0));
        optionsPanel.setOpaque(false);
        this.trackNumbersCheckBox = new JCheckBox(this.cat(this.languageManager.get("add_track_numbers")), true);
        this.trackNumbersCheckBox.setForeground(this.themeManager.getTopPanelTextColor());
        this.trackNumbersCheckBox.setFont(new Font("Monospaced", 0, 12));
        this.trackNumbersCheckBox.setOpaque(false);
        this.trackNumbersCheckBox.setFocusPainted(false);
        this.trackNumbersCheckBox.addActionListener(e -> this.downloadManager.setAddTrackNumbers(this.trackNumbersCheckBox.isSelected()));
        optionsPanel.add(this.trackNumbersCheckBox);
        panel.add(optionsPanel);
        panel.add(Box.createVerticalStrut(5));
        JPanel buttonsPanel = new JPanel(new GridLayout(2, 5, 8, 6));
        buttonsPanel.setOpaque(false);
        this.downloadButton = new PixelButton(this.cat(this.languageManager.get("download")));
        this.downloadButton.addActionListener(e -> this.startDownload());
        this.stopButton = new PixelButton(this.cat(this.languageManager.get("stop")));
        this.stopButton.setEnabled(false);
        this.stopButton.addActionListener(e -> this.stopDownload());
        this.queueButton = new PixelButton(this.cat("QUEUE +"));
        this.queueButton.addActionListener(e -> this.addToQueue());
        this.queueCountLabel = new JLabel("0 queued");
        this.queueCountLabel.setForeground(this.themeManager.getTopPanelTextColor());
        this.queueCountLabel.setFont(new Font("Monospaced", 0, 11));
        this.queueCountLabel.setToolTipText("Queued URLs will start sequentially when the current download finishes.");
        PixelButton folderButton = new PixelButton(this.cat(this.languageManager.get("folder")));
        folderButton.addActionListener(e -> this.chooseFolder());
        PixelButton openFolderButton = new PixelButton(this.cat(this.languageManager.get("open")));
        openFolderButton.addActionListener(e -> this.openOutputFolder());
        PixelButton libraryButton = new PixelButton(this.cat("LIBRARY"));
        libraryButton.setToolTipText("Browse and play downloaded music");
        libraryButton.addActionListener(e -> this.openMusicLibrary());
        PixelButton saveLogButton = new PixelButton(this.cat("\ud83d\udcbe Save Log"));
        saveLogButton.addActionListener(e -> this.saveLogToFile());
        PixelButton settingsButton = new PixelButton(this.cat(this.languageManager.get("settings")));
        settingsButton.addActionListener(e -> this.openSettings());
        buttonsPanel.add(this.downloadButton);
        buttonsPanel.add(this.stopButton);
        buttonsPanel.add(this.queueButton);
        buttonsPanel.add(this.queueCountLabel);
        buttonsPanel.add(folderButton);
        buttonsPanel.add(openFolderButton);
        buttonsPanel.add(libraryButton);
        buttonsPanel.add(saveLogButton);
        buttonsPanel.add(settingsButton);
        panel.add(buttonsPanel);
        return panel;
    }

    private JPanel createCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));
        panel.setName("centerPanel");
        JLabel logLabel = new JLabel(this.cat(this.languageManager.get("download_log")));
        logLabel.setForeground(this.themeManager.getTextColor());
        logLabel.setFont(new Font("Monospaced", 1, 14));
        panel.add((Component)logLabel, "North");
        this.logArea = new JTextArea();
        this.logArea.setEditable(false);
        this.logArea.setFont(new Font("Monospaced", 0, 11));
        this.logArea.setBackground(this.themeManager.getLogBgColor());
        this.logArea.setForeground(this.themeManager.getCustomLogTextColor());
        this.logArea.setCaretColor(this.themeManager.getTextColor());
        this.logArea.setText(this.cat("Ready to download music! \u266a") + "\n" + this.cat("Output folder: " + this.outputFolder) + "\n\n" + this.cat("Features:") + "\n" + this.cat("\u2713 Multiple format support (Lossless & Compressed)") + "\n" + this.cat("\u2713 Downloads with cover art & metadata") + "\n" + this.cat("\u2713 Creates folders for albums/playlists") + "\n" + this.cat("\u2713 Embeds artist, album, and title info") + "\n\n" + this.cat("Format Guide:") + "\n" + this.cat("\u2022 FLAC: Best quality, lossless (large files)") + "\n" + this.cat("\u2022 MP3 320kbps: Great quality, smaller files") + "\n" + this.cat("\u2022 OPUS: Modern codec, excellent quality/size ratio") + "\n" + this.cat("\u2022 WAV: Uncompressed, largest files") + "\n");
        this.logScrollPane = new JScrollPane(this.logArea);
        this.logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(100, 80, 120), 2));
        this.logScrollPane.setMinimumSize(new Dimension(100, 80));
        this.logScrollPane.setPreferredSize(new Dimension(100, 150));
        panel.add((Component)this.logScrollPane, "Center");
        return panel;
    }

    private JPanel createProgressPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        this.progressBar = new JProgressBar(0, 100);
        this.progressBar.setStringPainted(true);
        this.progressBar.setString(this.cat("Ready"));
        this.progressBar.setValue(0);
        this.progressBar.setForeground(this.themeManager.getProgressBarColor());
        this.progressBar.setBackground(new Color(50, 50, 50));
        this.progressBar.setBorder(BorderFactory.createLineBorder(new Color(100, 80, 120), 2));
        panel.add((Component)this.progressBar, "Center");
        JPanel buttonsPanel = new JPanel(new FlowLayout(2, 5, 0));
        buttonsPanel.setOpaque(false);
        this.toggleLogButton = new JButton(this.cat("Hide Log"));
        this.toggleLogButton.setFont(new Font("Monospaced", 1, 11));
        this.toggleLogButton.setFocusPainted(false);
        this.toggleLogButton.addActionListener(e -> {
            this.isLogVisible = !this.isLogVisible;
            this.centerPanel.setVisible(this.isLogVisible);
            this.toggleLogButton.setText(this.cat(this.isLogVisible ? "Hide Log" : "Show Log"));
            if (this.isLogVisible && this.logHiddenDueToResize) {
                this.logHiddenDueToResize = false;
                int preservedWidth = Math.max(this.getWidth(), 960);
                SwingUtilities.invokeLater(() -> {
                    Dimension newSize = new Dimension(preservedWidth, Math.max(720, 600));
                    this.setSize(newSize);
                });
                return;
            }
            if (!this.isLogVisible) {
                this.logHiddenDueToResize = false;
            }
            this.mainPanel.revalidate();
            this.mainPanel.repaint();
        });
        buttonsPanel.add(this.toggleLogButton);
        this.outputButton = new JButton(this.cat("Output"));
        this.outputButton.setFont(new Font("Monospaced", 1, 11));
        this.outputButton.setFocusPainted(false);
        this.outputButton.setEnabled(false);
        this.outputButton.addActionListener(e -> this.showOutput());
        buttonsPanel.add(this.outputButton);
        this.historyButton = new JButton(this.cat("History"));
        this.historyButton.setFont(new Font("Monospaced", 1, 11));
        this.historyButton.setFocusPainted(false);
        this.historyButton.addActionListener(e -> this.showHistoryInLog());
        buttonsPanel.add(this.historyButton);
        panel.add((Component)buttonsPanel, "East");
        return panel;
    }

    private JPanel createStatusPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 0, 3));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        this.statusLabel = new JLabel(this.cat(this.languageManager.get("status") + " " + this.languageManager.get("status_idle")));
        this.statusLabel.setForeground(this.themeManager.getTextColor());
        this.statusLabel.setFont(new Font("Monospaced", 1, 13));
        this.etaLabel = new JLabel(this.cat("Info: Ready"));
        this.etaLabel.setForeground(this.themeManager.getTitleColor());
        this.etaLabel.setFont(new Font("Monospaced", 0, 12));
        this.deviceLabel = new JLabel("");
        this.deviceLabel.setForeground(this.themeManager.getTitleColor());
        this.deviceLabel.setFont(new Font("Monospaced", 1, 12));
        panel.add(this.statusLabel);
        panel.add(this.etaLabel);
        panel.add(this.deviceLabel);
        return panel;
    }

    private void applyPixelArtStyle() {
        PixelButton.setModernStyle(this.settings.isModernAppearance());
        this.setCursor(this.settings.isModernAppearance() ? Cursor.getDefaultCursor() : Cursor.getPredefinedCursor(12));
    }

    private void updateFormatInfo() {
        String format = (String)this.formatComboBox.getSelectedItem();
        String info = "";
        if (format.contains("FLAC")) {
            info = "\n[Format Info] FLAC: Lossless compression, ~50% of WAV size, perfect quality";
        } else if (format.contains("MP3")) {
            info = "\n[Format Info] MP3 320kbps: High quality lossy, wide compatibility, ~10% of WAV size";
        } else if (format.contains("OPUS")) {
            info = "\n[Format Info] OPUS: Modern codec, better than MP3 at same bitrate";
        } else if (format.contains("M4A")) {
            info = "\n[Format Info] M4A/AAC: Apple's format, good quality/size ratio";
        } else if (format.contains("WAV")) {
            info = "\n[Format Info] WAV: Uncompressed, largest files, no quality loss";
        } else if (format.contains("ALAC")) {
            info = "\n[Format Info] ALAC: Apple Lossless, similar to FLAC";
        } else if (format.contains("OGG")) {
            info = "\n[Format Info] OGG Vorbis: Open-source, good quality";
        }
        if (!info.isEmpty()) {
            this.appendLog(info + "\n");
        }
    }

    private String getSelectedFormat() {
        String selection = (String)this.formatComboBox.getSelectedItem();
        if (selection.contains("FLAC")) {
            return "flac";
        }
        if (selection.contains("OPUS")) {
            return "opus";
        }
        if (selection.contains("M4A")) {
            return "m4a";
        }
        if (selection.contains("WAV")) {
            return "wav";
        }
        if (selection.contains("ALAC")) {
            return "alac";
        }
        if (selection.contains("OGG")) {
            return "vorbis";
        }
        return "mp3";
    }

    private String getQuality() {
        String format;
        switch (format = this.getSelectedFormat()) {
            case "mp3": {
                return "320";
            }
            case "opus": {
                return "256";
            }
            case "vorbis": {
                return "320";
            }
            case "m4a": {
                return "256";
            }
        }
        return "0";
    }

    private boolean isSourceUrlCompatible(String source, String url) {
        if (source == null || url == null) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            host = host.toLowerCase();
            switch (source) {
                case "Spotify":
                    return host.equals("spotify.com") || host.endsWith(".spotify.com");
                case "YouTube Music":
                    return host.equals("music.youtube.com");
                case "YouTube":
                    return host.equals("youtube.com") || host.endsWith(".youtube.com") || host.equals("youtu.be");
                case "SoundCloud":
                    return host.equals("soundcloud.com") || host.endsWith(".soundcloud.com");
                case "Deezer":
                    return host.equals("deezer.com") || host.endsWith(".deezer.com");
                case "Apple Music":
                    return host.equals("music.apple.com") || host.equals("itunes.apple.com");
                default:
                    return false;
            }
        }
        catch (URISyntaxException e) {
            return false;
        }
    }

    private void addToQueue() {
        String url = this.urlField.getText().trim();
        if (url.isEmpty() && this.loadNextQueuedDownload()) {
            url = this.urlField.getText().trim();
            this.urlField.setText("");
        } else if (!url.isEmpty()) {
            this.activeDownloadFromQueue = false;
        }
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, this.cat("Please enter a URL before adding it to the queue."), this.cat("Queue"), 0);
            return;
        }
        String source = (String)this.sourceComboBox.getSelectedItem();
        if (!this.isSourceUrlCompatible(source, url)) {
            JOptionPane.showMessageDialog(this, this.cat("This URL does not match the selected source."), this.cat("Unsupported URL"), 0);
            return;
        }
        QueuedDownload item = new QueuedDownload(url, this.sourceComboBox.getSelectedIndex(), this.typeComboBox.getSelectedIndex(), this.formatComboBox.getSelectedIndex());
        this.downloadQueue.add(item);
        this.urlField.setText("");
        this.updateQueueCount();
        this.appendLog("Queued " + source + " download (" + this.downloadQueue.size() + " waiting)\n");
    }

    private void updateQueueCount() {
        if (this.queueCountLabel != null) {
            this.queueCountLabel.setText(this.downloadQueue.size() + " queued");
        }
    }

    private boolean loadNextQueuedDownload() {
        if (this.downloadQueue.isEmpty()) {
            return false;
        }
        QueuedDownload item = this.downloadQueue.remove(0);
        this.sourceComboBox.setSelectedIndex(item.sourceIndex);
        this.typeComboBox.setSelectedIndex(item.typeIndex);
        this.formatComboBox.setSelectedIndex(item.formatIndex);
        this.urlField.setText(item.url);
        this.activeDownloadFromQueue = true;
        this.updateQueueCount();
        this.appendLog("Starting queued item (" + (this.downloadQueue.size() + 1) + " total)\n");
        return true;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void startDownload() {
        String parseMode;
        String parseModeText;
        int result;
        String downloadPath;
        boolean isDeviceDownload;
        String deviceDestination;
        String url = this.urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, this.cat("Please enter a URL!"), this.cat("Error"), 0);
            return;
        }
        String selectedSource = (String)this.sourceComboBox.getSelectedItem();
        if (!this.isSourceUrlCompatible(selectedSource, url)) {
            JOptionPane.showMessageDialog(this, this.cat("This URL does not match the selected source.\n\nSelected source: " + selectedSource + "\nPlease choose the matching provider or paste a supported URL."), this.cat("Unsupported URL"), 0);
            return;
        }
        String capabilityError = ProviderCapabilities.validationError(selectedSource, url, this.getSelectedFormat());
        if (capabilityError != null) {
            JOptionPane.showMessageDialog(this, this.cat(capabilityError), this.cat("Unsupported Combination"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        MainFrame mainFrame = this;
        synchronized (mainFrame) {
            if (this.isDownloading) {
                JOptionPane.showMessageDialog(this, this.cat("Download already in progress!"), this.cat("Error"), 0);
                return;
            }
            this.isDownloading = true;
        }
        if (this.tempDeviceDownloadPath != null && !this.tempDeviceDownloadPath.isEmpty()) {
            deviceDestination = this.tempDeviceDownloadPath;
            isDeviceDownload = true;
            downloadPath = this.outputFolder;
            this.deviceDownloadSnapshot = this.snapshotMusicFiles(new File(downloadPath));
            this.logArea.append("\n\ud83d\udcf1 Device Download Mode Active\n");
            if (this.tempDeviceName != null) {
                this.logArea.append("Target Device: " + this.tempDeviceName + "\n");
            }
            this.logArea.append("Device Path: " + deviceDestination + "\n");
            this.logArea.append("Download to PC first: " + downloadPath + "\n");
            this.logArea.append("Will transfer after download completes\n");
        } else {
            deviceDestination = null;
            isDeviceDownload = false;
            downloadPath = this.outputFolder;
        }
        this.logArea.append("\n" + this.languageManager.get("checking_write_access") + "\n");
        this.logArea.append("Testing: " + downloadPath + "\n");
        boolean canWrite = this.downloadManager.testWriteAccess(downloadPath, this::appendLog);
        if (!canWrite) {
            this.statusLabel.setText(this.languageManager.get("status") + " " + this.languageManager.get("status_error"));
            this.etaLabel.setText("Info: Ready");
            this.animatedCat.setWalking(false);
            JOptionPane.showMessageDialog(this, this.languageManager.get("write_test_failed") + "\n\nDestination: " + downloadPath, "Error", 0);
            this.finishDownload();
            return;
        }
        String source = (String)this.sourceComboBox.getSelectedItem();
        String type = (String)this.typeComboBox.getSelectedItem();
        String format = this.getSelectedFormat();
        String quality = this.getQuality();
        this.downloadStartTime = System.currentTimeMillis();
        this.downloadButton.setEnabled(false);
        this.stopButton.setEnabled(true);
        this.statusLabel.setText(this.languageManager.get("status") + " " + this.languageManager.get("status_downloading"));
        this.etaLabel.setText(this.languageManager.get("eta") + " " + this.languageManager.get("status_idle") + "...");
        this.animatedCat.setWalking(true);
        this.progressBar.setValue(0);
        this.progressBar.setString("Downloading...");
        this.logArea.append("\n=== Starting Download ===\n");
        this.logArea.append("Time: " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "\n");
        this.logArea.append("Source: " + source + "\n");
        this.logArea.append("Workflow: " + ProviderCapabilities.workflowDescription(source) + "\n");
        this.logArea.append("Type: " + type + "\n");
        this.logArea.append("Format: " + format.toUpperCase() + " (Quality: " + quality + ")\n");
        this.logArea.append("URL: " + url + "\n");
        this.logArea.append("Track Numbers: " + (this.trackNumbersCheckBox.isSelected() ? "Yes" : "No") + "\n");
        if ((source.equals("Deezer") || source.equals("Apple Music")) && (result = JOptionPane.showConfirmDialog(this, "\u26a0\ufe0f IMPORTANT NOTICE:\n\nMusic from " + source + " is NOT downloaded directly from their servers.\nInstead, we parse the metadata and search for matching tracks.\n\nCurrent Parse Mode: " + (parseModeText = (parseMode = this.settings.getParseMode()).equals("spotify") ? "Spotify" : (parseMode.equals("youtube-music") ? "YouTube Music" : "Dual Mode (Best Quality from Spotify or YouTube Music)")) + "\n\nParse Mode Options:\n\u2022 Dual Mode: Automatically chooses the best quality source for each track\n  (compares Spotify and YouTube Music, downloads from better source)\n\u2022 Spotify: Parse all tracks from Spotify\n\u2022 YouTube Music: Parse all tracks from YouTube Music\n\nNote:\n\u2022 Track quality depends on source availability\n\u2022 Some tracks might not match exactly\n\u2022 Download may take longer (searches each track)\n\u2022 Dual mode provides best quality but takes slightly longer\n\nYou can change parse mode in the dropdown next to Type selection.\n\nDo you want to continue?", "Download Method Notice - Parse Mode: " + parseModeText, 0, 1)) != 0) {
            SwingUtilities.invokeLater(() -> {
                this.statusLabel.setText(this.cat("Status: Cancelled by user"));
                this.etaLabel.setText(this.cat("Info: Cancelled"));
                this.animatedCat.setWalking(false);
                this.finishDownload();
            });
            return;
        }
        new Thread(() -> {
            this.startETATimer();
            boolean finalIsDeviceDownload = isDeviceDownload;
            String finalDeviceDestination = deviceDestination;
            String finalDeviceName = this.tempDeviceName;
            String finalDownloadPath = downloadPath;
            try {
                // Use the stable selection index because labels can be translated or decorated in cat mode.
                boolean isPlaylist = this.typeComboBox.getSelectedIndex() > 0;
                switch (source) {
                    case "Spotify": {
                        this.downloadManager.downloadFromSpotify(url, format, quality, this::appendLog);
                        break;
                    }
                    case "YouTube Music":
                    case "YouTube": {
                        this.downloadManager.downloadFromYouTube(url, isPlaylist, format, quality, this::appendLog);
                        break;
                    }
                    case "SoundCloud": {
                        this.downloadManager.downloadFromSoundCloud(url, isPlaylist, format, quality, this::appendLog);
                        break;
                    }
                    case "Deezer": {
                        this.downloadManager.downloadFromDeezer(url, isPlaylist, format, quality, this::appendLog);
                        break;
                    }
                    case "Apple Music": {
                        this.downloadManager.downloadFromAppleMusic(url, isPlaylist, format, quality, this::appendLog);
                        break;
                    }
                    default: {
                        this.downloadManager.downloadFromYouTube(url, isPlaylist, format, quality, this::appendLog);
                    }
                }
                this.stopETATimer();
                SwingUtilities.invokeLater(() -> {
                    long duration = (System.currentTimeMillis() - this.downloadStartTime) / 1000L;
                    this.statusLabel.setText("Status: Complete! \u2713 (" + this.formatDuration(duration) + ")");
                    this.etaLabel.setText("Total time: " + this.formatDuration(duration));
                    this.appendLog("\n\u2713 Download completed successfully in " + this.formatDuration(duration) + "!\n");
                    this.soundManager.playSuccessMelody();
                    this.showSystemNotification("Download Complete", "Music downloaded successfully in " + this.formatDuration(duration) + "!", TrayIcon.MessageType.INFO);
                    String historyEntry = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " - " + source + " - " + type + " - " + format.toUpperCase() + " - Duration: " + this.formatDuration(duration);
                    this.downloadHistory.add(historyEntry);
                    this.downloadHistoryStore.append(historyEntry);
                    this.progressBar.setValue(100);
                    this.progressBar.setString("Complete!");
                    if (finalIsDeviceDownload && finalDeviceDestination != null) {
                        this.appendLog("\n\ud83d\udcf1 Starting transfer to " + finalDeviceName + "...\n");
                        this.transferDownloadedFilesToDevice(finalDownloadPath, finalDeviceDestination, finalDeviceName);
                    } else {
                        this.animatedCat.setWalking(false);
                        this.finishDownload();
                    }
                });
            }
            catch (Exception e) {
                this.stopETATimer();
                SwingUtilities.invokeLater(() -> {
                    String errorMessage;
                    String string = errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    if (errorMessage.contains("stopped by user") || errorMessage.contains("process stopped")) {
                        this.statusLabel.setText(this.languageManager.get("status") + " " + this.languageManager.get("status_stopped"));
                        this.appendLog("\n\u25a0 " + this.languageManager.get("process_stopped_by_user") + "\n");
                    } else {
                        this.statusLabel.setText(this.languageManager.get("status") + " " + this.languageManager.get("status_error"));
                        this.appendLog("\n\u2717 Error: " + e.getMessage() + "\n");
                        this.soundManager.playErrorMelody();
                        this.showSystemNotification("Download Failed", "Error: " + e.getMessage(), TrayIcon.MessageType.ERROR);
                        JOptionPane.showMessageDialog(this, "Download failed: " + e.getMessage(), "Error", 0);
                    }
                    this.etaLabel.setText("Info: Error");
                    this.animatedCat.setWalking(false);
                    this.finishDownload();
                });
            }
        }, "DownloadThread").start();
    }

    private void startETATimer() {
        this.stopETATimer();
        this.etaUpdateTimer = new Timer(500, e -> {
            if (this.downloadManager != null && this.downloadManager.getEstimator() != null) {
                DownloadEstimator estimator = this.downloadManager.getEstimator();
                if (estimator.hasValidData()) {
                    int songsRemaining = estimator.getTotalItems() - estimator.getCurrentItem() + 1;
                    String infoText = String.format("Info: %s | %s | Song %d/%d (%d remaining)", estimator.getFormattedETA(), estimator.getFormattedSpeed(), estimator.getCurrentItem(), estimator.getTotalItems(), songsRemaining);
                    this.etaLabel.setText(infoText);
                    double progress = estimator.getOverallProgress();
                    if (progress > 0.0) {
                        this.statusLabel.setText(String.format("%s %s (%.1f%%)", this.languageManager.get("status"), this.languageManager.get("status_downloading"), progress));
                        this.progressBar.setValue((int)progress);
                        this.progressBar.setString(String.format("%.1f%%", progress));
                    }
                } else {
                    this.etaLabel.setText("Info: Calculating...");
                    if (this.progressBar.getValue() < 95) {
                        this.progressBar.setValue(this.progressBar.getValue() + 1);
                        this.progressBar.setString("Downloading...");
                    }
                }
            }
        });
        this.etaUpdateTimer.start();
    }

    private void stopETATimer() {
        if (this.etaUpdateTimer != null && this.etaUpdateTimer.isRunning()) {
            this.etaUpdateTimer.stop();
        }
    }

    private void stopDownload() {
        int confirm;
        if (this.isDownloading && (confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to stop the download?", "Confirm Stop", 0)) == 0) {
            this.downloadManager.stopDownload();
            this.downloadQueue.clear();
            this.activeDownloadFromQueue = false;
            this.updateQueueCount();
            this.stopETATimer();
            this.statusLabel.setText("Status: Stopping...");
            this.stopButton.setEnabled(false);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void finishDownload() {
        this.stopETATimer();
        MainFrame mainFrame = this;
        synchronized (mainFrame) {
            this.isDownloading = false;
        }
        this.downloadButton.setEnabled(true);
        this.stopButton.setEnabled(false);
        boolean shouldStartNext = this.activeDownloadFromQueue && !this.downloadQueue.isEmpty();
        this.activeDownloadFromQueue = false;
        if (shouldStartNext) {
            this.appendLog("\nMoving to the next queued download...\n");
            Timer nextDownloadTimer = new Timer(700, e -> this.startDownload());
            nextDownloadTimer.setRepeats(false);
            nextDownloadTimer.start();
        }
        Timer resetTimer = new Timer(2000, e -> {
            this.progressBar.setValue(0);
            this.progressBar.setString("Ready");
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser(this.outputFolder);
        chooser.setFileSelectionMode(1);
        if (chooser.showOpenDialog(this) == 0) {
            File selectedFolder = chooser.getSelectedFile();
            if (selectedFolder != null && selectedFolder.exists() && selectedFolder.isDirectory()) {
                this.outputFolder = selectedFolder.getAbsolutePath();
                try {
                    this.downloadManager.setOutputFolder(this.outputFolder);
                    this.settings.setOutputFolder(this.outputFolder);
                    this.settings.saveSettings();
                    this.appendLog("Output folder changed to: " + this.outputFolder + "\n");
                }
                catch (IllegalArgumentException e) {
                    JOptionPane.showMessageDialog(this, "Invalid folder: " + e.getMessage(), "Error", 0);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Selected folder is invalid or does not exist", "Error", 0);
            }
        }
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private void openOutputFolder() {
        try {
            File folder = new File(this.outputFolder);
            if (!folder.exists()) {
                int create = JOptionPane.showConfirmDialog(this, "Output folder doesn't exist. Create it?", "Create Folder", 0);
                if (create != 0) return;
                if (!folder.mkdirs()) throw new IOException("Failed to create folder");
                this.appendLog("Created output folder: " + this.outputFolder + "\n");
            }
            if (!folder.isDirectory()) {
                throw new IOException("Path is not a directory");
            }
            Desktop.getDesktop().open(folder);
            return;
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Cannot open folder: " + e.getMessage(), "Error", 0);
        }
    }

    private void exportForAppleDevice() {
        File sourceRoot = new File(this.outputFolder);
        ArrayList<String> musicPaths = new ArrayList<String>();
        this.collectMusicFiles(sourceRoot, musicPaths);
        if (musicPaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No downloaded music was found in:\n" + this.outputFolder, "Nothing to Export", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose a folder for the Apple device export");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path destinationRoot = chooser.getSelectedFile().toPath().resolve("NekuLoader Apple Export");
        new Thread(() -> {
            try {
                Files.createDirectories(destinationRoot);
                ArrayList<String> playlist = new ArrayList<String>();
                playlist.add("#EXTM3U");
                Path normalizedSource = sourceRoot.toPath().toAbsolutePath().normalize();
                for (int i = 0; i < musicPaths.size(); ++i) {
                    Path source = Path.of(musicPaths.get(i)).toAbsolutePath().normalize();
                    Path relative = normalizedSource.relativize(source);
                    Path destination = destinationRoot.resolve(relative);
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    playlist.add(relative.toString().replace(File.separatorChar, '/'));
                    this.appendLog("Apple export " + (i + 1) + "/" + musicPaths.size() + ": " + relative + "\n");
                }
                Files.write(destinationRoot.resolve("NekuLoader.m3u8"), playlist, StandardCharsets.UTF_8);
                SwingUtilities.invokeLater(() -> {
                    try {
                        Desktop.getDesktop().open(destinationRoot.toFile());
                    }
                    catch (IOException e) {
                        this.appendLog("Could not open export folder: " + e.getMessage() + "\n");
                    }
                    JOptionPane.showMessageDialog(this,
                            "Prepared " + musicPaths.size() + " track(s) for Apple device sync.\n\n"
                                    + "To add them to Music, import this folder in Music/iTunes and sync your device.\n"
                                    + "For iPhone/iPad/iPod touch File Sharing, drag the files into a compatible app in Finder or Apple Devices.\n\n"
                                    + destinationRoot,
                            "Apple Export Ready",
                            JOptionPane.INFORMATION_MESSAGE);
                });
            }
            catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Apple export failed: " + e.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE));
            }
        }, "AppleDeviceExport").start();
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600L;
        long minutes = seconds % 3600L / 60L;
        long secs = seconds % 60L;
        if (hours > 0L) {
            return String.format("%dh %dm %ds", hours, minutes, secs);
        }
        if (minutes > 0L) {
            return String.format("%dm %ds", minutes, secs);
        }
        return String.format("%ds", secs);
    }

    private String cat(String text) {
        return this.settings.isCatModeEnabled() ? text + " :3" : text;
    }

    private void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            if (this.isShowingHistory) {
                this.originalLogContent = this.originalLogContent + message;
            } else {
                this.logArea.append(message);
                this.logArea.setCaretPosition(this.logArea.getDocument().getLength());
            }
            if (message.contains("%") && message.contains("in")) {
                try {
                    String[] parts;
                    if (message.contains("ETA")) {
                        String[] parts2 = message.split("ETA");
                        if (parts2.length > 1) {
                            String eta = parts2[1].trim().split(" ")[0];
                            this.etaLabel.setText("Approximate time: ~" + eta);
                        }
                    } else if (message.contains("in ") && (parts = message.split("in ")).length > 1) {
                        String timeInfo = parts[1].trim().split(" ")[0];
                        this.etaLabel.setText("Approximate time: ~" + timeInfo);
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
    }

    private void saveLogToFile() {
        try {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Save Log Output");
            fileChooser.setSelectedFile(new File("download_log_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".txt"));
            int result = fileChooser.showSaveDialog(this);
            if (result == 0) {
                File file = fileChooser.getSelectedFile();
                Files.writeString(file.toPath(), (CharSequence)this.logArea.getText(), new OpenOption[0]);
                this.appendLog("\n\u2713 Log saved to: " + file.getAbsolutePath() + "\n");
                JOptionPane.showMessageDialog(this, "Log saved successfully!\n\n" + file.getAbsolutePath(), "Success", 1);
            }
        }
        catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving log: " + e.getMessage(), "Error", 0);
        }
    }

    private void createMenuBar() {
        String[] themes;
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBackground(this.themeManager.getMenuBarColor());
        menuBar.setBorder(BorderFactory.createEmptyBorder());
        menuBar.setOpaque(true);
        Color menuButtonTextColor = Color.WHITE;
        final Color menuOptionTextColor = Color.BLACK;
        JMenu fileMenu = new JMenu(this.languageManager.get("menu_file"));
        fileMenu.setForeground(menuButtonTextColor);
        fileMenu.setOpaque(true);
        fileMenu.setBackground(this.themeManager.getMenuBarColor());
        fileMenu.getPopupMenu().setBackground(this.themeManager.getMenuBarColor());
        JMenuItem settingsItem = new JMenuItem(this.languageManager.get("menu_settings"));
        settingsItem.setBackground(this.themeManager.getMenuBarColor());
        settingsItem.setForeground(menuOptionTextColor);
        settingsItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        settingsItem.addActionListener(e -> this.openSettings());
        JMenuItem exitItem = new JMenuItem(this.languageManager.get("menu_exit"));
        exitItem.setBackground(this.themeManager.getMenuBarColor());
        exitItem.setForeground(menuOptionTextColor);
        exitItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        exitItem.addActionListener(e -> System.exit(0));
        fileMenu.add(settingsItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);
        JMenu helpMenu = new JMenu(this.languageManager.get("menu_help"));
        helpMenu.setForeground(menuButtonTextColor);
        helpMenu.setOpaque(true);
        helpMenu.setBackground(this.themeManager.getMenuBarColor());
        helpMenu.getPopupMenu().setBackground(this.themeManager.getMenuBarColor());
        JMenuItem aboutItem = new JMenuItem(this.languageManager.get("menu_about"));
        aboutItem.setBackground(this.themeManager.getMenuBarColor());
        aboutItem.setForeground(menuOptionTextColor);
        aboutItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        aboutItem.addActionListener(e -> this.openAbout());
        helpMenu.add(aboutItem);
        JMenu libraryMenu = new JMenu("Library");
        libraryMenu.setForeground(menuButtonTextColor);
        libraryMenu.setOpaque(true);
        libraryMenu.setBackground(this.themeManager.getMenuBarColor());
        JMenuItem openLibraryItem = new JMenuItem("Open Music Library");
        openLibraryItem.setBackground(this.themeManager.getMenuBarColor());
        openLibraryItem.setForeground(menuOptionTextColor);
        openLibraryItem.addActionListener(e -> this.openMusicLibrary());
        libraryMenu.add(openLibraryItem);
        JMenu themeMenu = new JMenu("Theme");
        themeMenu.setForeground(menuButtonTextColor);
        themeMenu.setOpaque(true);
        themeMenu.setBackground(this.themeManager.getMenuBarColor());
        themeMenu.getPopupMenu().setBackground(this.themeManager.getMenuBarColor());
        JMenu themesSubmenu = new JMenu("Themes");
        themesSubmenu.setBackground(this.themeManager.getMenuBarColor());
        themesSubmenu.setForeground(menuOptionTextColor);
        themesSubmenu.getPopupMenu().setBackground(this.themeManager.getMenuBarColor());
        for (String theme : themes = new String[]{"Classic", "Dark", "Light", "System", "Cyberpunk", "Neon", "Absolute Black and White", "Absolute White and Black", "Custom"}) {
            JMenuItem themeItem = new JMenuItem(theme);
            themeItem.setBackground(this.themeManager.getMenuBarColor());
            themeItem.setForeground(menuOptionTextColor);
            themeItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
            themeItem.addActionListener(e -> this.changeTheme(theme));
            themesSubmenu.add(themeItem);
        }
        JMenuItem customThemeItem = new JMenuItem("Customize Theme");
        customThemeItem.setBackground(this.themeManager.getMenuBarColor());
        customThemeItem.setForeground(menuOptionTextColor);
        customThemeItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        customThemeItem.addActionListener(e -> this.openCustomThemeDialog());
        JMenuItem setBgImageItem = new JMenuItem("Set Background Image");
        setBgImageItem.setBackground(this.themeManager.getMenuBarColor());
        setBgImageItem.setForeground(menuOptionTextColor);
        setBgImageItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        setBgImageItem.addActionListener(e -> this.setBackgroundImage());
        JMenuItem removeBgImageItem = new JMenuItem("Remove Background Image");
        removeBgImageItem.setBackground(this.themeManager.getMenuBarColor());
        removeBgImageItem.setForeground(menuOptionTextColor);
        removeBgImageItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        removeBgImageItem.addActionListener(e -> this.removeBackgroundImage());
        final JMenu recentBgMenu = new JMenu("Recent Backgrounds");
        recentBgMenu.setBackground(this.themeManager.getMenuBarColor());
        recentBgMenu.setForeground(menuOptionTextColor);
        recentBgMenu.addMenuListener(new MenuListener(){

            @Override
            public void menuSelected(MenuEvent e) {
                MainFrame.this.populateRecentBackgroundsMenu(recentBgMenu, menuOptionTextColor);
            }

            @Override
            public void menuDeselected(MenuEvent e) {
            }

            @Override
            public void menuCanceled(MenuEvent e) {
            }
        });
        this.populateRecentBackgroundsMenu(recentBgMenu, menuOptionTextColor);
        themeMenu.add(themesSubmenu);
        themeMenu.addSeparator();
        themeMenu.add(customThemeItem);
        themeMenu.add(setBgImageItem);
        themeMenu.add(removeBgImageItem);
        themeMenu.add(recentBgMenu);
        JMenu devicesMenu = new JMenu("Devices");
        devicesMenu.setForeground(menuButtonTextColor);
        devicesMenu.setOpaque(true);
        devicesMenu.setBackground(this.themeManager.getMenuBarColor());
        devicesMenu.getPopupMenu().setBackground(this.themeManager.getMenuBarColor());
        JMenuItem checkConnectionItem = new JMenuItem("Check Connection");
        checkConnectionItem.setBackground(this.themeManager.getMenuBarColor());
        checkConnectionItem.setForeground(menuOptionTextColor);
        checkConnectionItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        checkConnectionItem.addActionListener(e -> this.checkDeviceConnection());
        JMenuItem iOSDevicesItem = new JMenuItem("iOS Devices");
        iOSDevicesItem.setBackground(this.themeManager.getMenuBarColor());
        iOSDevicesItem.setForeground(menuOptionTextColor);
        iOSDevicesItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        iOSDevicesItem.addActionListener(e -> this.exportForAppleDevice());
        JMenuItem androidDevicesItem = new JMenuItem("Android Devices");
        androidDevicesItem.setBackground(this.themeManager.getMenuBarColor());
        androidDevicesItem.setForeground(menuOptionTextColor);
        androidDevicesItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        androidDevicesItem.addActionListener(e -> this.openDeviceTransfer());
        JMenuItem downloadToDeviceItem = new JMenuItem("Download to Device");
        downloadToDeviceItem.setBackground(this.themeManager.getMenuBarColor());
        downloadToDeviceItem.setForeground(menuOptionTextColor);
        downloadToDeviceItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        downloadToDeviceItem.addActionListener(e -> this.downloadToDevice());
        JMenuItem transferMusicItem = new JMenuItem("Transfer Music to Device");
        transferMusicItem.setBackground(this.themeManager.getMenuBarColor());
        transferMusicItem.setForeground(menuOptionTextColor);
        transferMusicItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        transferMusicItem.addActionListener(e -> this.openDeviceTransfer());
        JMenuItem appleExportItem = new JMenuItem("Export for iPhone / iPod");
        appleExportItem.setBackground(this.themeManager.getMenuBarColor());
        appleExportItem.setForeground(menuOptionTextColor);
        appleExportItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 80));
        appleExportItem.addActionListener(e -> this.exportForAppleDevice());
        devicesMenu.add(checkConnectionItem);
        devicesMenu.addSeparator();
        devicesMenu.add(iOSDevicesItem);
        devicesMenu.add(androidDevicesItem);
        devicesMenu.addSeparator();
        devicesMenu.add(downloadToDeviceItem);
        devicesMenu.add(transferMusicItem);
        devicesMenu.add(appleExportItem);
        menuBar.add(fileMenu);
        menuBar.add(helpMenu);
        menuBar.add(themeMenu);
        menuBar.add(libraryMenu);
        menuBar.add(devicesMenu);
        JPanel fillerPanel = new JPanel();
        fillerPanel.setBackground(this.themeManager.getMenuBarColor());
        fillerPanel.setOpaque(true);
        menuBar.add(Box.createHorizontalGlue());
        this.setJMenuBar(menuBar);
    }

    private void openMusicLibrary() {
        MusicLibraryDialog dialog = new MusicLibraryDialog(this, Path.of(this.outputFolder));
        dialog.setVisible(true);
    }

    private void openSettings() {
        String newOutputFolder;
        SettingsDialog dialog = new SettingsDialog(this, this.settings, this.languageManager);
        dialog.setVisible(true);
        if (dialog.isSettingsChanged() && (newOutputFolder = this.settings.getOutputFolder()) != null && !newOutputFolder.trim().isEmpty()) {
            this.outputFolder = newOutputFolder;
            try {
                this.downloadManager.setOutputFolder(this.outputFolder);
                this.appendLog("Settings updated. Output folder: " + this.outputFolder + "\n");
            }
            catch (IllegalArgumentException e) {
                this.appendLog("Warning: Invalid output folder in settings: " + e.getMessage() + "\n");
            }
        }
    }

    private void openDeviceTransfer() {
        DeviceTransferManager transferManager = new DeviceTransferManager();
        transferManager.setLogger(this::appendLog);
        this.appendLog("\n" + transferManager.getDeviceStatus());
        List<String> androidDevices = transferManager.detectAndroidDevices();
        List<String> iosDevices = transferManager.detectIOSDevices();
        if (androidDevices.isEmpty() && iosDevices.isEmpty()) {
            int choice = JOptionPane.showConfirmDialog(this, this.languageManager.get("no_devices_found") + "\n\n" + this.languageManager.get("android_device") + ": " + this.languageManager.get("enable_usb_debugging") + "\n" + this.languageManager.get("ios_device") + ": Use iTunes or iCloud\n\nWould you like to see installation instructions?", this.languageManager.get("device_transfer"), 0, 1);
            if (choice == 0) {
                this.showDeviceInstructions(transferManager);
            }
            return;
        }
        ArrayList<String> deviceOptions = new ArrayList<String>();
        HashMap<String, DeviceTransferManager.DeviceType> deviceTypeMap = new HashMap<String, DeviceTransferManager.DeviceType>();
        HashMap<String, String> deviceIdMap = new HashMap<String, String>();
        for (String deviceId : androidDevices) {
            String deviceName = transferManager.getAndroidDeviceName(deviceId);
            String option = "\ud83d\udcf1 Android: " + deviceName;
            deviceOptions.add(option);
            deviceTypeMap.put(option, DeviceTransferManager.DeviceType.ANDROID);
            deviceIdMap.put(option, deviceId);
        }
        for (String deviceId : iosDevices) {
            String option = "\ud83d\udcf1 iOS: " + deviceId;
            deviceOptions.add(option);
            deviceTypeMap.put(option, DeviceTransferManager.DeviceType.IOS);
            deviceIdMap.put(option, deviceId);
        }
        String selectedDevice = (String)JOptionPane.showInputDialog(this, this.languageManager.get("select_device") + ":", this.languageManager.get("device_transfer"), 3, null, deviceOptions.toArray(), deviceOptions.get(0));
        if (selectedDevice == null) {
            return;
        }
        DeviceTransferManager.DeviceType deviceType = deviceTypeMap.get(selectedDevice);
        String deviceId = deviceIdMap.get(selectedDevice);
        if (deviceType == DeviceTransferManager.DeviceType.IOS) {
            int choice = JOptionPane.showOptionDialog(this,
                    transferManager.getIOSTransferInstructions(),
                    this.languageManager.get("ios_device"),
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.INFORMATION_MESSAGE,
                    null,
                    new String[]{"Prepare Export", "Close"},
                    "Prepare Export");
            if (choice == 0) {
                this.exportForAppleDevice();
            }
            return;
        }
        JFileChooser sourceChooser = new JFileChooser(this.outputFolder);
        sourceChooser.setDialogTitle("Select source folder with music");
        sourceChooser.setFileSelectionMode(2);
        sourceChooser.setMultiSelectionEnabled(true);
        if (sourceChooser.showOpenDialog(this) != 0) {
            return;
        }
        File[] selectedFiles = sourceChooser.getSelectedFiles();
        ArrayList<String> filesToTransfer = new ArrayList<String>();
        for (File file : selectedFiles) {
            if (file.isDirectory()) {
                this.collectMusicFiles(file, filesToTransfer);
                continue;
            }
            if (!this.isMusicFile(file)) continue;
            filesToTransfer.add(file.getAbsolutePath());
        }
        if (filesToTransfer.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No music files found in selection!", "Error", 0);
            return;
        }
        String defaultPath = "/sdcard/Music/";
        String devicePath = JOptionPane.showInputDialog(this, this.languageManager.get("device_destination") + "\nFiles to transfer: " + filesToTransfer.size() + "\n\nEnter destination path:", defaultPath);
        if (devicePath == null || devicePath.trim().isEmpty()) {
            return;
        }
        String finalDevicePath = devicePath.trim();
        int confirm = JOptionPane.showConfirmDialog(this, "Transfer " + filesToTransfer.size() + " file(s) to:\n" + selectedDevice + "\nDestination: " + finalDevicePath + "\n\nContinue?", "Confirm Transfer", 0, 3);
        if (confirm != 0) {
            return;
        }
        new Thread(() -> {
            this.appendLog("\n" + this.languageManager.get("transferring_files") + "\n");
            this.appendLog("Device: " + selectedDevice + "\n");
            this.appendLog("Destination: " + finalDevicePath + "\n");
            this.appendLog("Files: " + filesToTransfer.size() + "\n\n");
            int success = transferManager.transferMultipleFiles(filesToTransfer, finalDevicePath, deviceType, deviceId);
            SwingUtilities.invokeLater(() -> {
                if (success == filesToTransfer.size()) {
                    JOptionPane.showMessageDialog(this, this.languageManager.get("transfer_complete") + "\nTransferred: " + success + " files", this.languageManager.get("device_transfer"), 1);
                } else {
                    JOptionPane.showMessageDialog(this, this.languageManager.get("transfer_failed") + "\nSuccessfully transferred: " + success + "/" + filesToTransfer.size(), this.languageManager.get("device_transfer"), 2);
                }
            });
        }, "DeviceTransferThread").start();
    }

    private void collectMusicFiles(File dir, List<String> fileList) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    this.collectMusicFiles(file, fileList);
                    continue;
                }
                if (!this.isMusicFile(file)) continue;
                fileList.add(file.getAbsolutePath());
            }
        }
    }

    private boolean isMusicFile(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".alac") || name.endsWith(".opus") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".vorbis");
    }

    private HashMap<String, Long> snapshotMusicFiles(File root) {
        ArrayList<String> paths = new ArrayList<String>();
        if (root.isDirectory()) {
            this.collectMusicFiles(root, paths);
        }
        HashMap<String, Long> snapshot = new HashMap<String, Long>();
        for (String path : paths) {
            File file = new File(path);
            snapshot.put(file.getAbsolutePath(), file.lastModified());
        }
        return snapshot;
    }

    private void showDeviceInstructions(DeviceTransferManager transferManager) {
        Object[] options = new String[]{"Android (ADB)", "iOS", "Close"};
        int choice = JOptionPane.showOptionDialog(this, "Select platform for installation instructions:", this.languageManager.get("install_adb"), -1, 3, null, options, options[0]);
        if (choice == 0) {
            JTextArea textArea = new JTextArea(transferManager.getADBInstallInstructions());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", 0, 12));
            textArea.setCaretPosition(0);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(this, scrollPane, "Android ADB Installation", 1);
        } else if (choice == 1) {
            JTextArea textArea = new JTextArea(transferManager.getIOSTransferInstructions());
            textArea.setEditable(false);
            textArea.setFont(new Font("Monospaced", 0, 12));
            textArea.setCaretPosition(0);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(600, 400));
            JOptionPane.showMessageDialog(this, scrollPane, "iOS Transfer Options", 1);
        }
    }

    private void openAbout() {
        AboutDialog dialog = new AboutDialog(this);
        dialog.setVisible(true);
    }

    private void showHistoryInLog() {
        if (!this.isShowingHistory) {
            this.originalLogContent = this.logArea.getText();
            this.isShowingHistory = true;
            StringBuilder sb = new StringBuilder();
            sb.append("\u2550".repeat(60)).append("\n");
            sb.append("                    DOWNLOAD HISTORY\n");
            sb.append("\u2550".repeat(60)).append("\n\n");
            if (this.downloadHistory.isEmpty()) {
                sb.append("No downloads yet.\n\n");
                sb.append("Your download history will appear here once you start\n");
                sb.append("downloading music.\n\n");
                sb.append("Click 'Output' button to return to the log view.");
            } else {
                sb.append("Total Downloads: ").append(this.downloadHistory.size()).append("\n");
                sb.append("\u2500".repeat(60)).append("\n\n");
                for (int i = this.downloadHistory.size() - 1; i >= 0; --i) {
                    sb.append(this.downloadHistory.size() - i).append(". ");
                    sb.append(this.downloadHistory.get(i)).append("\n\n");
                }
                sb.append("\u2500".repeat(60)).append("\n");
                sb.append("Click 'Output' button to return to the log view.\n");
            }
            this.logArea.setText(sb.toString());
            this.logArea.setCaretPosition(0);
            this.historyButton.setEnabled(false);
            this.outputButton.setEnabled(true);
        }
    }

    private void showOutput() {
        if (this.isShowingHistory) {
            this.logArea.setText(this.originalLogContent);
            this.logArea.setCaretPosition(this.logArea.getText().length());
            this.isShowingHistory = false;
            this.historyButton.setEnabled(true);
            this.outputButton.setEnabled(false);
        }
    }

    private void showDownloadHistory() {
        JDialog historyDialog = new JDialog(this, "Download History", true);
        historyDialog.setSize(600, 400);
        historyDialog.setLocationRelativeTo(this);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(this.themeManager.getBgStart());
        JLabel titleLabel = new JLabel("Download History");
        titleLabel.setFont(new Font("Monospaced", 1, 16));
        titleLabel.setForeground(this.themeManager.getTitleColor());
        panel.add((Component)titleLabel, "North");
        JTextArea historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Monospaced", 0, 12));
        historyArea.setBackground(this.themeManager.getLogBgColor());
        historyArea.setForeground(this.themeManager.getCustomLogTextColor());
        if (this.downloadHistory.isEmpty()) {
            historyArea.setText("No downloads yet.\n\nYour download history will appear here once you start downloading music.");
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("Total Downloads: ").append(this.downloadHistory.size()).append("\n");
            sb.append("\u2550".repeat(50)).append("\n\n");
            for (int i = this.downloadHistory.size() - 1; i >= 0; --i) {
                sb.append(this.downloadHistory.size() - i).append(". ");
                sb.append(this.downloadHistory.get(i)).append("\n\n");
            }
            historyArea.setText(sb.toString());
        }
        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(this.themeManager.getTitleColor(), 2));
        panel.add((Component)scrollPane, "Center");
        JPanel buttonPanel = new JPanel(new FlowLayout(2, 10, 0));
        buttonPanel.setOpaque(false);
        JButton clearButton = new JButton("Clear History");
        clearButton.setFont(new Font("Monospaced", 1, 12));
        clearButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(historyDialog, "Are you sure you want to clear all download history?", "Clear History", 0);
            if (result == 0) {
                this.downloadHistory.clear();
                this.downloadHistoryStore.clear();
                historyArea.setText("No downloads yet.\n\nYour download history will appear here once you start downloading music.");
            }
        });
        buttonPanel.add(clearButton);
        JButton closeButton = new JButton("Close");
        closeButton.setFont(new Font("Monospaced", 1, 12));
        closeButton.addActionListener(e -> historyDialog.dispose());
        buttonPanel.add(closeButton);
        panel.add((Component)buttonPanel, "South");
        historyDialog.add(panel);
        historyDialog.setVisible(true);
    }

    private void changeTheme(String themeName) {
        this.settings.setTheme(themeName);
        this.themeManager = new ThemeManager(themeName, this.settings.getOutputColor(), this.settings);
        this.getContentPane().removeAll();
        this.mainPanel = null;
        this.initComponents();
        this.applyPixelArtStyle();
        this.revalidate();
        this.repaint();
        this.appendLog("Theme changed to: " + themeName + "\n");
    }

    private void openThemeDialog() {
        CustomThemeDialog dialog = new CustomThemeDialog(this, this.settings, this.languageManager);
        dialog.setVisible(true);
        if (dialog.isAccepted()) {
            this.themeManager = new ThemeManager(this.settings.getTheme(), this.settings.getOutputColor(), this.settings);
            this.getContentPane().removeAll();
            this.mainPanel = null;
            this.initComponents();
            this.applyPixelArtStyle();
            this.revalidate();
            this.repaint();
            this.appendLog("Theme updated successfully!\n");
        }
    }

    private void openCustomThemeDialog() {
        this.openThemeDialog();
    }

    private void setBackgroundImage() {
        int result;
        File currentFile;
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Background Image");
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
        String currentPath = this.settings.getBackgroundImagePath();
        if (!currentPath.isEmpty() && (currentFile = new File(currentPath)).exists()) {
            fileChooser.setCurrentDirectory(currentFile.getParentFile());
        }
        if ((result = fileChooser.showOpenDialog(this)) == 0) {
            File selectedFile = fileChooser.getSelectedFile();
            this.settings.setBackgroundImagePath(selectedFile.getAbsolutePath());
            this.settings.saveSettings();
            this.themeManager.loadBackgroundImage();
            this.mainPanel.repaint();
            this.soundManager.playUISuccessSound();
            JOptionPane.showMessageDialog(this, this.cat("Background image set successfully!"), this.cat("Success"), 1);
        }
    }

    private void removeBackgroundImage() {
        if (!this.settings.hasBackgroundImage()) {
            JOptionPane.showMessageDialog(this, this.cat("No background image is currently set."), this.cat("Info"), 1);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, this.cat("Remove current background image?"), this.cat("Confirm Remove"), 0, 3);
        if (confirm == 0) {
            this.settings.removeBackgroundImage();
            this.themeManager.loadBackgroundImage();
            this.mainPanel.repaint();
            this.soundManager.playUISuccessSound();
            JOptionPane.showMessageDialog(this, this.cat("Background image removed successfully!"), this.cat("Success"), 1);
        }
    }

    private void populateRecentBackgroundsMenu(JMenu menu, Color textColor) {
        menu.removeAll();
        List<String> recentImages = this.settings.getRecentBackgroundImages();
        if (recentImages.isEmpty()) {
            JMenuItem noRecentItem = new JMenuItem("No recent backgrounds");
            noRecentItem.setEnabled(false);
            noRecentItem.setBackground(this.themeManager.getMenuBarColor());
            noRecentItem.setForeground(textColor);
            menu.add(noRecentItem);
        } else {
            for (String imagePath : recentImages) {
                File imageFile = new File(imagePath);
                Object displayName = imageFile.getName();
                if (((String)displayName).length() > 40) {
                    displayName = ((String)displayName).substring(0, 37) + "...";
                }
                JMenuItem recentItem = new JMenuItem((String)displayName);
                recentItem.setBackground(this.themeManager.getMenuBarColor());
                recentItem.setForeground(textColor);
                recentItem.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 10));
                recentItem.setToolTipText(imagePath);
                recentItem.addActionListener(e -> {
                    this.settings.setBackgroundImagePath(imagePath);
                    this.settings.saveSettings();
                    this.themeManager.loadBackgroundImage();
                    this.mainPanel.repaint();
                    this.soundManager.playUISuccessSound();
                    JOptionPane.showMessageDialog(this, this.cat("Background image set to: " + imageFile.getName()), this.cat("Success"), 1);
                });
                menu.add(recentItem);
            }
        }
    }

    private void showSystemNotification(String title, String message, TrayIcon.MessageType messageType) {
        if (!SystemTray.isSupported()) {
            System.out.println("System tray not supported");
            return;
        }
        try {
            TrayIcon[] icons;
            SystemTray tray = SystemTray.getSystemTray();
            if (tray.getTrayIcons().length == 0) {
                Image image = Toolkit.getDefaultToolkit().createImage(this.getClass().getResource("/images/icon.png"));
                if (image == null) {
                    image = new BufferedImage(16, 16, 2);
                }
                TrayIcon trayIcon = new TrayIcon(image, "Pixel Music Downloader");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            }
            if ((icons = tray.getTrayIcons()).length > 0) {
                icons[0].displayMessage(title, message, messageType);
            }
        }
        catch (Exception e) {
            System.err.println("Failed to show system notification: " + e.getMessage());
        }
    }

    private boolean verifyDownloadedFiles(String downloadPath, int expectedCount, String source, boolean isRetry) {
        try {
            this.appendLog("\n=== Verifying Downloaded Files ===\n");
            File downloadDir = new File(downloadPath);
            if (!downloadDir.exists() || !downloadDir.isDirectory()) {
                this.appendLog("\u2717 Download directory not found: " + downloadPath + "\n");
                return false;
            }
            File[] files = downloadDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp3") || lower.endsWith(".flac") || lower.endsWith(".m4a") || lower.endsWith(".wav") || lower.endsWith(".ogg") || lower.endsWith(".opus");
            });
            int actualCount = files != null ? files.length : 0;
            this.appendLog("Expected files: " + expectedCount + "\n");
            this.appendLog("Found files: " + actualCount + "\n");
            if (actualCount >= expectedCount) {
                this.appendLog("\u2713 All files downloaded successfully!\n");
                return true;
            }
            int missingCount = expectedCount - actualCount;
            this.appendLog("\u26a0\ufe0f Missing " + missingCount + " file(s)\n");
            if (isRetry) {
                this.soundManager.playErrorMelody();
                this.showSystemNotification("Download Failed", "Critical Error: " + missingCount + " files missing after retry!", TrayIcon.MessageType.ERROR);
                JOptionPane.showMessageDialog(this, "CRITICAL ERROR\n\nDownload verification failed twice!\n\nMissing files: " + missingCount + "\nExpected: " + expectedCount + "\nDownloaded: " + actualCount + "\n\nPlease check:\n\u2022 Internet connection\n\u2022 Source availability (" + source + ")\n\u2022 Disk space\n\u2022 yt-dlp installation", "Critical Download Error", 0);
                return false;
            }
            this.soundManager.playErrorMelody();
            int choice = JOptionPane.showConfirmDialog(this, "Download Verification Warning\n\nSome files were not downloaded:\n\nMissing: " + missingCount + " file(s)\nExpected: " + expectedCount + "\nDownloaded: " + actualCount + "\n\nWould you like to retry downloading the missing files?", "Incomplete Download", 0, 2);
            if (choice == 0) {
                this.appendLog("\nRetrying download for missing files...\n");
                return false;
            }
            this.appendLog("User chose not to retry. Download incomplete.\n");
            return true;
        }
        catch (Exception e) {
            this.appendLog("\u2717 Error during verification: " + e.getMessage() + "\n");
            return true;
        }
    }

    private void checkDeviceConnection() {
        this.appendLog("\n=== Device Connection Check ===\n");
        this.appendLog("Scanning for connected devices...\n\n");
        this.appendLog("--- iOS Devices ---\n");
        List<IOSDevice> iosDevices = this.detectIOSDevices();
        if (iosDevices.isEmpty()) {
            this.appendLog("\u26a0\ufe0f No iOS devices detected.\n");
        } else {
            this.appendLog("\u2713 Found " + iosDevices.size() + " iOS device(s):\n");
            for (int i = 0; i < iosDevices.size(); ++i) {
                IOSDevice device = iosDevices.get(i);
                this.appendLog("  " + (i + 1) + ". " + device.getName() + " (" + device.getType() + ")\n");
            }
        }
        this.appendLog("\n--- Android Devices ---\n");
        List<AndroidDevice> androidDevices = this.detectAndroidDevices();
        if (androidDevices.isEmpty()) {
            this.appendLog("\u26a0\ufe0f No Android devices detected.\n");
        } else {
            this.appendLog("\u2713 Found " + androidDevices.size() + " Android device(s):\n");
            for (int i = 0; i < androidDevices.size(); ++i) {
                AndroidDevice device = androidDevices.get(i);
                this.appendLog("  " + (i + 1) + ". " + device.getName() + " (" + device.getModel() + ")\n");
            }
        }
        this.appendLog("\n=== Scan Complete ===\n");
        int totalDevices = iosDevices.size() + androidDevices.size();
        if (totalDevices == 0) {
            JOptionPane.showMessageDialog(this, "No Devices Detected!\n\niOS Devices:\n\u2022 Connect via USB cable\n\u2022 Unlock device and trust this computer\n\u2022 Install iTunes or Apple Devices app (Windows)\n\nAndroid Devices:\n\u2022 Connect via USB cable\n\u2022 Enable USB Debugging in Developer Options\n\u2022 Allow file transfer (MTP mode)", "No Devices Found", 1);
        } else {
            JOptionPane.showMessageDialog(this, "Found " + totalDevices + " device(s)!\n\niOS: " + iosDevices.size() + "\nAndroid: " + androidDevices.size(), "Devices Found", 1);
        }
    }

    private void manageIOSDevices() {
        this.appendLog("\n=== iOS Device Manager ===\n");
        List<IOSDevice> devices = this.detectIOSDevices();
        if (devices.isEmpty()) {
            this.appendLog("\u26a0\ufe0f No iOS devices detected.\n");
            JOptionPane.showMessageDialog(this, "No iOS Device Detected!\n\nPlease ensure:\n\u2022 Connected via USB cable\n\u2022 Device is unlocked\n\u2022 Trusted this computer\n\u2022 iTunes or Apple Devices app installed (Windows)", "No Device Found", 2);
            return;
        }
        this.appendLog("\u2713 Found " + devices.size() + " iOS device(s):\n");
        for (int i = 0; i < devices.size(); ++i) {
            IOSDevice device = devices.get(i);
            this.appendLog(i + 1 + ". " + device.getName() + " (" + device.getType() + ")\n");
            this.appendLog("   Path: " + device.getPath() + "\n");
        }
        this.showIOSDeviceDialog(devices);
    }

    private void manageAndroidDevices() {
        this.appendLog("\n=== Android Device Manager ===\n");
        List<AndroidDevice> devices = this.detectAndroidDevices();
        if (devices.isEmpty()) {
            this.appendLog("\u26a0\ufe0f No Android devices detected.\n");
            JOptionPane.showMessageDialog(this, "No Android Device Detected!\n\nPlease ensure:\n\u2022 Connected via USB cable\n\u2022 USB Debugging enabled in Developer Options\n\u2022 File transfer (MTP) mode selected\n\u2022 USB drivers installed", "No Device Found", 2);
            return;
        }
        this.appendLog("\u2713 Found " + devices.size() + " Android device(s):\n");
        for (int i = 0; i < devices.size(); ++i) {
            AndroidDevice device = devices.get(i);
            this.appendLog(i + 1 + ". " + device.getName() + " (" + device.getModel() + ")\n");
            this.appendLog("   Path: " + device.getPath() + "\n");
        }
        this.showAndroidDeviceDialog(devices);
    }

    private void downloadToDevice() {
        DeviceTransferManager transferManager = new DeviceTransferManager();
        transferManager.setLogger(this::appendLog);
        List<String> deviceIds = transferManager.detectAndroidDevices();
        if (deviceIds.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No authorized Android device was found.\n\nConnect a device with USB debugging enabled, accept its authorization prompt, and try again.\nWireless ADB devices are supported too.",
                    "Android Device Not Found",
                    JOptionPane.WARNING_MESSAGE);
            this.showDeviceInstructions(transferManager);
            return;
        }
        ArrayList<String> deviceLabels = new ArrayList<String>();
        HashMap<String, String> idsByLabel = new HashMap<String, String>();
        for (String deviceId : deviceIds) {
            String label = transferManager.getAndroidDeviceName(deviceId) + " (" + deviceId + ")";
            deviceLabels.add(label);
            idsByLabel.put(label, deviceId);
        }
        String selected = (String)JOptionPane.showInputDialog(this,
                "Choose the Android device that should receive the next download:",
                "Download to Android",
                JOptionPane.QUESTION_MESSAGE,
                null,
                deviceLabels.toArray(new String[0]),
                deviceLabels.get(0));
        if (selected == null) {
            return;
        }
        String destination = JOptionPane.showInputDialog(this, "Android music folder:", "/sdcard/Music/NekuLoader");
        if (destination == null || destination.isBlank()) {
            return;
        }
        String deviceId = idsByLabel.get(selected);
        String normalizedDestination = destination.trim().replaceAll("/+$", "");
        if (!transferManager.ensureAndroidDirectory(normalizedDestination, deviceId)) {
            JOptionPane.showMessageDialog(this, "Could not create the destination folder on Android. Check the download log for details.", "Android Setup Failed", JOptionPane.ERROR_MESSAGE);
            return;
        }
        this.tempDeviceDownloadPath = normalizedDestination;
        this.tempDeviceName = selected;
        this.tempDeviceId = deviceId;
        this.deviceLabel.setText("Device: " + selected);
        this.deviceLabel.setForeground(new Color(100, 255, 100));
        this.appendLog("Android target ready: " + normalizedDestination + "\n");
        JOptionPane.showMessageDialog(this,
                "Android is ready.\n\nThe next completed download will be copied to:\n" + normalizedDestination + "\n\nAlbum folders will be preserved.",
                "Download to Android Ready",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void downloadToDeviceLegacy() {
        this.appendLog("\n=== Download to Device ===\n");
        List<IOSDevice> iosDevices = this.detectIOSDevices();
        List<AndroidDevice> androidDevices = this.detectAndroidDevices();
        if (iosDevices.isEmpty() && androidDevices.isEmpty()) {
            this.appendLog("\u26a0\ufe0f No devices detected.\n");
            JOptionPane.showMessageDialog(this, "No Devices Detected!\n\nPlease connect a device and try again.", "No Devices", 2);
            return;
        }
        ArrayList<String> deviceNames = new ArrayList<String>();
        ArrayList<Object> deviceObjects = new ArrayList<Object>();
        for (IOSDevice iOSDevice : iosDevices) {
            deviceNames.add("iOS: " + iOSDevice.getName() + " (" + iOSDevice.getType() + ")");
            deviceObjects.add(iOSDevice);
        }
        for (AndroidDevice androidDevice : androidDevices) {
            deviceNames.add("Android: " + androidDevice.getName() + " (" + androidDevice.getModel() + ")");
            deviceObjects.add(androidDevice);
        }
        String selected = (String)JOptionPane.showInputDialog(this, "Select a device to download music directly to:", "Download to Device", 3, null, deviceNames.toArray(new String[0]), deviceNames.get(0));
        if (selected != null) {
            boolean isIOS;
            String deviceName;
            String deviceBasePath;
            int n = deviceNames.indexOf(selected);
            Object device = deviceObjects.get(n);
            if (device instanceof IOSDevice) {
                deviceBasePath = ((IOSDevice)device).getPath();
                deviceName = ((IOSDevice)device).getName();
                isIOS = true;
                this.appendLog("Selected iOS device: " + deviceName + "\n");
            } else {
                deviceBasePath = ((AndroidDevice)device).getPath();
                deviceName = ((AndroidDevice)device).getName();
                isIOS = false;
                this.appendLog("Selected Android device: " + deviceName + "\n");
            }
            this.appendLog("\n=== Preparing Device for Download ===\n");
            String preparedPath = this.prepareDeviceFolder(deviceBasePath, isIOS);
            if (preparedPath == null) {
                JOptionPane.showMessageDialog(this, "Failed to prepare device folder!\n\nDevice: " + deviceName + "\nPath: " + deviceBasePath + "\n\nPlease check:\n\u2022 Device is properly connected\n\u2022 USB debugging is enabled (Android)\n\u2022 Device is unlocked\n\u2022 You have write permissions", "Device Error", 0);
                return;
            }
            this.appendLog("\n=== Testing Device Access ===\n");
            if (!this.testDeviceWriteAccess(preparedPath)) {
                JOptionPane.showMessageDialog(this, "Cannot write to device!\n\nDevice: " + deviceName + "\nPath: " + preparedPath + "\n\nPlease ensure:\n\u2022 Device is unlocked\n\u2022 File transfer (MTP) mode is enabled\n\u2022 You have write permissions on the device", "Write Access Denied", 0);
                return;
            }
            this.appendLog("\u2713 Device ready for downloads\n");
            this.appendLog("Download destination: " + preparedPath + "\n");
            this.appendLog("Ready to download. Enter URL and click Download.\n");
            this.tempDeviceDownloadPath = preparedPath;
            this.tempDeviceName = deviceName;
            JOptionPane.showMessageDialog(this, "Device Ready!\n\nDownloads will now be saved to:\n" + deviceName + " \u2192 Music folder\n\nPath: " + preparedPath + "\n\nEnter a URL and click Download to start.", "Ready to Download", 1);
        }
    }

    private List<IOSDevice> detectIOSDevices() {
        ArrayList<IOSDevice> devices;
        block18: {
            devices = new ArrayList<IOSDevice>();
            try {
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    String devicePath;
                    String deviceType;
                    String line;
                    BufferedReader reader;
                    Process process;
                    ProcessBuilder pb;
                    this.appendLog("Scanning for iOS devices...\n");
                    try {
                        pb = new ProcessBuilder("powershell.exe", "-Command", "Get-PnpDevice -Class 'WPD' | Where-Object {$_.Status -eq 'OK' -and ($_.FriendlyName -like '*iPhone*' -or $_.FriendlyName -like '*iPad*' -or $_.FriendlyName -like '*iPod*')} | Select-Object -ExpandProperty FriendlyName");
                        process = pb.start();
                        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        while ((line = reader.readLine()) != null) {
                            if ((line = line.trim()).isEmpty() || !line.contains("iPhone") && !line.contains("iPad") && !line.contains("iPod")) continue;
                            deviceType = line.contains("iPad") ? "iPad" : (line.contains("iPod") ? "iPod" : "iPhone");
                            devicePath = this.findIOSDevicePath();
                            this.appendLog("Found: " + line + "\n");
                            devices.add(new IOSDevice(line, deviceType, devicePath));
                        }
                        reader.close();
                        process.waitFor();
                    }
                    catch (Exception e) {
                        this.appendLog("PowerShell WPD scan: " + e.getMessage() + "\n");
                    }
                    if (devices.isEmpty()) {
                        try {
                            pb = new ProcessBuilder("powershell.exe", "-Command", "Get-Service -Name 'Apple Mobile Device Service' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Status");
                            process = pb.start();
                            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            String status = reader.readLine();
                            if (status != null && status.trim().equals("Running")) {
                                this.appendLog("Apple Mobile Device Service is running\n");
                                this.appendLog("Device may be connected but not accessible via file system\n");
                                this.appendLog("Try: Opening iTunes and check if device appears there\n");
                            } else {
                                this.appendLog("Apple Mobile Device Service is not running\n");
                                this.appendLog("Please install iTunes or Apple Devices app\n");
                            }
                            reader.close();
                            process.waitFor();
                        }
                        catch (Exception e) {
                            this.appendLog("Service check error: " + e.getMessage() + "\n");
                        }
                    }
                    if (devices.isEmpty()) {
                        try {
                            pb = new ProcessBuilder("wmic", "path", "Win32_PnPEntity", "where", "Name like '%Apple%'", "get", "Name");
                            process = pb.start();
                            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            while ((line = reader.readLine()) != null) {
                                if (!(line = line.trim()).contains("Apple") || !line.contains("iPhone") && !line.contains("iPad") && !line.contains("iPod")) continue;
                                deviceType = "iPhone";
                                if (line.contains("iPad")) {
                                    deviceType = "iPad";
                                } else if (line.contains("iPod")) {
                                    deviceType = "iPod";
                                }
                                devicePath = this.findIOSDevicePath();
                                devices.add(new IOSDevice(line, deviceType, devicePath));
                            }
                            reader.close();
                        }
                        catch (Exception e) {
                            this.appendLog("WMIC scan error: " + e.getMessage() + "\n");
                        }
                    }
                    break block18;
                }
                this.appendLog("Note: Auto-detection is limited on this OS.\n");
                this.appendLog("You may need to manually specify the device path.\n");
            }
            catch (Exception e) {
                this.appendLog("Error detecting devices: " + e.getMessage() + "\n");
            }
        }
        return devices;
    }

    private String findIOSDevicePath() {
        try {
            String line;
            ProcessBuilder pb = new ProcessBuilder("wmic", "logicaldisk", "where", "DriveType=2", "get", "DeviceID,VolumeName");
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = reader.readLine()) != null) {
                String[] parts;
                if (!line.contains("iPhone") && !line.contains("iPad") && !line.contains("iPod") || (parts = line.trim().split("\\s+")).length <= 0) continue;
                return parts[0];
            }
            reader.close();
        }
        catch (Exception exception) {
            // empty catch block
        }
        return "\\\\?\\usb#vid_05ac";
    }

    private void showIOSDeviceDialog(List<IOSDevice> devices) {
        Object[] deviceNames = devices.stream().map(d -> d.getName() + " (" + d.getType() + ")").toArray(String[]::new);
        String selected = (String)JOptionPane.showInputDialog(this, "Select an iOS device to transfer music:\n\nNote: Music will be copied to the device's Documents folder.\nYou may need to use a file manager app on your device to access the files.\n\nAlternative: Use iTunes/Finder to sync music to your device.", "iOS Device Manager", 3, null, deviceNames, deviceNames.length > 0 ? deviceNames[0] : null);
        if (selected != null) {
            int index = Arrays.asList(deviceNames).indexOf(selected);
            IOSDevice device = devices.get(index);
            this.openIOSTransferDialog(device);
        }
    }

    private void openIOSTransferDialog(IOSDevice device) {
        Object[] options = new String[]{"Transfer Downloaded Music", "Prepare Apple Export", "View Device Info", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Device: " + device.getName() + "\n\nWhat would you like to do?", "iOS Device: " + device.getName(), -1, 3, null, options, options[0]);
        switch (choice) {
            case 0: {
                this.transferMusicToIOSDevice(device);
                break;
            }
            case 1: {
                this.setupAutoTransfer(device);
                break;
            }
            case 2: {
                this.showDeviceInfo(device);
                break;
            }
            default: {
                this.appendLog("Transfer cancelled.\n");
            }
        }
    }

    private void transferMusicToIOSDevice(IOSDevice device) {
        this.appendLog("\n=== Transfer Music to " + device.getName() + " ===\n");
        File outputDir = new File(this.outputFolder);
        File[] musicFiles = outputDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".flac") || lower.endsWith(".wav");
        });
        if (musicFiles == null || musicFiles.length == 0) {
            this.appendLog("\u26a0\ufe0f No music files found in output folder.\n");
            this.appendLog("Please download some music first.\n");
            JOptionPane.showMessageDialog(this, "No music files found in:\n" + this.outputFolder + "\n\nPlease download music first, then transfer to your device.", "No Music Files", 2);
            return;
        }
        this.appendLog("Found " + musicFiles.length + " music file(s) in output folder.\n\n");
        String message = "\u26a0\ufe0f IMPORTANT: iOS Device Transfer Limitations\n\nDue to iOS security restrictions, direct file transfer is limited.\n\nRecommended methods:\n\n1. iTunes/Finder Sync (Best Quality):\n   \u2022 Open iTunes (Windows) or Finder (Mac)\n   \u2022 Select your device\n   \u2022 Drag music files from:\n     " + this.outputFolder + "\n   \u2022 Sync to your device\n\n2. Cloud Services:\n   \u2022 Upload to iCloud Drive, Dropbox, etc.\n   \u2022 Download on your iOS device\n\n3. Third-Party Apps:\n   \u2022 Use apps like VLC, Documents by Readdle\n   \u2022 Enable WiFi file sharing\n   \u2022 Transfer via web interface\n\nWould you like to:\n\u2022 Open output folder to manually drag files?\n\u2022 Get detailed transfer instructions?";
        int result = JOptionPane.showOptionDialog(this, message, "iOS Transfer Methods", 1, 1, null, new String[]{"Open Folder", "Instructions", "Cancel"}, "Open Folder");
        if (result == 0) {
            this.openOutputFolder();
            this.appendLog("\u2713 Output folder opened. Drag files to iTunes/Finder.\n");
        } else if (result == 1) {
            this.showDetailedTransferInstructions(device);
        } else {
            this.appendLog("Transfer cancelled.\n");
        }
    }

    private void showDetailedTransferInstructions(IOSDevice device) {
        String instructions = "=== Detailed Transfer Instructions ===\n\nMethod 1: iTunes (Windows) / Finder (Mac)\n----------------------------------------\n1. Connect your " + device.getType() + " via USB\n2. Open iTunes (Windows) or Finder (Mac)\n3. Click on your device icon\n4. Go to 'Music' or 'Songs' section\n5. Enable 'Sync Music' if not already enabled\n6. Choose 'Selected playlists, artists, albums, and genres'\n7. Drag music files from output folder to iTunes library\n8. Click 'Apply' or 'Sync' button\n\nMethod 2: VLC for Mobile\n------------------------\n1. Install VLC app from App Store\n2. Open VLC on your device\n3. Enable 'Sharing via WiFi'\n4. Note the IP address shown (e.g., http://192.168.1.10)\n5. On your computer, open a web browser\n6. Enter the IP address\n7. Drag and drop music files to upload\n\nMethod 3: Documents by Readdle\n------------------------------\n1. Install Documents app from App Store\n2. Open Documents app\n3. Tap WiFi icon (bottom right)\n4. Enable WiFi Transfer\n5. Visit the URL shown on your computer's browser\n6. Upload music files\n\nYour music files are located in:\n" + this.outputFolder + "\n\nWould you like to open this folder?";
        JTextArea textArea = new JTextArea(instructions);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", 0, 11));
        textArea.setRows(25);
        textArea.setColumns(60);
        JScrollPane scrollPane = new JScrollPane(textArea);
        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Transfer Instructions for " + device.getName(), 0, 1);
        if (result == 0) {
            this.openOutputFolder();
        }
        this.appendLog(instructions + "\n");
    }

    private void setupAutoTransfer(IOSDevice device) {
        this.appendLog("Preparing Apple export for: " + device.getName() + "\n");
        this.exportForAppleDevice();
    }

    private void showDeviceInfo(IOSDevice device) {
        String info = "Device Information\n\nName: " + device.getName() + "\nType: " + device.getType() + "\nPath: " + device.getPath() + "\n\nTransfer Status: Ready\nSupported Formats: MP3, M4A, ALAC\nRecommended: Use iTunes/Finder for best compatibility";
        this.appendLog("\n" + info + "\n\n");
        JOptionPane.showMessageDialog(this, info, "Device: " + device.getName(), 1);
    }

    private List<AndroidDevice> detectAndroidDevices() {
        ArrayList<AndroidDevice> devices;
        block12: {
            devices = new ArrayList<AndroidDevice>();
            try {
                if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                    String line;
                    BufferedReader reader;
                    Process process;
                    ProcessBuilder pb;
                    this.appendLog("Scanning for Android devices...\n");
                    try {
                        pb = new ProcessBuilder("adb", "devices");
                        process = pb.start();
                        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        boolean foundHeader = false;
                        while ((line = reader.readLine()) != null) {
                            String[] parts;
                            if ((line = line.trim()).contains("List of devices")) {
                                foundHeader = true;
                                continue;
                            }
                            if (!foundHeader || line.isEmpty() || !line.contains("device") || (parts = line.split("\\s+")).length < 2 || !parts[1].equals("device")) continue;
                            String deviceId = parts[0];
                            String devicePath = this.findAndroidDevicePath(deviceId);
                            this.appendLog("Found via ADB: " + deviceId + "\n");
                            devices.add(new AndroidDevice(deviceId, "Android Device", devicePath));
                        }
                        reader.close();
                        process.waitFor();
                    }
                    catch (Exception e) {
                        this.appendLog("ADB not found, trying alternative methods...\n");
                    }
                    if (devices.isEmpty()) {
                        try {
                            pb = new ProcessBuilder("powershell.exe", "-Command", "Get-PnpDevice -Class 'WPD' | Where-Object {$_.Status -eq 'OK' -and $_.FriendlyName -notlike '*Apple*'} | Select-Object -ExpandProperty FriendlyName");
                            process = pb.start();
                            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                            while ((line = reader.readLine()) != null) {
                                if ((line = line.trim()).isEmpty() || line.contains("iPhone") || line.contains("iPad") || line.contains("iPod")) continue;
                                String devicePath = this.findAndroidDevicePath(line);
                                this.appendLog("Found via WPD: " + line + "\n");
                                devices.add(new AndroidDevice(line, "Android Device", devicePath));
                            }
                            reader.close();
                            process.waitFor();
                        }
                        catch (Exception e) {
                            this.appendLog("PowerShell WPD scan failed: " + e.getMessage() + "\n");
                        }
                    }
                    if (devices.isEmpty()) {
                        this.appendLog("Checking for MTP devices...\n");
                    }
                    break block12;
                }
                this.appendLog("Note: Android detection is limited on this OS.\n");
            }
            catch (Exception e) {
                this.appendLog("Error detecting Android devices: " + e.getMessage() + "\n");
            }
        }
        return devices;
    }

    private String findAndroidDevicePath(String deviceId) {
        try {
            File[] roots;
            for (File root : roots = File.listRoots()) {
                String path = root.getAbsolutePath();
                if (!path.matches("[A-Z]:\\\\")) continue;
                try {
                    File dcim = new File(root, "DCIM");
                    File music = new File(root, "Music");
                    if (!dcim.exists() && !music.exists()) continue;
                    return path;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return "This PC\\[Android Device]\\Internal Storage\\Music";
    }

    /*
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    private String prepareDeviceFolder(String devicePath, boolean isIOS) {
        try {
            File musicFolder;
            File deviceRoot = new File(devicePath);
            if (!deviceRoot.exists() || !deviceRoot.isDirectory()) {
                this.appendLog("\u26a0\ufe0f Device path not accessible: " + devicePath + "\n");
                return null;
            }
            if (isIOS) {
                musicFolder = new File(deviceRoot, "Music");
                this.appendLog("iOS Device: Preparing Music folder...\n");
            } else {
                this.appendLog("Android Device: Looking for Music folder...\n");
                musicFolder = new File(deviceRoot, "Music");
                if (!musicFolder.exists()) {
                    File[] commonPaths;
                    for (File path : commonPaths = new File[]{new File(deviceRoot, "Internal storage/Music"), new File(deviceRoot, "Internal Storage/Music"), new File(deviceRoot, "storage/emulated/0/Music"), new File(deviceRoot, "sdcard/Music")}) {
                        if (!path.exists() || !path.isDirectory()) continue;
                        musicFolder = path;
                        this.appendLog("\u2713 Found existing Music folder: " + path.getAbsolutePath() + "\n");
                        break;
                    }
                }
            }
            if (musicFolder.exists()) {
                this.appendLog("\u2713 Music folder exists: " + musicFolder.getAbsolutePath() + "\n");
                return musicFolder.getAbsolutePath();
            }
            this.appendLog("Creating Music folder: " + musicFolder.getAbsolutePath() + "\n");
            if (musicFolder.mkdirs()) {
                this.appendLog("\u2713 Music folder created successfully\n");
                return musicFolder.getAbsolutePath();
            }
            this.appendLog("\u26a0\ufe0f Failed to create Music folder\n");
            return null;
        }
        catch (Exception e) {
            this.appendLog("\u2717 Error preparing device folder: " + e.getMessage() + "\n");
            return null;
        }
    }

    private boolean testDeviceWriteAccess(String devicePath) {
        try {
            File testFile = new File(devicePath, ".test_write_access.tmp");
            this.appendLog("Testing write access to device...\n");
            if (testFile.createNewFile()) {
                this.appendLog("\u2713 Write access test passed\n");
                testFile.delete();
                return true;
            }
            this.appendLog("\u26a0\ufe0f Could not create test file\n");
            return false;
        }
        catch (Exception e) {
            this.appendLog("\u2717 Write access test failed: " + e.getMessage() + "\n");
            return false;
        }
    }

    private void showAndroidDeviceDialog(List<AndroidDevice> devices) {
        Object[] deviceNames = devices.stream().map(d -> d.getName() + " (" + d.getModel() + ")").toArray(String[]::new);
        String selected = (String)JOptionPane.showInputDialog(this, "Select an Android device to transfer music:\n\nNote: Music will be copied to the device's Music folder.\nMake sure USB debugging is enabled or MTP mode is selected.", "Android Device Manager", 3, null, deviceNames, deviceNames.length > 0 ? deviceNames[0] : null);
        if (selected != null) {
            int index = Arrays.asList(deviceNames).indexOf(selected);
            AndroidDevice device = devices.get(index);
            this.openAndroidTransferDialog(device);
        }
    }

    private void openAndroidTransferDialog(AndroidDevice device) {
        Object[] options = new String[]{"Download to this Device", "View Device Info", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Device: " + device.getName() + "\n\nWhat would you like to do?", "Android Device: " + device.getName(), -1, 3, null, options, options[0]);
        switch (choice) {
            case 0: {
                this.setupDownloadToDevice(device);
                break;
            }
            case 1: {
                this.showAndroidDeviceInfo(device);
                break;
            }
            default: {
                this.appendLog("Cancelled.\n");
            }
        }
    }

    private void setupDownloadToDevice(AndroidDevice device) {
        this.downloadToDevice();
    }

    private void transferMusicToAndroidDevice(AndroidDevice device) {
        this.openDeviceTransfer();
    }

    private void showAndroidDeviceInfo(AndroidDevice device) {
        String info = "=== Android Device Info ===\n\nName: " + device.getName() + "\nModel: " + device.getModel() + "\nPath: " + device.getPath() + "\n\nSupported Transfer Methods:\n\u2022 MTP (Media Transfer Protocol)\n\u2022 File Explorer drag-and-drop\n\u2022 ADB push (if USB debugging enabled)\n\nTips:\n\u2022 Ensure device is unlocked\n\u2022 Select 'File Transfer' mode in USB options\n\u2022 Music files go in /Music or /Download folder";
        this.appendLog("\n" + info + "\n\n");
        JOptionPane.showMessageDialog(this, info, "Device: " + device.getName(), 1);
    }

    private void transferDownloadedFilesToDevice(String sourcePath, String devicePath, String deviceName) {
        new Thread(() -> {
            try {
                this.appendLog("\ud83d\udcc2 Scanning downloaded files...\n");
                File sourceDir = new File(sourcePath);
                ArrayList<String> allMusicPaths = new ArrayList<String>();
                this.collectMusicFiles(sourceDir, allMusicPaths);
                ArrayList<File> musicFiles = new ArrayList<File>();
                for (String path : allMusicPaths) {
                    File file = new File(path);
                    Long previousModified = this.deviceDownloadSnapshot.get(file.getAbsolutePath());
                    if (previousModified == null || file.lastModified() > previousModified.longValue()) {
                        musicFiles.add(file);
                    }
                }
                if (musicFiles.isEmpty()) {
                    SwingUtilities.invokeLater(() -> {
                        this.appendLog("\u26a0 No music files found to transfer\n");
                        this.animatedCat.setWalking(false);
                        this.finishDownload();
                    });
                    return;
                }
                this.appendLog("Found " + musicFiles.size() + " new or changed file(s) to transfer\n");
                DeviceTransferManager transferManager = new DeviceTransferManager();
                transferManager.setLogger(this::appendLog);
                String deviceId = this.tempDeviceId;
                if (deviceId == null || !transferManager.detectAndroidDevices().contains(deviceId)) {
                    SwingUtilities.invokeLater(() -> {
                        this.appendLog("\u2717 Device not found. Please reconnect " + deviceName + "\n");
                        this.animatedCat.setWalking(false);
                        this.finishDownload();
                    });
                    return;
                }
                this.appendLog("\ud83d\udce4 Transferring to " + deviceName + "...\n");
                int successCount = transferManager.transferToAndroidPreservingFolders(musicFiles, sourceDir.toPath(), devicePath, deviceId);
                int totalFiles = musicFiles.size();
                SwingUtilities.invokeLater(() -> {
                    if (successCount == totalFiles) {
                        this.appendLog("\n\u2713 All " + totalFiles + " file(s) transferred successfully to " + deviceName + "!\n");
                        this.appendLog("\ud83d\udcc1 Location: " + devicePath + "\n");
                    } else {
                        this.appendLog("\n\u26a0 Transferred " + successCount + " of " + totalFiles + " files\n");
                    }
                    this.tempDeviceDownloadPath = null;
                    this.tempDeviceName = null;
                    this.tempDeviceId = null;
                    this.deviceDownloadSnapshot.clear();
                    this.deviceLabel.setText("");
                    this.animatedCat.setWalking(false);
                    this.finishDownload();
                });
            }
            catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    this.appendLog("\n\u2717 Transfer error: " + e.getMessage() + "\n");
                    this.appendLog("Files remain in: " + sourcePath + "\n");
                    this.tempDeviceDownloadPath = null;
                    this.tempDeviceName = null;
                    this.tempDeviceId = null;
                    this.deviceDownloadSnapshot.clear();
                    this.deviceLabel.setText("");
                    this.animatedCat.setWalking(false);
                    this.finishDownload();
                });
            }
        }).start();
    }

    private static class QueuedDownload {
        private final String url;
        private final int sourceIndex;
        private final int typeIndex;
        private final int formatIndex;

        private QueuedDownload(String url, int sourceIndex, int typeIndex, int formatIndex) {
            this.url = url;
            this.sourceIndex = sourceIndex;
            this.typeIndex = typeIndex;
            this.formatIndex = formatIndex;
        }
    }

    private static class IOSDevice {
        private String name;
        private String type;
        private String path;

        public IOSDevice(String name, String type, String path) {
            this.name = name;
            this.type = type;
            this.path = path;
        }

        public String getName() {
            return this.name;
        }

        public String getType() {
            return this.type;
        }

        public String getPath() {
            return this.path;
        }
    }

    private static class AndroidDevice {
        private String name;
        private String model;
        private String path;

        public AndroidDevice(String name, String model, String path) {
            this.name = name;
            this.model = model;
            this.path = path;
        }

        public String getName() {
            return this.name;
        }

        public String getModel() {
            return this.model;
        }

        public String getPath() {
            return this.path;
        }
    }
}

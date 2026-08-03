package com.musicdownloader.ui;

import com.musicdownloader.AppInfo;
import com.musicdownloader.player.AudioPlayer;
import com.musicdownloader.player.TrackMetadata;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

public final class MusicLibraryDialog extends JDialog {
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "flac", "opus", "m4a", "aac", "wav", "alac", "ogg", "vorbis");
    private static final Color BACKGROUND = new Color(30, 25, 38);
    private static final Color PANEL = new Color(45, 36, 55);
    private static final Color ACCENT = new Color(255, 170, 220);

    private final Path libraryFolder;
    private final AudioPlayer player = new AudioPlayer();
    private final DefaultListModel<File> trackModel = new DefaultListModel<>();
    private final JList<File> trackList = new JList<>(this.trackModel);
    private final JLabel titleLabel = new JLabel("Nothing playing");
    private final JLabel artistLabel = new JLabel("Select a downloaded track");
    private final JLabel albumLabel = new JLabel(" ");
    private final JLabel artworkLabel = new JLabel("NO ART", JLabel.CENTER);
    private final JLabel timeLabel = new JLabel("0:00 / 0:00");
    private final JButton playPauseButton = new JButton("Play");
    private final JSlider seekSlider = new JSlider(0, 1000, 0);
    private final JSlider volumeSlider = new JSlider(0, 100, 80);
    private final JProgressBar scanProgress = new JProgressBar();
    private final Timer positionTimer;
    private volatile double durationSeconds;
    private volatile boolean seekInProgress;

    public MusicLibraryDialog(JFrame parent, Path libraryFolder) {
        super(parent, AppInfo.NAME + " Music Library", false);
        this.libraryFolder = libraryFolder;
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.setSize(900, 600);
        this.setMinimumSize(new Dimension(700, 450));
        this.setLocationRelativeTo(parent);
        this.initComponents();
        this.player.setListener(new PlayerListener());
        this.player.setVolume(0.8f);
        this.positionTimer = new Timer(250, e -> updatePosition());
        this.positionTimer.start();
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                MusicLibraryDialog.this.positionTimer.stop();
                MusicLibraryDialog.this.player.close();
            }
        });
        this.refreshLibrary();
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(BACKGROUND);
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel heading = new JLabel("MUSIC LIBRARY");
        heading.setFont(new Font("Monospaced", Font.BOLD, 18));
        heading.setForeground(ACCENT);
        header.add(heading, BorderLayout.WEST);
        JButton refreshButton = new JButton("Refresh");
        refreshButton.setToolTipText("Scan the download folder again");
        refreshButton.addActionListener(e -> this.refreshLibrary());
        header.add(refreshButton, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        this.trackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.trackList.setBackground(new Color(22, 18, 28));
        this.trackList.setForeground(Color.WHITE);
        this.trackList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.trackList.setFixedCellHeight(34);
        this.trackList.setCellRenderer(new TrackRenderer());
        this.trackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                this.loadSelectedMetadata();
            }
        });
        this.trackList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    MusicLibraryDialog.this.playSelected();
                }
            }
        });
        JScrollPane tracksPane = new JScrollPane(this.trackList);
        tracksPane.setBorder(BorderFactory.createLineBorder(new Color(90, 70, 105)));

        JPanel nowPlaying = new JPanel(new BorderLayout(12, 12));
        nowPlaying.setBackground(PANEL);
        nowPlaying.setBorder(new EmptyBorder(18, 18, 18, 18));
        this.artworkLabel.setPreferredSize(new Dimension(220, 220));
        this.artworkLabel.setOpaque(true);
        this.artworkLabel.setBackground(new Color(24, 20, 30));
        this.artworkLabel.setForeground(new Color(150, 140, 160));
        this.artworkLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        nowPlaying.add(this.artworkLabel, BorderLayout.NORTH);

        JPanel metadata = new JPanel(new GridLayout(3, 1, 0, 4));
        metadata.setOpaque(false);
        this.titleLabel.setFont(new Font("Monospaced", Font.BOLD, 16));
        this.titleLabel.setForeground(Color.WHITE);
        this.artistLabel.setFont(new Font("Monospaced", Font.PLAIN, 13));
        this.artistLabel.setForeground(ACCENT);
        this.albumLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        this.albumLabel.setForeground(new Color(190, 185, 200));
        metadata.add(this.titleLabel);
        metadata.add(this.artistLabel);
        metadata.add(this.albumLabel);
        nowPlaying.add(metadata, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tracksPane, nowPlaying);
        splitPane.setResizeWeight(0.58);
        splitPane.setDividerLocation(500);
        splitPane.setBorder(null);
        root.add(splitPane, BorderLayout.CENTER);

        JPanel playerPanel = new JPanel(new BorderLayout(8, 8));
        playerPanel.setOpaque(false);
        this.scanProgress.setIndeterminate(true);
        this.scanProgress.setVisible(false);
        playerPanel.add(this.scanProgress, BorderLayout.NORTH);

        JPanel seekPanel = new JPanel(new BorderLayout(8, 0));
        seekPanel.setOpaque(false);
        this.seekSlider.setOpaque(false);
        this.seekSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                MusicLibraryDialog.this.seekInProgress = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (MusicLibraryDialog.this.durationSeconds > 0.0) {
                    double target = MusicLibraryDialog.this.durationSeconds * MusicLibraryDialog.this.seekSlider.getValue() / 1000.0;
                    MusicLibraryDialog.this.player.seek(target);
                }
                MusicLibraryDialog.this.seekInProgress = false;
            }
        });
        this.timeLabel.setForeground(Color.WHITE);
        this.timeLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        seekPanel.add(this.seekSlider, BorderLayout.CENTER);
        seekPanel.add(this.timeLabel, BorderLayout.EAST);
        playerPanel.add(seekPanel, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        controls.setOpaque(false);
        JButton previousButton = new JButton("Previous");
        previousButton.setToolTipText("Play the previous track");
        previousButton.addActionListener(e -> this.playRelative(-1));
        this.playPauseButton.setToolTipText("Play or pause the selected track");
        this.playPauseButton.addActionListener(e -> this.togglePlayback());
        JButton stopButton = new JButton("Stop");
        stopButton.setToolTipText("Stop playback");
        stopButton.addActionListener(e -> this.player.stop());
        JButton nextButton = new JButton("Next");
        nextButton.setToolTipText("Play the next track");
        nextButton.addActionListener(e -> this.playRelative(1));
        JLabel volumeLabel = new JLabel("Volume");
        volumeLabel.setForeground(Color.WHITE);
        this.volumeSlider.setPreferredSize(new Dimension(110, 24));
        this.volumeSlider.setOpaque(false);
        this.volumeSlider.addChangeListener(e -> this.player.setVolume(this.volumeSlider.getValue() / 100.0f));
        controls.add(previousButton);
        controls.add(this.playPauseButton);
        controls.add(stopButton);
        controls.add(nextButton);
        controls.add(volumeLabel);
        controls.add(this.volumeSlider);
        playerPanel.add(controls, BorderLayout.SOUTH);
        root.add(playerPanel, BorderLayout.SOUTH);
        this.setContentPane(root);
    }

    private void refreshLibrary() {
        this.scanProgress.setVisible(true);
        new SwingWorker<File[], Void>() {
            @Override
            protected File[] doInBackground() throws Exception {
                if (!Files.isDirectory(MusicLibraryDialog.this.libraryFolder)) {
                    return new File[0];
                }
                try (Stream<Path> paths = Files.walk(MusicLibraryDialog.this.libraryFolder)) {
                    return paths.filter(Files::isRegularFile)
                            .map(Path::toFile)
                            .filter(MusicLibraryDialog::isAudioFile)
                            .sorted(Comparator.comparing(File::getAbsolutePath, String.CASE_INSENSITIVE_ORDER))
                            .toArray(File[]::new);
                }
            }

            @Override
            protected void done() {
                MusicLibraryDialog.this.scanProgress.setVisible(false);
                try {
                    File selected = MusicLibraryDialog.this.trackList.getSelectedValue();
                    MusicLibraryDialog.this.trackModel.clear();
                    for (File file : get()) {
                        MusicLibraryDialog.this.trackModel.addElement(file);
                    }
                    if (selected != null) {
                        MusicLibraryDialog.this.trackList.setSelectedValue(selected, true);
                    } else if (!MusicLibraryDialog.this.trackModel.isEmpty()) {
                        MusicLibraryDialog.this.trackList.setSelectedIndex(0);
                    }
                }
                catch (Exception e) {
                    JOptionPane.showMessageDialog(MusicLibraryDialog.this, "Could not scan library: " + e.getMessage(), "Library Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void loadSelectedMetadata() {
        File selected = this.trackList.getSelectedValue();
        if (selected == null) {
            return;
        }
        this.titleLabel.setText(stripExtension(selected.getName()));
        this.artistLabel.setText("Reading metadata...");
        this.albumLabel.setText(" ");
        this.artworkLabel.setIcon(null);
        this.artworkLabel.setText("NO ART");
        new SwingWorker<TrackMetadata, Void>() {
            @Override
            protected TrackMetadata doInBackground() {
                return TrackMetadata.read(selected);
            }

            @Override
            protected void done() {
                if (!selected.equals(MusicLibraryDialog.this.trackList.getSelectedValue())) {
                    return;
                }
                try {
                    TrackMetadata metadata = get();
                    MusicLibraryDialog.this.titleLabel.setText(metadata.title());
                    MusicLibraryDialog.this.artistLabel.setText(metadata.artist());
                    MusicLibraryDialog.this.albumLabel.setText(metadata.album());
                    MusicLibraryDialog.this.durationSeconds = metadata.durationSeconds();
                    if (metadata.artwork() != null) {
                        Image image = metadata.artwork().getScaledInstance(220, 220, Image.SCALE_SMOOTH);
                        MusicLibraryDialog.this.artworkLabel.setText("");
                        MusicLibraryDialog.this.artworkLabel.setIcon(new ImageIcon(image));
                    }
                }
                catch (Exception e) {
                    MusicLibraryDialog.this.artistLabel.setText("Unknown artist");
                }
            }
        }.execute();
    }

    private void togglePlayback() {
        AudioPlayer.State state = this.player.getState();
        if (state == AudioPlayer.State.IDLE) {
            this.playSelected();
        } else {
            this.player.togglePause();
        }
    }

    private void playSelected() {
        File selected = this.trackList.getSelectedValue();
        if (selected == null) {
            return;
        }
        this.seekSlider.setValue(0);
        this.player.play(selected);
        this.loadSelectedMetadata();
    }

    private void playRelative(int offset) {
        if (this.trackModel.isEmpty()) {
            return;
        }
        int index = this.trackList.getSelectedIndex();
        if (index < 0) {
            index = 0;
        } else {
            index = Math.floorMod(index + offset, this.trackModel.size());
        }
        this.trackList.setSelectedIndex(index);
        this.trackList.ensureIndexIsVisible(index);
        this.playSelected();
    }

    private void updatePosition() {
        double position = this.player.getPositionSeconds();
        if (!this.seekInProgress && this.durationSeconds > 0.0) {
            this.seekSlider.setValue((int)Math.min(1000, position * 1000.0 / this.durationSeconds));
        }
        this.timeLabel.setText(formatTime(position) + " / " + formatTime(this.durationSeconds));
    }

    private static boolean isAudioFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && AUDIO_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String formatTime(double seconds) {
        long total = Math.max(0L, Math.round(seconds));
        return String.format(Locale.ROOT, "%d:%02d", total / 60L, total % 60L);
    }

    private final class PlayerListener implements AudioPlayer.Listener {
        @Override
        public void onStateChanged(AudioPlayer.State state) {
            SwingUtilities.invokeLater(() -> MusicLibraryDialog.this.playPauseButton.setText(state == AudioPlayer.State.PLAYING ? "Pause" : "Play"));
        }

        @Override
        public void onCompleted(File file) {
            SwingUtilities.invokeLater(() -> MusicLibraryDialog.this.playRelative(1));
        }

        @Override
        public void onError(File file, String message) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    MusicLibraryDialog.this,
                    "Could not play " + file.getName() + ":\n" + message + "\n\nCheck that FFmpeg is installed.",
                    "Playback Error",
                    JOptionPane.ERROR_MESSAGE));
        }
    }

    private static final class TrackRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel label = (JLabel)super.getListCellRendererComponent(list, value, index, selected, focus);
            File file = (File)value;
            label.setText(stripExtension(file.getName()));
            label.setToolTipText(file.getAbsolutePath());
            label.setBorder(new EmptyBorder(3, 8, 3, 8));
            return label;
        }
    }
}

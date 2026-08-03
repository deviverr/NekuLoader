/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.downloader;

import com.musicdownloader.downloader.AppleMusicParser;
import com.musicdownloader.downloader.DeezerParser;
import com.musicdownloader.downloader.DownloadEstimator;
import com.musicdownloader.utils.AndroidDeviceHelper;
import com.musicdownloader.utils.FolderIconManager;
import com.musicdownloader.utils.Settings;
import com.musicdownloader.utils.ToolLocator;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DownloadManager {
    private String outputFolder;
    private Process currentProcess;
    private boolean addTrackNumbers = true;
    private Settings settings;
    private volatile boolean isUserStopped = false;
    private DownloadEstimator estimator;

    private static String pythonCommand() {
        return ToolLocator.python();
    }

    private static String pipCommand() {
        return ToolLocator.pip();
    }

    public DownloadManager(String outputFolder) {
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder cannot be null or empty");
        }
        this.outputFolder = outputFolder;
        this.settings = new Settings();
        this.estimator = new DownloadEstimator();
    }

    public DownloadManager(String outputFolder, Settings settings) {
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder cannot be null or empty");
        }
        if (settings == null) {
            throw new IllegalArgumentException("Settings cannot be null");
        }
        this.outputFolder = outputFolder;
        this.settings = settings;
        this.estimator = new DownloadEstimator();
    }

    public DownloadEstimator getEstimator() {
        return this.estimator;
    }

    public void setOutputFolder(String outputFolder) {
        if (outputFolder == null || outputFolder.trim().isEmpty()) {
            throw new IllegalArgumentException("Output folder cannot be null or empty");
        }
        this.outputFolder = outputFolder;
    }

    public String getOutputFolder() {
        return this.outputFolder;
    }

    public void setAddTrackNumbers(boolean add) {
        this.addTrackNumbers = add;
    }

    public void stopDownload() {
        this.isUserStopped = true;
        if (this.currentProcess != null && this.currentProcess.isAlive()) {
            this.currentProcess.destroy();
            try {
                if (!this.currentProcess.waitFor(5L, TimeUnit.SECONDS)) {
                    this.currentProcess.destroyForcibly();
                    this.currentProcess.waitFor();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.currentProcess.destroyForcibly();
                try {
                    this.currentProcess.waitFor();
                }
                catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public boolean testWriteAccess(String destinationPath, Consumer<String> logger) {
        if (destinationPath == null || destinationPath.trim().isEmpty()) {
            logger.accept("\u2717 Invalid destination path\n");
            return false;
        }
        if (AndroidDeviceHelper.isMTPPath(destinationPath)) {
            logger.accept("\u26a0\ufe0f ANDROID/MTP DEVICE DETECTED\n");
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            logger.accept("Path: " + destinationPath + "\n\n");
            logger.accept("\u274c Direct download to Android devices is NOT supported!\n\n");
            logger.accept("This is a Windows MTP limitation - Python/Java apps cannot\n");
            logger.accept("write to Android devices connected via USB.\n\n");
            logger.accept("\u2705 SOLUTION: Download to PC first, then transfer:\n");
            logger.accept("   Recommended folder: " + AndroidDeviceHelper.getDefaultMusicDownloadsFolder() + "\n\n");
            if (AndroidDeviceHelper.isADBAvailable()) {
                logger.accept("\u2713 ADB detected on your system!\n");
                logger.accept("You can use ADB to transfer files after download.\n\n");
            } else {
                logger.accept("\ud83d\udca1 TIP: Install ADB for easier file transfers\n");
                logger.accept("   https://developer.android.com/tools/releases/platform-tools\n\n");
            }
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            return false;
        }
        File destination = new File(destinationPath);
        if (!destination.exists()) {
            logger.accept("\u26a0 Destination folder does not exist: " + destinationPath + "\n");
            logger.accept("Attempting to create folder...\n");
            try {
                if (!destination.mkdirs()) {
                    logger.accept("\u2717 Failed to create folder\n");
                    return false;
                }
                logger.accept("\u2713 Folder created successfully\n");
            }
            catch (Exception e) {
                logger.accept("\u2717 Error creating folder: " + e.getMessage() + "\n");
                return false;
            }
        }
        if (!destination.isDirectory()) {
            logger.accept("\u2717 Path is not a directory: " + destinationPath + "\n");
            return false;
        }
        File testFile = new File(destination, ".test_write_access_" + System.currentTimeMillis() + ".txt");
        try {
            logger.accept("Testing write access...\n");
            if (testFile.createNewFile()) {
                try (FileWriter writer = new FileWriter(testFile);){
                    writer.write("Write access test");
                    writer.flush();
                }
                if (testFile.exists() && testFile.canRead() && testFile.length() > 0L) {
                    logger.accept("\u2713 Write access confirmed\n");
                    if (testFile.delete()) {
                        logger.accept("\u2713 Test file cleaned up\n");
                    } else {
                        logger.accept("\u26a0 Could not delete test file (not critical)\n");
                    }
                    return true;
                }
                logger.accept("\u2717 Created file is not readable or empty\n");
                testFile.delete();
                return false;
            }
            logger.accept("\u2717 Cannot create file in destination\n");
            return false;
        }
        catch (IOException e) {
            logger.accept("\u2717 Write access test failed: " + e.getMessage() + "\n");
            if (e.getMessage().contains("Access is denied") || e.getMessage().contains("device")) {
                logger.accept("\u26a0 This may be an MTP device or have permission issues\n");
                logger.accept("\u26a0 Try:\n");
                logger.accept("  - Unlock your device\n");
                logger.accept("  - Set USB mode to 'File Transfer' (not 'Charging only')\n");
                logger.accept("  - Download to PC instead\n");
            }
            if (testFile.exists()) {
                try {
                    testFile.delete();
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            return false;
        }
        catch (SecurityException e) {
            logger.accept("\u2717 Security restriction: " + e.getMessage() + "\n");
            return false;
        }
    }

    public void downloadFromSpotify(String url, String format, String quality, Consumer<String> logger) throws Exception {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty");
        }
        if (format == null || format.trim().isEmpty()) {
            throw new IllegalArgumentException("Format cannot be null or empty");
        }
        if (logger == null) {
            throw new IllegalArgumentException("Logger cannot be null");
        }
        if (format.equals("alac")) {
            throw new IllegalArgumentException("Spotify matching does not currently support ALAC. Use M4A or FLAC instead.");
        }
        logger.accept("Starting Spotify download with " + format.toUpperCase() + " format...\n");
        logger.accept("INFO: Attempting direct Spotify download (requires premium account)\n");
        try {
            this.downloadFromSpotifyDirect(url, format, quality, logger);
        }
        catch (Exception e) {
            logger.accept("\n\u26a0\ufe0f Direct Spotify download failed. Trying alternative method...\n");
            logger.accept("Alternative: Using spotdl to match songs from YouTube Music\n\n");
            this.downloadFromSpotifyFallback(url, format, quality, logger);
        }
    }

    private void downloadFromSpotifyDirect(String url, String format, String quality, Consumer<String> logger) throws Exception {
        logger.accept("DEBUG: Starting direct Spotify download with Zotify\n");
        ArrayList<String> command = new ArrayList<String>();
        command.add(DownloadManager.pythonCommand());
        command.add("-m");
        command.add("zotify");
        command.add("--download-format");
        command.add(format.toUpperCase());
        if (format.equals("mp3")) {
            command.add("--download-quality");
            command.add(quality + "k");
        } else if (format.equals("flac")) {
            command.add("--download-quality");
            command.add("flac");
        }
        command.add("-d");
        command.add(this.outputFolder);
        if (this.addTrackNumbers) {
            command.add("--track-numbers");
        }
        command.add(url);
        logger.accept("DEBUG: Zotify command: " + String.join((CharSequence)" ", command) + "\n");
        ProcessBuilder pb = new ProcessBuilder(command);
        try {
            this.executeProcess(pb, logger);
            if (url.contains("/album/") || url.contains("/playlist/")) {
                this.postProcessAlbumFolder(logger);
            }
        }
        catch (Exception e) {
            String errorMessage = e.getMessage() == null ? "" : e.getMessage();
            if (errorMessage.contains("CreateProcess error=2") || errorMessage.contains("No module named")) {
                logger.accept("\n\u26a0\ufe0f Zotify not found! Installing...\n");
                this.installZotify(logger);
                this.executeProcess(pb, logger);
                if (url.contains("/album/") || url.contains("/playlist/")) {
                    this.postProcessAlbumFolder(logger);
                }
            }
            throw e;
        }
    }

    private void downloadFromSpotifyFallback(String url, String format, String quality, Consumer<String> logger) throws Exception {
        logger.accept("DEBUG: Starting Spotify fallback download\n");
        boolean isAlbumOrPlaylist = url.contains("/album/") || url.contains("/playlist/");
        logger.accept("DEBUG: Detected as " + (isAlbumOrPlaylist ? "album/playlist" : "single track") + "\n");
        ArrayList<String> command = new ArrayList<String>();
        command.add(ToolLocator.spotdl());
        command.add("download");
        command.add(url);
        command.add("--format");
        command.add(this.spotdlFormat(format));
        if (format.equals("mp3") || format.equals("opus") || format.equals("vorbis") || format.equals("m4a")) {
            command.add("--bitrate");
            command.add(quality + "k");
        }
        command.add("--add-unavailable");
        command.add("--generate-lrc");
        String outputPattern;
        if (isAlbumOrPlaylist) {
            logger.accept("Detected album or playlist - creating subfolder...\n");
            if (this.addTrackNumbers) {
                command.add("--playlist-numbering");
                outputPattern = this.outputFolder + File.separator + "{album}" + File.separator + "{title}.{output-ext}";
            } else {
                outputPattern = this.outputFolder + File.separator + "{album}" + File.separator + "{title}.{output-ext}";
            }
            command.add("--output");
            command.add(outputPattern);
        } else {
            outputPattern = this.outputFolder + File.separator + "{title}.{output-ext}";
            command.add("--output");
            command.add(outputPattern);
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        this.executeProcess(pb, logger);
        if (isAlbumOrPlaylist) {
            try {
                Thread.sleep(1000L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.postProcessAlbumFolder(logger);
        }
    }

    public void downloadFromYouTube(String url, boolean isPlaylist, String format, String quality, Consumer<String> logger) throws Exception {
        logger.accept("Starting YouTube Music download with " + format.toUpperCase() + " format...\n");
        this.downloadFromGenericSource(url, isPlaylist, format, quality, "YouTube Music", logger);
    }

    public void downloadFromSoundCloud(String url, boolean isPlaylist, String format, String quality, Consumer<String> logger) throws Exception {
        logger.accept("Starting SoundCloud download with " + format.toUpperCase() + " format...\n");
        this.downloadFromGenericSource(url, isPlaylist, format, quality, "SoundCloud", logger);
    }

    public void downloadFromDeezer(String url, boolean isPlaylist, String format, String quality, Consumer<String> logger) throws Exception {
        String parseMode = this.settings.getParseMode();
        logger.accept("Starting Deezer download with " + format.toUpperCase() + " format...\n");
        logger.accept("\u2139\ufe0f INFO: Deezer Smart Download Mode (Parse Mode: " + parseMode + ")\n");
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("\u26a0\ufe0f Parsing Deezer URL and extracting metadata...\n\n");
        try {
            DeezerParser.ParsedResult parsed = DeezerParser.parseDeezerUrl(url);
            logger.accept("\u2705 Successfully parsed Deezer " + parsed.type + "!\n");
            logger.accept("\ud83d\udcc0 Title: " + parsed.title + "\n");
            logger.accept("\ud83c\udfa4 Artist: " + parsed.artist + "\n");
            logger.accept("\ud83c\udfb5 Tracks: " + parsed.tracks.size() + "\n\n");
            logger.accept("Track List:\n");
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            for (DeezerParser.Track track : parsed.tracks) {
                logger.accept(track.toString() + "\n");
            }
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
            if (parseMode.equals("spotify")) {
                logger.accept("\ud83d\udd0d Now downloading from Spotify...\n\n");
                this.downloadTracksWithParseMode(parsed, format, quality, "spotify", logger);
            } else if (parseMode.equals("dual")) {
                logger.accept("\ud83d\udd0d Now downloading with Dual Mode (comparing Spotify and YouTube Music quality)...\n\n");
                this.downloadTracksWithParseMode(parsed, format, quality, "dual", logger);
            } else {
                logger.accept("\ud83d\udd0d Now downloading from YouTube Music...\n\n");
                this.downloadTracksWithParseMode(parsed, format, quality, "youtube-music", logger);
            }
        }
        catch (Exception e) {
            logger.accept("\n\u26a0\ufe0f Failed to parse Deezer URL: " + e.getMessage() + "\n");
            logger.accept("Falling back to manual instructions...\n\n");
            this.provideDeezerToYouTubeMusicInstructions(url, format, quality, logger);
        }
    }

    private void downloadTracksWithParseMode(DeezerParser.ParsedResult parsed, String format, String quality, String parseMode, Consumer<String> logger) throws Exception {
        boolean isCollection;
        String albumFolder = parsed.title;
        isCollection = parsed.type.equals("album") || parsed.type.equals("playlist");
        if (isCollection) {
            logger.accept("Creating folder: " + albumFolder + "\n");
        }
        int successCount = 0;
        int failCount = 0;
        int spotifyCount = 0;
        int youtubeCount = 0;
        for (int i = 0; i < parsed.tracks.size(); ++i) {
            DeezerParser.Track track = parsed.tracks.get(i);
            logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            logger.accept("Downloading track " + (i + 1) + "/" + parsed.tracks.size() + "\n");
            logger.accept("\ud83c\udfb5 " + track.getSearchQuery() + "\n");
            try {
                if (parseMode.equals("dual")) {
                    logger.accept("\ud83d\udd0d Comparing quality: Spotify vs YouTube Music...\n");
                    String chosenSource = this.compareSources(track.getSearchQuery(), format, logger);
                    logger.accept("\u2705 Chosen source: " + chosenSource + "\n");
                    logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                    if (chosenSource.equals("spotify")) {
                        this.downloadSingleTrackFromSpotify(track.getSearchQuery(), albumFolder, isCollection, format, quality, logger);
                        ++spotifyCount;
                    } else {
                        this.downloadSingleTrackFromYouTube(track, albumFolder, isCollection, format, quality, logger);
                        ++youtubeCount;
                    }
                } else if (parseMode.equals("spotify")) {
                    logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                    this.downloadSingleTrackFromSpotify(track.getSearchQuery(), albumFolder, isCollection, format, quality, logger);
                    ++spotifyCount;
                } else {
                    logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                    this.downloadSingleTrackFromYouTube(track, albumFolder, isCollection, format, quality, logger);
                    ++youtubeCount;
                }
                ++successCount;
                continue;
            }
            catch (Exception e) {
                ++failCount;
                logger.accept("\u26a0\ufe0f Failed to download: " + e.getMessage() + "\n");
            }
        }
        logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("\u2705 Download complete!\n");
        logger.accept("Successfully downloaded: " + successCount + "/" + parsed.tracks.size() + "\n");
        if (parseMode.equals("dual")) {
            logger.accept("\ud83d\udcca Sources used: Spotify (" + spotifyCount + "), YouTube Music (" + youtubeCount + ")\n");
        } else if (parseMode.equals("spotify")) {
            logger.accept("\ud83d\udcca All tracks downloaded from Spotify\n");
        } else {
            logger.accept("\ud83d\udcca All tracks downloaded from YouTube Music\n");
        }
        if (failCount > 0) {
            logger.accept("Failed: " + failCount + "\n");
        }
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        if (isCollection) {
            try {
                Thread.sleep(1000L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.postProcessPlaylistFolder(logger);
        }
    }

    private String compareSources(String searchQuery, String format, Consumer<String> logger) {
        try {
            if (format.equals("flac") || format.equals("mp3")) {
                return "spotify";
            }
            return "youtube-music";
        }
        catch (Exception e) {
            logger.accept("\u26a0\ufe0f Quality comparison failed, using YouTube Music\n");
            return "youtube-music";
        }
    }

    private void downloadSingleTrackFromSpotify(String searchQuery, String albumFolder, boolean isCollection, String format, String quality, Consumer<String> logger) throws Exception {
        if (format.equals("alac")) {
            logger.accept("ALAC is not supported by SpotDL; using YouTube Music matching for this track.\n");
            this.downloadSearchResult(searchQuery, albumFolder, isCollection, format, quality, logger);
            return;
        }
        String template = isCollection ? this.outputFolder + File.separator + albumFolder + File.separator + "{title}.{output-ext}" : this.outputFolder + File.separator + "{title}.{output-ext}";
        ArrayList<String> command = new ArrayList<String>();
        command.add(ToolLocator.spotdl());
        command.add("download");
        command.add(searchQuery);
        command.add("--format");
        command.add(this.spotdlFormat(format));
        if (format.equals("mp3") || format.equals("opus") || format.equals("vorbis") || format.equals("m4a")) {
            command.add("--bitrate");
            command.add(quality + "k");
        }
        command.add("--output");
        command.add(template);
        this.executeProcess(new ProcessBuilder(command), logger);
    }

    private void downloadSearchResult(String searchQuery, String albumFolder, boolean isCollection, String format, String quality, Consumer<String> logger) throws Exception {
        String template = isCollection ? this.outputFolder + File.separator + albumFolder + File.separator + "%(title)s.%(ext)s" : this.outputFolder + File.separator + "%(title)s.%(ext)s";
        ArrayList<String> command = new ArrayList<String>();
        command.addAll(ToolLocator.ytDlpCommand());
        this.addYtDlpRuntimeOptions(command);
        this.addYtDlpSafetyOptions(command, false);
        command.add("-x");
        command.add("--audio-format");
        command.add(format);
        command.add("--audio-quality");
        command.add(quality);
        command.add("--embed-metadata");
        command.add("--progress");
        command.add("--newline");
        command.add("-o");
        command.add(template);
        command.add("ytsearch1:" + searchQuery);
        this.executeProcess(new ProcessBuilder(command), logger);
    }

    private void downloadTracksWithParseMode(AppleMusicParser.ParsedResult parsed, String format, String quality, String parseMode, Consumer<String> logger) throws Exception {
        boolean isCollection;
        String albumFolder = parsed.title;
        isCollection = parsed.type.equals("album") || parsed.type.equals("playlist");
        if (isCollection) {
            logger.accept("Creating folder: " + albumFolder + "\n");
        }
        int successCount = 0;
        int failCount = 0;
        int spotifyCount = 0;
        int youtubeCount = 0;
        for (int i = 0; i < parsed.tracks.size(); ++i) {
            AppleMusicParser.Track track = parsed.tracks.get(i);
            logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            logger.accept("Downloading track " + (i + 1) + "/" + parsed.tracks.size() + "\n");
            logger.accept("\ud83c\udfb5 " + track.getSearchQuery() + "\n");
            try {
                if (parseMode.equals("dual")) {
                    logger.accept("\ud83d\udd0d Comparing quality: Spotify vs YouTube Music...\n");
                    String chosenSource = this.compareSources(track.getSearchQuery(), format, logger);
                    logger.accept("\u2705 Chosen source: " + chosenSource + "\n");
                    logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                    if (chosenSource.equals("spotify")) {
                        this.downloadSingleTrackFromSpotify(track.getSearchQuery(), albumFolder, isCollection, format, quality, logger);
                        ++spotifyCount;
                    } else {
                        this.downloadSingleTrackFromYouTube(track, albumFolder, isCollection, format, quality, logger);
                        ++youtubeCount;
                    }
                } else if (parseMode.equals("spotify")) {
                    logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                    this.downloadSingleTrackFromSpotify(track.getSearchQuery(), albumFolder, isCollection, format, quality, logger);
                    ++spotifyCount;
                } else {
                    logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
                    this.downloadSingleTrackFromYouTube(track, albumFolder, isCollection, format, quality, logger);
                    ++youtubeCount;
                }
                ++successCount;
                continue;
            }
            catch (Exception e) {
                ++failCount;
                logger.accept("\u26a0\ufe0f Failed to download: " + e.getMessage() + "\n");
            }
        }
        logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("\u2705 Download complete!\n");
        logger.accept("Successfully downloaded: " + successCount + "/" + parsed.tracks.size() + "\n");
        if (parseMode.equals("dual")) {
            logger.accept("\ud83d\udcca Sources used: Spotify (" + spotifyCount + "), YouTube Music (" + youtubeCount + ")\n");
        } else if (parseMode.equals("spotify")) {
            logger.accept("\ud83d\udcca All tracks downloaded from Spotify\n");
        } else {
            logger.accept("\ud83d\udcca All tracks downloaded from YouTube Music\n");
        }
        if (failCount > 0) {
            logger.accept("Failed: " + failCount + "\n");
        }
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        if (isCollection) {
            try {
                Thread.sleep(1000L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.postProcessPlaylistFolder(logger);
        }
    }

    private void downloadTracksFromYouTubeMusic(DeezerParser.ParsedResult parsed, String format, String quality, Consumer<String> logger) throws Exception {
        boolean isCollection;
        String albumFolder = parsed.title;
        isCollection = parsed.type.equals("album") || parsed.type.equals("playlist");
        if (isCollection) {
            logger.accept("Creating folder: " + albumFolder + "\n");
        }
        int successCount = 0;
        int failCount = 0;
        for (int i = 0; i < parsed.tracks.size(); ++i) {
            DeezerParser.Track track = parsed.tracks.get(i);
            logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            logger.accept("Downloading track " + (i + 1) + "/" + parsed.tracks.size() + "\n");
            logger.accept("\ud83c\udfb5 " + track.getSearchQuery() + "\n");
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            try {
                this.downloadSingleTrackFromYouTube(track, albumFolder, isCollection, format, quality, logger);
                ++successCount;
                continue;
            }
            catch (Exception e) {
                ++failCount;
                logger.accept("\u26a0\ufe0f Failed to download: " + e.getMessage() + "\n");
            }
        }
        logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("\u2705 Download complete!\n");
        logger.accept("Successfully downloaded: " + successCount + "/" + parsed.tracks.size() + "\n");
        if (failCount > 0) {
            logger.accept("Failed: " + failCount + "\n");
        }
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        if (isCollection) {
            try {
                Thread.sleep(1000L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.postProcessPlaylistFolder(logger);
        }
    }

    private void downloadTracksFromYouTubeMusic(AppleMusicParser.ParsedResult parsed, String format, String quality, Consumer<String> logger) throws Exception {
        boolean isCollection;
        String albumFolder = parsed.title;
        isCollection = parsed.type.equals("album") || parsed.type.equals("playlist");
        if (isCollection) {
            logger.accept("Creating folder: " + albumFolder + "\n");
        }
        int successCount = 0;
        int failCount = 0;
        for (int i = 0; i < parsed.tracks.size(); ++i) {
            AppleMusicParser.Track track = parsed.tracks.get(i);
            logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            logger.accept("Downloading track " + (i + 1) + "/" + parsed.tracks.size() + "\n");
            logger.accept("\ud83c\udfb5 " + track.getSearchQuery() + "\n");
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            try {
                this.downloadSingleTrackFromYouTube(track, albumFolder, isCollection, format, quality, logger);
                ++successCount;
                continue;
            }
            catch (Exception e) {
                ++failCount;
                logger.accept("\u26a0\ufe0f Failed to download: " + e.getMessage() + "\n");
            }
        }
        logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("\u2705 Download complete!\n");
        logger.accept("Successfully downloaded: " + successCount + "/" + parsed.tracks.size() + "\n");
        if (failCount > 0) {
            logger.accept("Failed: " + failCount + "\n");
        }
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        if (isCollection) {
            try {
                Thread.sleep(1000L);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            this.postProcessPlaylistFolder(logger);
        }
    }

    private void downloadSingleTrackFromYouTube(DeezerParser.Track track, String albumFolder, boolean isCollection, String format, String quality, Consumer<String> logger) throws Exception {
        String template;
        String searchQuery = track.getSearchQuery();
        String ytSearchUrl = "ytsearch1:" + searchQuery;
        if (isCollection) {
            if (this.addTrackNumbers) {
                String trackNum = String.format("%02d", track.trackNumber);
                template = this.outputFolder + File.separator + albumFolder + File.separator + trackNum + " - %(title)s.%(ext)s";
            } else {
                template = this.outputFolder + File.separator + albumFolder + File.separator + "%(title)s.%(ext)s";
            }
        } else {
            template = this.outputFolder + File.separator + "%(title)s.%(ext)s";
        }
        ArrayList<String> command = new ArrayList<String>();
        command.addAll(ToolLocator.ytDlpCommand());
        this.addYtDlpRuntimeOptions(command);
        this.addYtDlpSafetyOptions(command, isCollection);
        command.add("-x");
        command.add("--audio-format");
        command.add(format);
        command.add("--audio-quality");
        command.add(quality);
        command.add("--socket-timeout");
        command.add("30");
        command.add("--retries");
        command.add("10");
        command.add("--embed-metadata");
        if (!format.equals("wav")) {
            command.add("--embed-thumbnail");
            command.add("--convert-thumbnails");
            command.add("jpg");
        }
        command.add("--parse-metadata");
        command.add("%(title)s:%(meta_title)s");
        command.add("--parse-metadata");
        command.add(track.artist + ":%(meta_artist)s");
        command.add("--parse-metadata");
        command.add(track.album + ":%(meta_album)s");
        command.add("--parse-metadata");
        command.add(String.valueOf(track.trackNumber) + ":%(meta_track)s");
        command.add("--progress");
        command.add("--newline");
        command.add("-o");
        command.add(template);
        command.add(ytSearchUrl);
        this.executeProcess(new ProcessBuilder(command), logger);
    }

    private void downloadSingleTrackFromYouTube(AppleMusicParser.Track track, String albumFolder, boolean isCollection, String format, String quality, Consumer<String> logger) throws Exception {
        String template;
        String searchQuery = track.getSearchQuery();
        String ytSearchUrl = "ytsearch1:" + searchQuery;
        if (isCollection) {
            if (this.addTrackNumbers) {
                String trackNum = String.format("%02d", track.trackNumber);
                template = this.outputFolder + File.separator + albumFolder + File.separator + trackNum + " - %(title)s.%(ext)s";
            } else {
                template = this.outputFolder + File.separator + albumFolder + File.separator + "%(title)s.%(ext)s";
            }
        } else {
            template = this.outputFolder + File.separator + "%(title)s.%(ext)s";
        }
        ArrayList<String> command = new ArrayList<String>();
        command.addAll(ToolLocator.ytDlpCommand());
        this.addYtDlpRuntimeOptions(command);
        this.addYtDlpSafetyOptions(command, isCollection);
        command.add("-x");
        command.add("--audio-format");
        command.add(format);
        command.add("--audio-quality");
        command.add(quality);
        command.add("--socket-timeout");
        command.add("30");
        command.add("--retries");
        command.add("10");
        command.add("--embed-metadata");
        if (!format.equals("wav")) {
            command.add("--embed-thumbnail");
            command.add("--convert-thumbnails");
            command.add("jpg");
        }
        command.add("--parse-metadata");
        command.add("%(title)s:%(meta_title)s");
        command.add("--parse-metadata");
        command.add(track.artist + ":%(meta_artist)s");
        command.add("--parse-metadata");
        command.add(track.album + ":%(meta_album)s");
        command.add("--parse-metadata");
        command.add(String.valueOf(track.trackNumber) + ":%(meta_track)s");
        command.add("--progress");
        command.add("--newline");
        command.add("-o");
        command.add(template);
        command.add(ytSearchUrl);
        this.executeProcess(new ProcessBuilder(command), logger);
    }

    private void provideDeezerToYouTubeMusicInstructions(String url, String format, String quality, Consumer<String> logger) throws Exception {
        logger.accept("\ud83d\udd0d STEP 1: Find your album on YouTube Music\n");
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("1. Open: https://music.youtube.com\n");
        logger.accept("2. Copy this Deezer URL: " + url + "\n");
        logger.accept("3. Go to the album page on Deezer in your browser\n");
        logger.accept("4. Copy the ALBUM NAME and ARTIST NAME\n");
        logger.accept("5. Search for it on YouTube Music\n");
        logger.accept("6. Copy the YouTube Music URL\n");
        logger.accept("7. Come back here and paste the YouTube Music URL\n\n");
        logger.accept("\ud83d\udca1 TIP: Look for albums with the same track count and order\n\n");
        logger.accept("\ud83c\udfaf Or try this direct search (copy and paste in YouTube Music):\n");
        String searchSuggestion = this.getBasicSearchSuggestion(url);
        logger.accept("   Search for: \"" + searchSuggestion + "\"\n\n");
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("Once you have the YouTube Music URL, select 'YouTube Music'\n");
        logger.accept("as the source and paste the URL to download!\n\n");
        throw new Exception("Please find this album on YouTube Music first, then paste that URL here.");
    }

    private String getBasicSearchSuggestion(String url) {
        try {
            if (url.contains("/album/")) {
                return "album music popular";
            }
            if (url.contains("/track/")) {
                return "music song";
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return "music";
    }

    public void downloadFromAppleMusic(String url, boolean isPlaylist, String format, String quality, Consumer<String> logger) throws Exception {
        String parseMode = this.settings.getParseMode();
        logger.accept("Starting Apple Music download with " + format.toUpperCase() + " format...\n");
        logger.accept("\u2139\ufe0f INFO: Apple Music Smart Download Mode (Parse Mode: " + parseMode + ")\n");
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("\u26a0\ufe0f Parsing Apple Music URL and extracting metadata...\n\n");
        try {
            AppleMusicParser.ParsedResult parsed = AppleMusicParser.parseAppleMusicUrl(url);
            logger.accept("\u2705 Successfully parsed Apple Music " + parsed.type + "!\n");
            logger.accept("\ud83d\udcc0 Title: " + parsed.title + "\n");
            logger.accept("\ud83c\udfa4 Artist: " + parsed.artist + "\n");
            logger.accept("\ud83c\udfb5 Tracks: " + parsed.tracks.size() + "\n\n");
            logger.accept("Track List:\n");
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
            for (AppleMusicParser.Track track : parsed.tracks) {
                logger.accept(track.toString() + "\n");
            }
            logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
            if (parseMode.equals("spotify")) {
                logger.accept("\ud83d\udd0d Now downloading from Spotify...\n\n");
                this.downloadTracksWithParseMode(parsed, format, quality, "spotify", logger);
            } else if (parseMode.equals("dual")) {
                logger.accept("\ud83d\udd0d Now downloading with Dual Mode (comparing Spotify and YouTube Music quality)...\n\n");
                this.downloadTracksWithParseMode(parsed, format, quality, "dual", logger);
            } else {
                logger.accept("\ud83d\udd0d Now downloading from YouTube Music...\n\n");
                this.downloadTracksWithParseMode(parsed, format, quality, "youtube-music", logger);
            }
        }
        catch (Exception e) {
            logger.accept("\n\u26a0\ufe0f Failed to parse Apple Music URL: " + e.getMessage() + "\n");
            logger.accept("Falling back to manual instructions...\n\n");
            this.provideAppleMusicToYouTubeMusicInstructions(url, format, quality, logger);
        }
    }

    private void provideAppleMusicToYouTubeMusicInstructions(String url, String format, String quality, Consumer<String> logger) throws Exception {
        logger.accept("\ud83d\udd0d STEP 1: Find your album on YouTube Music\n");
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("1. Open: https://music.youtube.com\n");
        logger.accept("2. Copy this Apple Music URL: " + url + "\n");
        logger.accept("3. Go to the album page on Apple Music in your browser\n");
        logger.accept("4. Copy the ALBUM NAME and ARTIST NAME\n");
        logger.accept("5. Search for it on YouTube Music\n");
        logger.accept("6. Copy the YouTube Music URL\n");
        logger.accept("7. Come back here and paste the YouTube Music URL\n\n");
        logger.accept("\ud83d\udca1 TIP: Look for albums with the same track count and order\n\n");
        logger.accept("\ud83c\udfaf Or try this direct search (copy and paste in YouTube Music):\n");
        String searchSuggestion = this.getAppleMusicSearchSuggestion(url);
        logger.accept("   Search for: \"" + searchSuggestion + "\"\n\n");
        logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        logger.accept("Once you have the YouTube Music URL, select 'YouTube Music'\n");
        logger.accept("as the source and paste the URL to download!\n\n");
        throw new Exception("Please find this album on YouTube Music first, then paste that URL here.");
    }

    private String getAppleMusicSearchSuggestion(String url) {
        try {
            String albumPath;
            int idIndex;
            int albumIndex;
            if (url.contains("/album/") && (albumIndex = url.indexOf("/album/")) != -1 && (idIndex = (albumPath = url.substring(albumIndex + 7)).indexOf("/")) != -1) {
                String albumSlug = albumPath.substring(0, idIndex);
                String readableName = albumSlug.replace("-", " ");
                CharSequence[] words = readableName.split(" ");
                for (int i = 0; i < words.length; ++i) {
                    if (words[i].length() <= 0) continue;
                    words[i] = ((String)words[i]).substring(0, 1).toUpperCase() + ((String)words[i]).substring(1).toLowerCase();
                }
                return String.join((CharSequence)" ", words);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return "album music";
    }

    private String spotdlFormat(String format) {
        if (format.equals("vorbis")) {
            return "ogg";
        }
        if (format.equals("alac")) {
            throw new IllegalArgumentException("SpotDL does not support ALAC output");
        }
        return format;
    }

    public void downloadFromGenericSource(String url, boolean isPlaylist, String format, String quality, String sourceName, Consumer<String> logger) throws Exception {
        String template;
        logger.accept("DEBUG: Starting " + sourceName + " download\n");
        logger.accept("DEBUG: Output folder: " + this.outputFolder + "\n");
        logger.accept("DEBUG: Is playlist/album: " + isPlaylist + "\n");
        logger.accept("DEBUG: Track numbers enabled: " + this.addTrackNumbers + "\n");
        if (isPlaylist) {
            template = this.addTrackNumbers ? this.outputFolder + File.separator + "%(playlist_title,album)s" + File.separator + "%(playlist_index,track_number)03d - %(title,track)s.%(ext)s" : this.outputFolder + File.separator + "%(playlist_title,album)s" + File.separator + "%(title,track)s.%(ext)s";
            logger.accept("Creating folder for " + (isPlaylist ? "playlist/album" : "track") + "...\n");
            logger.accept("DEBUG: Template: " + (String)template + "\n");
        } else {
            template = this.outputFolder + File.separator + "%(title,track)s.%(ext)s";
            logger.accept("DEBUG: Single track template: " + template + "\n");
        }
        ArrayList<String> command = new ArrayList<String>();
        command.addAll(ToolLocator.ytDlpCommand());
        this.addYtDlpRuntimeOptions(command);
        this.addYtDlpSafetyOptions(command, isPlaylist);
        command.add("-x");
        command.add("--audio-format");
        command.add(format);
        command.add("--audio-quality");
        command.add(quality);
        command.add("--socket-timeout");
        command.add("30");
        command.add("--retries");
        command.add("10");
        command.add("--fragment-retries");
        command.add("10");
        command.add("--embed-metadata");
        if (!format.equals("wav")) {
            command.add("--embed-thumbnail");
            command.add("--convert-thumbnails");
            command.add("jpg");
        }
        command.add("--progress");
        command.add("--newline");
        if (!isPlaylist) {
            command.add("--no-playlist");
        }
        command.add("-o");
        command.add(template);
        logger.accept("DEBUG: Full command: " + String.join((CharSequence)" ", command) + "\n");
        command.add("--parse-metadata");
        command.add("%(title)s:%(meta_title)s");
        command.add("--parse-metadata");
        command.add("%(uploader)s:%(meta_artist)s");
        command.add(url);
        ProcessBuilder pb = new ProcessBuilder(command);
        this.executeProcess(pb, logger);
        if (isPlaylist) {
            this.postProcessPlaylistFolder(logger);
        }
    }

    private void postProcessAlbumFolder(Consumer<String> logger) {
        if (logger == null) {
            return;
        }
        try {
            if (this.settings == null || !this.settings.isCustomFolderIconsEnabled()) {
                return;
            }
            logger.accept("Post-processing: Searching for album folder...\n");
            File outputDir = new File(this.outputFolder);
            if (!outputDir.exists() || !outputDir.isDirectory()) {
                logger.accept("Post-processing warning: Output directory does not exist\n");
                return;
            }
            File[] subdirs = outputDir.listFiles(File::isDirectory);
            if (subdirs != null && subdirs.length > 0) {
                File newestDir = null;
                long newestTime = 0L;
                for (File dir : subdirs) {
                    if (dir.lastModified() <= newestTime) continue;
                    newestTime = dir.lastModified();
                    newestDir = dir;
                }
                if (newestDir != null) {
                    logger.accept("Post-processing: Found album folder: " + newestDir.getName() + "\n");
                    this.applyFolderIcon(newestDir, logger);
                }
            }
        }
        catch (Exception e) {
            logger.accept("Post-processing warning: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void postProcessPlaylistFolder(Consumer<String> logger) {
        if (logger == null) {
            return;
        }
        try {
            logger.accept("Post-processing: Searching for playlist folder...\n");
            File outputDir = new File(this.outputFolder);
            if (!outputDir.exists() || !outputDir.isDirectory()) {
                logger.accept("Post-processing warning: Output directory does not exist\n");
                return;
            }
            File[] subdirs = outputDir.listFiles(File::isDirectory);
            if (subdirs != null && subdirs.length > 0) {
                File newestDir = null;
                long newestTime = 0L;
                for (File dir : subdirs) {
                    if (dir.lastModified() <= newestTime) continue;
                    newestTime = dir.lastModified();
                    newestDir = dir;
                }
                if (newestDir != null) {
                    logger.accept("Post-processing: Found folder: " + newestDir.getName() + "\n");
                    String folderName = newestDir.getName();
                    if (folderName.startsWith("Album - ")) {
                        String cleanName = folderName.substring(8);
                        File renamedDir = new File(outputDir, cleanName);
                        if (newestDir.renameTo(renamedDir)) {
                            logger.accept("Post-processing: Renamed folder to: " + cleanName + "\n");
                            newestDir = renamedDir;
                        } else {
                            logger.accept("Post-processing warning: Could not rename folder\n");
                        }
                    }
                    if (this.settings != null && this.settings.isCustomFolderIconsEnabled()) {
                        this.applyFolderIcon(newestDir, logger);
                    }
                }
            }
        }
        catch (Exception e) {
            logger.accept("Post-processing warning: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void applyFolderIcon(File folder, Consumer<String> logger) {
        try {
            File[] cacheFiles;
            File cacheDir;
            String name;
            File[] files = folder.listFiles();
            if (files == null || files.length == 0) {
                logger.accept("Post-processing: Folder is empty\n");
                return;
            }
            logger.accept("Post-processing: Scanning " + files.length + " files for cover art...\n");
            File coverImage = null;
            for (File file : files) {
                name = file.getName().toLowerCase();
                logger.accept("Post-processing: Checking file: " + file.getName() + "\n");
                if (!name.equals("cover.jpg") && !name.equals("cover.jpeg") && !name.equals("folder.jpg") && !name.equals("album.jpg") && !name.equals("cover.png") && !name.equals("album.png") && (!name.contains("cover") || !name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png"))) continue;
                coverImage = file;
                logger.accept("Post-processing: Found cover by name: " + file.getName() + "\n");
                break;
            }
            if (coverImage == null) {
                for (File file : files) {
                    name = file.getName().toLowerCase();
                    if (!name.endsWith(".jpg") && !name.endsWith(".jpeg") && !name.endsWith(".png") && !name.endsWith(".webp")) continue;
                    coverImage = file;
                    logger.accept("Post-processing: Found image file: " + file.getName() + "\n");
                    break;
                }
            }
            if (coverImage == null && (cacheDir = new File(folder, ".spotdl-cache")).exists() && cacheDir.isDirectory() && (cacheFiles = cacheDir.listFiles()) != null) {
                for (File file : cacheFiles) {
                    String name2 = file.getName().toLowerCase();
                    if (!name2.endsWith(".jpg") && !name2.endsWith(".jpeg") && !name2.endsWith(".png")) continue;
                    coverImage = file;
                    logger.accept("Post-processing: Found cover in cache: " + file.getName() + "\n");
                    break;
                }
            }
            if (coverImage != null && coverImage.exists() && coverImage.length() > 0L) {
                logger.accept("Post-processing: Applying folder icon from: " + coverImage.getName() + " (" + coverImage.length() + " bytes)\n");
                FolderIconManager.setFolderIconFromFile(folder.getAbsolutePath(), coverImage);
                logger.accept("Post-processing: Folder icon applied successfully!\n");
            } else {
                logger.accept("Post-processing: No valid cover image found in folder\n");
                logger.accept("Post-processing: Tip - Make sure spotdl is configured to download covers\n");
            }
        }
        catch (Exception e) {
            logger.accept("Post-processing: Failed to apply folder icon: " + e.getMessage() + "\n");
            e.printStackTrace();
        }
    }

    private void installZotify(Consumer<String> logger) throws Exception {
        logger.accept("Installing Zotify for direct Spotify downloads...\n");
        ArrayList<String> installCommand = new ArrayList<String>();
        installCommand.add(DownloadManager.pipCommand());
        installCommand.add("-m");
        installCommand.add("pip");
        installCommand.add("install");
        installCommand.add("git+https://github.com/zotify-dev/zotify.git");
        ProcessBuilder pb = new ProcessBuilder(installCommand);
        try {
            this.executeProcess(pb, logger);
            logger.accept("\u2705 Zotify installed successfully!\n");
        }
        catch (Exception e) {
            logger.accept("\u26a0\ufe0f Failed to install Zotify automatically. Please install manually:\n");
            logger.accept("pip install git+https://github.com/zotify-dev/zotify.git\n");
            throw e;
        }
    }

    private void addFfmpegLocation(ArrayList<String> command) {
        String ffmpegLocation = ToolLocator.ffmpegLocation();
        if (ffmpegLocation != null) {
            command.add("--ffmpeg-location");
            command.add(ffmpegLocation);
        }
    }

    private void addYtDlpRuntimeOptions(ArrayList<String> command) {
        this.addFfmpegLocation(command);
        String jsRuntime = ToolLocator.jsRuntime();
        if (jsRuntime != null) {
            command.add("--js-runtimes");
            command.add(jsRuntime);
        }
    }

    private void addYtDlpSafetyOptions(ArrayList<String> command, boolean isCollection) {
        command.add("--continue");
        command.add("--no-overwrites");
        command.add("--windows-filenames");
        command.add("--download-archive");
        command.add(new File(this.outputFolder, ".nekuloader-download-archive.txt").getAbsolutePath());
        if (isCollection) {
            command.add("--ignore-errors");
        }
    }

    private void executeProcess(ProcessBuilder pb, Consumer<String> logger) throws Exception {
        pb.redirectErrorStream(true);
        this.isUserStopped = false;
        this.estimator.reset();
        try {
            this.currentProcess = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(this.currentProcess.getInputStream()));){
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.accept(line + "\n");
                    this.estimator.processLine(line);
                }
            }
            int exitCode = this.currentProcess.waitFor();
            if (this.isUserStopped) {
                throw new Exception("Process stopped by user");
            }
            if (exitCode == 143 || exitCode == -1) {
                throw new Exception("Process stopped by user");
            }
            if (exitCode != 0) {
                throw new Exception("Download failed with exit code: " + exitCode);
            }
        }
        catch (IOException e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("CreateProcess error=2") || errorMsg.contains("No such file or directory") || errorMsg.contains("cannot run program"))) {
                throw new Exception("Python or required tools not found!\n\nPlease install:\n1. Python: https://www.python.org/downloads/\n2. Run: pip install yt-dlp spotdl deemix\n3. Run: pip install git+https://github.com/zotify-dev/zotify.git\n4. Install FFmpeg: https://ffmpeg.org/\n\nMake sure to add Python to PATH during installation!\n\nFor direct downloads:\n- Deezer: deemix (works with free account)\n- Spotify: zotify (requires premium) or spotdl (free via YouTube Music)\n- Apple Music: spotdl (free via YouTube Music)");
            }
            throw e;
        }
        finally {
            this.currentProcess = null;
        }
    }
}

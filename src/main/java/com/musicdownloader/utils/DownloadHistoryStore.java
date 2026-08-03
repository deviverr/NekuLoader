package com.musicdownloader.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class DownloadHistoryStore {
    private static final int MAX_ENTRIES = 500;
    private final Path path;

    public DownloadHistoryStore() {
        this(Path.of(System.getProperty("user.home"), ".pixel-music-downloader", "download-history.log"));
    }

    public DownloadHistoryStore(Path path) {
        this.path = path;
    }

    public synchronized List<String> load() {
        if (!Files.isRegularFile(this.path)) {
            return new ArrayList<>();
        }
        try {
            List<String> entries = Files.readAllLines(this.path, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.isBlank())
                    .toList();
            int first = Math.max(0, entries.size() - MAX_ENTRIES);
            return new ArrayList<>(entries.subList(first, entries.size()));
        }
        catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public synchronized void append(String entry) {
        if (entry == null || entry.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(this.path.getParent());
            Files.writeString(this.path, entry.replace('\n', ' ') + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            this.trimIfNeeded();
        }
        catch (IOException ignored) {
        }
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(this.path);
        }
        catch (IOException ignored) {
        }
    }

    private void trimIfNeeded() throws IOException {
        List<String> entries = Files.readAllLines(this.path, StandardCharsets.UTF_8);
        if (entries.size() <= MAX_ENTRIES) {
            return;
        }
        Files.write(this.path, entries.subList(entries.size() - MAX_ENTRIES, entries.size()), StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING);
    }
}

package com.musicdownloader.utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DownloadHistoryStoreTest {
    private DownloadHistoryStoreTest() {
    }

    public static void main(String[] args) throws Exception {
        Path path = Files.createTempDirectory("nekuloader-history-test").resolve("history.log");
        DownloadHistoryStore store = new DownloadHistoryStore(path);
        require(store.load().isEmpty(), "new history is empty");
        store.append("first download");
        store.append("second\n download");
        require(store.load().equals(List.of("first download", "second  download")), "history round trip");
        store.clear();
        require(store.load().isEmpty(), "clear removes persisted history");
        System.out.println("Download history store tests passed");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}

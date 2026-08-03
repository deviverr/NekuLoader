package com.musicdownloader.downloader;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DeviceTransferManagerTest {
    private DeviceTransferManagerTest() {
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("nekuloader-adb-test");
        Path album = Files.createDirectories(root.resolve("Album"));
        Path track = album.resolve("track.mp3");
        Files.writeString(track, "audio fixture");

        DeviceTransferManager manager = new DeviceTransferManager();
        require(manager.isADBAvailable(), "fake ADB is available");
        require(manager.detectAndroidDevices().equals(List.of("test-device")), "authorized device detection");
        require("Neku Test Phone".equals(manager.getAndroidDeviceName("test-device")), "device model lookup");

        int transferred = manager.transferToAndroidPreservingFolders(
                List.of(track.toFile()),
                root,
                "/sdcard/Music/NekuLoader",
                "test-device");
        require(transferred == 1, "one file transferred");

        String log = Files.readString(Path.of(System.getenv("FAKE_ADB_LOG")));
        require(log.contains("push " + track.toFile().getAbsolutePath() + " /sdcard/Music/NekuLoader/Album/track.mp3"), "album folder preserved");
        require(log.contains("android.intent.action.MEDIA_SCANNER_SCAN_FILE"), "media scan requested");
        System.out.println("Android transfer integration test passed");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}

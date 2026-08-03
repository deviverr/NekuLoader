/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;

public class AndroidDeviceHelper {
    public static boolean isMTPPath(String path) {
        if (path == null) {
            return false;
        }
        return (path = path.toLowerCase()).contains("this pc\\") || path.contains("computer\\") || path.contains("internal storage") || path.contains("sdcard") || path.matches(".*[a-z]:\\\\[^\\\\]+\\\\internal.*") || path.matches(".*[a-z]:\\\\[^\\\\]+\\\\card.*");
    }

    public static String convertMTPPath(String mtpPath) {
        return null;
    }

    public static String getAndroidDownloadInstructions() {
        return "\u26a0\ufe0f ANDROID DEVICE DOWNLOAD LIMITATION \u26a0\ufe0f\n\nUnfortunately, downloading directly to Android devices via USB is not supported\ndue to Windows MTP (Media Transfer Protocol) limitations.\n\nWHY THIS DOESN'T WORK:\n\u2022 Python and Java cannot write to MTP devices like normal folders\n\u2022 Only Windows Explorer has special MTP drivers\n\u2022 yt-dlp and other tools cannot access these paths\n\n\u2705 RECOMMENDED SOLUTIONS:\n\n1. DOWNLOAD TO PC FIRST (Easiest)\n   \u2022 Download to: " + System.getProperty("user.home") + "\\Music\\Downloads\n   \u2022 Then transfer files using Windows Explorer\n\n2. USE ADB (Advanced Users)\n   \u2022 Enable USB Debugging on your phone\n   \u2022 Install ADB: https://developer.android.com/tools/adb\n   \u2022 Use: adb push <file> /sdcard/Music/\n\n3. USE A FILE TRANSFER APP\n   \u2022 LocalSend: https://localsend.org/\n   \u2022 ShareDrop: https://sharedrop.io/\n   \u2022 WiFi File Transfer apps from Play Store\n\n4. ENABLE USB MASS STORAGE (If Available)\n   \u2022 Some phones support USB Mass Storage mode\n   \u2022 Check Settings > Developer Options > USB Configuration\n   \u2022 This makes the phone appear as a normal drive\n\nWould you like to:\n\u2022 Download to your PC's Music folder instead?\n\u2022 Choose a different location?";
    }

    public static String getDefaultMusicDownloadsFolder() {
        String userHome = System.getProperty("user.home");
        String musicFolder = userHome + File.separator + "Music" + File.separator + "Downloads";
        try {
            Path path = Paths.get(musicFolder, new String[0]);
            if (!Files.exists(path, new LinkOption[0])) {
                Files.createDirectories(path, new FileAttribute[0]);
            }
        }
        catch (Exception e) {
            musicFolder = userHome + File.separator + "Music";
        }
        return musicFolder;
    }

    public static boolean isADBAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("adb", "version");
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0;
        }
        catch (Exception e) {
            return false;
        }
    }

    public static String getADBTransferInstructions(String localFolder) {
        return "\ud83d\udcf1 TRANSFER FILES USING ADB:\n\n1. Enable USB Debugging on your Android device:\n   Settings > About Phone > Tap 'Build Number' 7 times\n   Settings > Developer Options > Enable 'USB Debugging'\n\n2. Connect your device and run:\n   adb devices\n\n3. Transfer the downloaded music:\n   adb push \"" + localFolder + "\\*\" /sdcard/Music/\n\n4. Or transfer specific files:\n   adb push \"song.mp3\" /sdcard/Music/\n\nNeed ADB? Download from:\nhttps://developer.android.com/tools/releases/platform-tools";
    }
}

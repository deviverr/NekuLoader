/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.downloader;

import com.musicdownloader.utils.ToolLocator;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.nio.file.Path;

public class DeviceTransferManager {
    private Consumer<String> logger = System.out::println;
    private Process transferProcess;
    private volatile boolean isTransferStopped = false;

    public void setLogger(Consumer<String> logger) {
        this.logger = logger;
    }

    public boolean isADBAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder(ToolLocator.adb(), "version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            int exitCode = process.waitFor();
            return exitCode == 0 && line != null && line.contains("Android Debug Bridge");
        }
        catch (Exception e) {
            return false;
        }
    }

    public boolean isLibimobiledeviceAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ideviceinfo", "--help");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();
            return exitCode == 0 || exitCode == 1;
        }
        catch (Exception e) {
            return false;
        }
    }

    public List<String> detectAndroidDevices() {
        ArrayList<String> devices = new ArrayList<String>();
        if (!this.isADBAvailable()) {
            return devices;
        }
        try {
            String line;
            ProcessBuilder pb = new ProcessBuilder(ToolLocator.adb(), "devices");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                if ((line = line.trim()).isEmpty() || !line.contains("\tdevice")) continue;
                String deviceId = line.split("\t")[0];
                devices.add(deviceId);
            }
            process.waitFor();
        }
        catch (Exception e) {
            this.logger.accept("Error detecting Android devices: " + e.getMessage() + "\n");
        }
        return devices;
    }

    public List<String> detectIOSDevices() {
        ArrayList<String> devices = new ArrayList<String>();
        if (!this.isLibimobiledeviceAvailable()) {
            return devices;
        }
        try {
            String line;
            ProcessBuilder pb = new ProcessBuilder("idevice_id", "-l");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while ((line = reader.readLine()) != null) {
                if ((line = line.trim()).isEmpty()) continue;
                devices.add(line);
            }
            process.waitFor();
        }
        catch (Exception e) {
            this.logger.accept("Error detecting iOS devices: " + e.getMessage() + "\n");
        }
        return devices;
    }

    public String getAndroidDeviceName(String deviceId) {
        try {
            ProcessBuilder pb = new ProcessBuilder(ToolLocator.adb(), "-s", deviceId, "shell", "getprop", "ro.product.model");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String name = reader.readLine();
            process.waitFor();
            return name != null ? name.trim() : deviceId;
        }
        catch (Exception e) {
            return deviceId;
        }
    }

    public boolean transferToAndroid(String localFilePath, String devicePath, String deviceId) {
        if (!this.isADBAvailable()) {
            this.logger.accept("\u2717 ADB is not available. Please install Android Platform Tools.\n");
            this.logger.accept("Download from: https://developer.android.com/tools/releases/platform-tools\n");
            return false;
        }
        File localFile = new File(localFilePath);
        if (!localFile.exists()) {
            this.logger.accept("\u2717 Local file does not exist: " + localFilePath + "\n");
            return false;
        }
        try {
            String line;
            ArrayList<String> command = new ArrayList<String>();
            command.add(ToolLocator.adb());
            if (deviceId != null && !deviceId.isEmpty()) {
                command.add("-s");
                command.add(deviceId);
            }
            command.add("push");
            command.add(localFilePath);
            command.add(devicePath);
            this.logger.accept("\ud83d\udcf1 Transferring to Android device...\n");
            this.logger.accept("   File: " + localFile.getName() + "\n");
            this.logger.accept("   Size: " + this.formatFileSize(localFile.length()) + "\n");
            this.logger.accept("   Destination: " + devicePath + "\n\n");
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            this.transferProcess = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(this.transferProcess.getInputStream()));
            while ((line = reader.readLine()) != null && !this.isTransferStopped) {
                this.logger.accept(line + "\n");
            }
            int exitCode = this.transferProcess.waitFor();
            if (this.isTransferStopped) {
                this.logger.accept("\n\u26a0\ufe0f Transfer stopped by user\n");
                return false;
            }
            if (exitCode == 0) {
                this.logger.accept("\n\u2713 Transfer completed successfully!\n");
                return true;
            }
            this.logger.accept("\n\u2717 Transfer failed with exit code: " + exitCode + "\n");
            return false;
        }
        catch (Exception e) {
            this.logger.accept("\u2717 Transfer error: " + e.getMessage() + "\n");
            return false;
        }
    }

    public boolean transferToIOS(String localFilePath, String devicePath, String deviceId) {
        if (!this.isLibimobiledeviceAvailable()) {
            this.logger.accept("\u2717 libimobiledevice is not available.\n");
            this.logger.accept("For iOS file transfers, you can use:\n");
            this.logger.accept("\u2022 iTunes / Apple Music\n");
            this.logger.accept("\u2022 Third-party tools like iMazing, 3uTools\n");
            this.logger.accept("\u2022 Cloud services (iCloud, Dropbox, etc.)\n");
            return false;
        }
        File localFile = new File(localFilePath);
        if (!localFile.exists()) {
            this.logger.accept("\u2717 Local file does not exist: " + localFilePath + "\n");
            return false;
        }
        try {
            this.isTransferStopped = false;
            this.logger.accept("\ud83d\udcf1 iOS file transfer is limited.\n");
            this.logger.accept("Consider using:\n");
            this.logger.accept("\u2022 iTunes/Apple Music for syncing\n");
            this.logger.accept("\u2022 iCloud Drive\n");
            this.logger.accept("\u2022 Third-party apps with file sharing enabled\n");
            return false;
        }
        catch (Exception e) {
            this.logger.accept("\u2717 Transfer error: " + e.getMessage() + "\n");
            return false;
        }
    }

    public int transferMultipleFiles(List<String> localFiles, String devicePath, DeviceType deviceType, String deviceId) {
        int successCount = 0;
        int totalFiles = localFiles.size();
        this.isTransferStopped = false;
        if (deviceType == DeviceType.ANDROID && !this.ensureAndroidDirectory(devicePath, deviceId)) {
            return 0;
        }
        this.logger.accept("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        this.logger.accept("\ud83d\udcf1 BATCH TRANSFER TO " + deviceType.name() + "\n");
        this.logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        this.logger.accept("Total files: " + totalFiles + "\n\n");
        for (int i = 0; i < totalFiles && !this.isTransferStopped; ++i) {
            String localFile = localFiles.get(i);
            this.logger.accept("[" + (i + 1) + "/" + totalFiles + "] ");
            boolean success = false;
            if (deviceType == DeviceType.ANDROID) {
                success = this.transferToAndroid(localFile, devicePath + "/" + new File(localFile).getName(), deviceId);
            } else if (deviceType == DeviceType.IOS) {
                success = this.transferToIOS(localFile, devicePath, deviceId);
            }
            if (success) {
                ++successCount;
            }
            this.logger.accept("\n");
        }
        this.logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        this.logger.accept("\u2713 Successfully transferred: " + successCount + "/" + totalFiles + "\n");
        if (successCount < totalFiles) {
            this.logger.accept("\u2717 Failed: " + (totalFiles - successCount) + "\n");
        }
        this.logger.accept("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        return successCount;
    }

    public int transferToAndroidPreservingFolders(List<File> localFiles, Path sourceRoot, String devicePath, String deviceId) {
        this.isTransferStopped = false;
        if (!this.ensureAndroidDirectory(devicePath, deviceId)) {
            return 0;
        }
        int successCount = 0;
        for (int i = 0; i < localFiles.size() && !this.isTransferStopped; ++i) {
            File localFile = localFiles.get(i);
            Path relative = sourceRoot.toAbsolutePath().normalize().relativize(localFile.toPath().toAbsolutePath().normalize());
            String relativeParent = relative.getParent() == null ? "" : relative.getParent().toString().replace(File.separatorChar, '/');
            String remoteDirectory = relativeParent.isEmpty() ? devicePath : devicePath + "/" + relativeParent;
            if (!this.ensureAndroidDirectory(remoteDirectory, deviceId)) {
                continue;
            }
            this.logger.accept("[" + (i + 1) + "/" + localFiles.size() + "] ");
            String remoteFile = remoteDirectory + "/" + localFile.getName();
            if (this.transferToAndroid(localFile.getAbsolutePath(), remoteFile, deviceId)) {
                ++successCount;
                this.scanAndroidMedia(remoteFile, deviceId);
            }
        }
        this.logger.accept("Transferred " + successCount + "/" + localFiles.size() + " file(s) to Android.\n");
        return successCount;
    }

    public boolean ensureAndroidDirectory(String devicePath, String deviceId) {
        if (!this.isADBAvailable()) {
            this.logger.accept("ADB is not available. Install Android Platform Tools first.\n");
            return false;
        }
        try {
            ArrayList<String> command = this.adbCommand(deviceId);
            command.add("shell");
            command.add("mkdir");
            command.add("-p");
            command.add(devicePath);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(15L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                this.logger.accept("Timed out while creating " + devicePath + " on Android.\n");
                return false;
            }
            if (process.exitValue() != 0) {
                this.logger.accept("Could not create Android folder: " + output.trim() + "\n");
                return false;
            }
            return true;
        }
        catch (Exception e) {
            this.logger.accept("Could not create Android folder: " + e.getMessage() + "\n");
            return false;
        }
    }

    private void scanAndroidMedia(String remoteFile, String deviceId) {
        try {
            ArrayList<String> command = this.adbCommand(deviceId);
            command.add("shell");
            command.add("am");
            command.add("broadcast");
            command.add("-a");
            command.add("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
            command.add("-d");
            command.add("file://" + remoteFile);
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.waitFor(10L, TimeUnit.SECONDS);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
        catch (Exception ignored) {
        }
    }

    private ArrayList<String> adbCommand(String deviceId) {
        ArrayList<String> command = new ArrayList<>();
        command.add(ToolLocator.adb());
        if (deviceId != null && !deviceId.isBlank()) {
            command.add("-s");
            command.add(deviceId);
        }
        return command;
    }

    public void stopTransfer() {
        this.isTransferStopped = true;
        if (this.transferProcess != null && this.transferProcess.isAlive()) {
            this.transferProcess.destroy();
            try {
                if (!this.transferProcess.waitFor(5L, TimeUnit.SECONDS)) {
                    this.transferProcess.destroyForcibly();
                }
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                this.transferProcess.destroyForcibly();
            }
        }
    }

    public String getADBInstallInstructions() {
        return "\ud83d\udce5 HOW TO INSTALL ADB (Android Debug Bridge)\n\nWINDOWS:\n1. Download Android Platform Tools:\n   https://developer.android.com/tools/releases/platform-tools\n2. Extract the ZIP file to a folder (e.g., C:\\platform-tools)\n3. Add to PATH:\n   \u2022 Right-click 'This PC' \u2192 Properties \u2192 Advanced System Settings\n   \u2022 Environment Variables \u2192 System Variables \u2192 Path \u2192 Edit\n   \u2022 Add: C:\\platform-tools\n4. Open new Command Prompt and test: adb version\n\nENABLE USB DEBUGGING ON ANDROID:\n1. Go to Settings \u2192 About Phone\n2. Tap 'Build Number' 7 times (enables Developer Options)\n3. Go to Settings \u2192 Developer Options\n4. Enable 'USB Debugging'\n5. Connect device via USB and authorize the computer\n";
    }

    public String getIOSTransferInstructions() {
        return "APPLE DEVICE EXPORT\n\n"
                + "iPhone, iPad, and iPod touch:\n"
                + "- Use Finder (macOS) or Apple Devices/iTunes (Windows) to sync files into Music.\n"
                + "- Or use the Files tab to copy audio into an app that supports File Sharing, such as VLC.\n\n"
                + "iPod classic/nano/shuffle:\n"
                + "- Import the prepared files into Music/iTunes, then sync the device.\n\n"
                + "NekuLoader cannot inject arbitrary local files directly into the Apple Music library.\n";
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 0x100000L) {
            return String.format("%.2f KB", (double)bytes / 1024.0);
        }
        if (bytes < 0x40000000L) {
            return String.format("%.2f MB", (double)bytes / 1048576.0);
        }
        return String.format("%.2f GB", (double)bytes / 1.073741824E9);
    }

    public String getDeviceStatus() {
        StringBuilder status = new StringBuilder();
        status.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        status.append("\ud83d\udcf1 DEVICE TRANSFER STATUS\n");
        status.append("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n\n");
        boolean adbAvailable = this.isADBAvailable();
        status.append("Android (ADB): ");
        if (adbAvailable) {
            status.append("\u2713 Available\n");
            List<String> androidDevices = this.detectAndroidDevices();
            if (androidDevices.isEmpty()) {
                status.append("  \u26a0 No devices connected\n");
            } else {
                status.append("  \u2713 Devices connected: ").append(androidDevices.size()).append("\n");
                for (String deviceId : androidDevices) {
                    String name = this.getAndroidDeviceName(deviceId);
                    status.append("    \u2022 ").append(name).append(" (").append(deviceId).append(")\n");
                }
            }
        } else {
            status.append("\u2717 Not installed\n");
            status.append("  Install from: https://developer.android.com/tools/releases/platform-tools\n");
        }
        status.append("\n");
        boolean iosAvailable = this.isLibimobiledeviceAvailable();
        status.append("iOS (libimobiledevice): ");
        if (iosAvailable) {
            status.append("\u2713 Available\n");
            List<String> iosDevices = this.detectIOSDevices();
            if (iosDevices.isEmpty()) {
                status.append("  \u26a0 No devices connected\n");
            } else {
                status.append("  \u2713 Devices connected: ").append(iosDevices.size()).append("\n");
                for (String deviceId : iosDevices) {
                    status.append("    \u2022 ").append(deviceId).append("\n");
                }
            }
        } else {
            status.append("\u2717 Not installed\n");
            status.append("  Use iTunes/iCloud or third-party tools\n");
        }
        status.append("\n\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\n");
        return status.toString();
    }

    public static enum DeviceType {
        ANDROID,
        IOS,
        UNKNOWN;

    }

    public static enum TransferMethod {
        ADB,
        LIBIMOBILEDEVICE,
        NONE;

    }
}

/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.downloader;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DownloadEstimator {
    private static final Pattern DOWNLOAD_PATTERN = Pattern.compile("\\[download\\]\\s+(\\d+\\.?\\d*)%\\s+of\\s+([\\d\\.]+)(\\w+)\\s+at\\s+([\\d\\.]+)(\\w+)/s\\s+ETA\\s+(\\d+:\\d+)");
    private static final Pattern ITEM_PATTERN = Pattern.compile("\\[download\\]\\s+Downloading\\s+item\\s+(\\d+)\\s+of\\s+(\\d+)");
    private static final Pattern FILE_SIZE_PATTERN = Pattern.compile("\\[download\\]\\s+Destination:.*");
    private int totalItems = 1;
    private int currentItem = 1;
    private double currentProgress = 0.0;
    private double currentSpeed = 0.0;
    private long totalBytes = 0L;
    private long downloadedBytes = 0L;
    private long startTime = 0L;
    private long lastUpdateTime = 0L;
    private double[] speedHistory = new double[10];
    private int speedHistoryIndex = 0;
    private int speedHistoryCount = 0;

    public DownloadEstimator() {
        this.reset();
    }

    public void reset() {
        this.totalItems = 1;
        this.currentItem = 1;
        this.currentProgress = 0.0;
        this.currentSpeed = 0.0;
        this.totalBytes = 0L;
        this.downloadedBytes = 0L;
        this.lastUpdateTime = this.startTime = System.currentTimeMillis();
        this.speedHistoryIndex = 0;
        this.speedHistoryCount = 0;
        for (int i = 0; i < this.speedHistory.length; ++i) {
            this.speedHistory[i] = 0.0;
        }
    }

    public boolean processLine(String line) {
        Matcher downloadMatcher;
        if (line == null || line.trim().isEmpty()) {
            return false;
        }
        boolean updated = false;
        Matcher itemMatcher = ITEM_PATTERN.matcher(line);
        if (itemMatcher.find()) {
            this.currentItem = Integer.parseInt(itemMatcher.group(1));
            this.totalItems = Integer.parseInt(itemMatcher.group(2));
            updated = true;
        }
        if ((downloadMatcher = DOWNLOAD_PATTERN.matcher(line)).find()) {
            try {
                this.currentProgress = Double.parseDouble(downloadMatcher.group(1));
                double size = Double.parseDouble(downloadMatcher.group(2));
                String sizeUnit = downloadMatcher.group(3);
                double speed = Double.parseDouble(downloadMatcher.group(4));
                String speedUnit = downloadMatcher.group(5);
                this.totalBytes = this.convertToBytes(size, sizeUnit);
                this.downloadedBytes = (long)((double)this.totalBytes * this.currentProgress / 100.0);
                this.currentSpeed = this.convertToBytes(speed, speedUnit);
                this.addSpeedToHistory(this.currentSpeed);
                this.lastUpdateTime = System.currentTimeMillis();
                updated = true;
            }
            catch (NumberFormatException numberFormatException) {
                // empty catch block
            }
        }
        return updated;
    }

    private long convertToBytes(double size, String unit) {
        switch (unit = unit.toUpperCase().trim()) {
            case "KIB":
            case "KB": {
                return (long)(size * 1024.0);
            }
            case "MIB":
            case "MB": {
                return (long)(size * 1024.0 * 1024.0);
            }
            case "GIB":
            case "GB": {
                return (long)(size * 1024.0 * 1024.0 * 1024.0);
            }
        }
        return (long)size;
    }

    private void addSpeedToHistory(double speed) {
        this.speedHistory[this.speedHistoryIndex] = speed;
        this.speedHistoryIndex = (this.speedHistoryIndex + 1) % this.speedHistory.length;
        if (this.speedHistoryCount < this.speedHistory.length) {
            ++this.speedHistoryCount;
        }
    }

    private double getAverageSpeed() {
        if (this.speedHistoryCount == 0) {
            return this.currentSpeed;
        }
        double sum = 0.0;
        for (int i = 0; i < this.speedHistoryCount; ++i) {
            sum += this.speedHistory[i];
        }
        return sum / (double)this.speedHistoryCount;
    }

    public long getEstimatedTimeRemaining() {
        if (this.currentSpeed <= 0.0) {
            return -1L;
        }
        long remainingBytesCurrentItem = this.totalBytes - this.downloadedBytes;
        long remainingItems = this.totalItems - this.currentItem;
        double avgSpeed = this.getAverageSpeed();
        if (avgSpeed <= 0.0) {
            avgSpeed = this.currentSpeed;
        }
        long timeForCurrentItem = (long)((double)remainingBytesCurrentItem / avgSpeed);
        long timeForRemainingItems = 0L;
        if (remainingItems > 0L && this.totalBytes > 0L) {
            timeForRemainingItems = (long)((double)(this.totalBytes * remainingItems) / avgSpeed);
        }
        return timeForCurrentItem + timeForRemainingItems;
    }

    public double getCurrentProgress() {
        return this.currentProgress;
    }

    public double getOverallProgress() {
        if (this.totalItems <= 1) {
            return this.currentProgress;
        }
        double completedItems = this.currentItem - 1;
        double currentItemProgress = this.currentProgress / 100.0;
        return (completedItems + currentItemProgress) / (double)this.totalItems * 100.0;
    }

    public double getCurrentSpeed() {
        return this.currentSpeed;
    }

    public String getFormattedSpeed() {
        double avgSpeed = this.getAverageSpeed();
        if (avgSpeed <= 0.0) {
            return "-- B/s";
        }
        if (avgSpeed < 1024.0) {
            return String.format("%.1f B/s", avgSpeed);
        }
        if (avgSpeed < 1048576.0) {
            return String.format("%.1f KB/s", avgSpeed / 1024.0);
        }
        return String.format("%.1f MB/s", avgSpeed / 1048576.0);
    }

    public String getFormattedETA() {
        long eta = this.getEstimatedTimeRemaining();
        if (eta < 0L) {
            return "Calculating...";
        }
        if (eta < 60L) {
            return String.format("%ds", eta);
        }
        if (eta < 3600L) {
            long minutes = eta / 60L;
            long seconds = eta % 60L;
            return String.format("%dm %ds", minutes, seconds);
        }
        long hours = eta / 3600L;
        long minutes = eta % 3600L / 60L;
        return String.format("%dh %dm", hours, minutes);
    }

    public int getCurrentItem() {
        return this.currentItem;
    }

    public int getTotalItems() {
        return this.totalItems;
    }

    public long getElapsedTime() {
        return (System.currentTimeMillis() - this.startTime) / 1000L;
    }

    public String getFormattedElapsedTime() {
        long elapsed = this.getElapsedTime();
        if (elapsed < 60L) {
            return String.format("%ds", elapsed);
        }
        if (elapsed < 3600L) {
            long minutes = elapsed / 60L;
            long seconds = elapsed % 60L;
            return String.format("%dm %ds", minutes, seconds);
        }
        long hours = elapsed / 3600L;
        long minutes = elapsed % 3600L / 60L;
        return String.format("%dh %dm", hours, minutes);
    }

    public boolean hasValidData() {
        return this.currentSpeed > 0.0 && this.totalBytes > 0L;
    }
}

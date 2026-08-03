package com.musicdownloader.player;

import com.musicdownloader.utils.ToolLocator;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.json.JSONObject;

public record TrackMetadata(String title, String artist, String album, double durationSeconds, BufferedImage artwork) {
    public static TrackMetadata read(File file) {
        String title = stripExtension(file.getName());
        String artist = "Unknown artist";
        String album = file.getParentFile() == null ? "Unknown album" : file.getParentFile().getName();
        double duration = 0.0;
        try {
            Process process = new ProcessBuilder(
                    ToolLocator.ffprobe(), "-v", "quiet", "-print_format", "json", "-show_format", file.getAbsolutePath())
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            String json;
            try (InputStream input = process.getInputStream()) {
                json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0) {
                JSONObject format = new JSONObject(json).optJSONObject("format");
                if (format != null) {
                    duration = format.optDouble("duration", 0.0);
                    JSONObject tags = format.optJSONObject("tags");
                    if (tags != null) {
                        title = tag(tags, "title", title);
                        artist = tag(tags, "artist", artist);
                        album = tag(tags, "album", album);
                    }
                }
            } else {
                process.destroyForcibly();
            }
        }
        catch (Exception ignored) {
        }
        return new TrackMetadata(title, artist, album, duration, readArtwork(file));
    }

    private static BufferedImage readArtwork(File file) {
        Process process = null;
        try {
            process = new ProcessBuilder(
                    ToolLocator.ffmpeg(), "-v", "error", "-i", file.getAbsolutePath(),
                    "-map", "0:v:0", "-frames:v", "1", "-f", "image2pipe", "-")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            BufferedImage image;
            try (InputStream input = process.getInputStream()) {
                image = ImageIO.read(input);
            }
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return image;
        }
        catch (Exception ignored) {
            if (process != null) {
                process.destroyForcibly();
            }
            return null;
        }
    }

    private static String tag(JSONObject tags, String key, String fallback) {
        for (String candidate : tags.keySet()) {
            if (candidate.toLowerCase(Locale.ROOT).equals(key)) {
                String value = tags.optString(candidate, "").trim();
                return value.isEmpty() ? fallback : value;
            }
        }
        return fallback;
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}

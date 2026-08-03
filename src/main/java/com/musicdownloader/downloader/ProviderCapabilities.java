package com.musicdownloader.downloader;

public final class ProviderCapabilities {
    private ProviderCapabilities() {
    }

    public static String validationError(String source, String url, String format) {
        if ("Spotify".equals(source) && "alac".equals(format)) {
            return "Spotify matching currently supports MP3, FLAC, OGG Vorbis, Opus, M4A, and WAV. "
                    + "Choose M4A for Apple devices or FLAC for lossless audio.";
        }
        if ("Apple Music".equals(source) && url != null && url.contains("/playlist/")) {
            return "Apple Music playlists require authenticated Apple Music API access, which is not configured. "
                    + "Album and track links work now; playlist support is disabled for this release.";
        }
        return null;
    }

    public static String workflowDescription(String source) {
        return switch (source) {
            case "Spotify" -> "Spotify metadata is matched to audio from SpotDL providers.";
            case "Deezer", "Apple Music" -> source + " metadata is parsed, then each track is matched through SpotDL or YouTube Music.";
            case "YouTube", "YouTube Music", "SoundCloud" -> "Audio is extracted from the supplied " + source + " URL with yt-dlp.";
            default -> "Provider workflow unavailable.";
        };
    }
}

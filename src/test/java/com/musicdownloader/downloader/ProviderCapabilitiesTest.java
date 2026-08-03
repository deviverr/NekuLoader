package com.musicdownloader.downloader;

public final class ProviderCapabilitiesTest {
    private ProviderCapabilitiesTest() {
    }

    public static void main(String[] args) {
        require(ProviderCapabilities.validationError("Spotify", "https://open.spotify.com/track/1", "alac") != null, "Spotify ALAC is gated");
        require(ProviderCapabilities.validationError("Spotify", "https://open.spotify.com/track/1", "flac") == null, "Spotify FLAC is supported");
        require(ProviderCapabilities.validationError("Apple Music", "https://music.apple.com/us/playlist/example/pl.123", "m4a") != null, "Apple playlists are gated");
        require(ProviderCapabilities.validationError("Apple Music", "https://music.apple.com/us/album/example/123", "alac") == null, "Apple albums support ALAC through matching");
        require(ProviderCapabilities.workflowDescription("Deezer").contains("metadata"), "Deezer workflow is honest");
        System.out.println("Provider capability tests passed");
    }

    private static void require(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Failed: " + description);
        }
    }
}

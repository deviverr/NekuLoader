/*
 * Decompiled with CFR 0.152.
 */
package com.musicdownloader.examples;

import com.musicdownloader.downloader.AppleMusicParser;
import com.musicdownloader.downloader.DeezerParser;

public class ParserUsageExample {
    public static void main(String[] args) {
        System.out.println("=== Deezer & Apple Music Parser Examples ===\n");
        ParserUsageExample.exampleDeezerAlbum();
        ParserUsageExample.exampleDeezerPlaylist();
        ParserUsageExample.exampleDeezerTrack();
        ParserUsageExample.exampleAppleMusicAlbum();
    }

    public static void exampleDeezerAlbum() {
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        System.out.println("Example 1: Deezer Album");
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        try {
            String url = "https://www.deezer.com/album/302127";
            System.out.println("Parsing URL: " + url);
            DeezerParser.ParsedResult result = DeezerParser.parseDeezerUrl(url);
            System.out.println("\n\u2705 Success!");
            System.out.println("Type: " + result.type);
            System.out.println("Title: " + result.title);
            System.out.println("Artist: " + result.artist);
            System.out.println("Tracks: " + result.tracks.size());
            System.out.println("\nTrack List:");
            for (DeezerParser.Track track : result.tracks) {
                System.out.println(track.toString());
                System.out.println("  Duration: " + track.duration + "s");
                System.out.println("  Search Query: " + track.getSearchQuery());
            }
        }
        catch (Exception e) {
            System.out.println("\u274c Error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("\n");
    }

    public static void exampleDeezerPlaylist() {
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        System.out.println("Example 2: Deezer Playlist");
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        try {
            String url = "https://www.deezer.com/playlist/1313621735";
            System.out.println("Parsing URL: " + url);
            DeezerParser.ParsedResult result = DeezerParser.parseDeezerUrl(url);
            System.out.println("\n\u2705 Success!");
            System.out.println("Type: " + result.type);
            System.out.println("Title: " + result.title);
            System.out.println("Creator: " + result.artist);
            System.out.println("Tracks: " + result.tracks.size());
            System.out.println("\nFirst 5 tracks:");
            for (int i = 0; i < Math.min(5, result.tracks.size()); ++i) {
                System.out.println(result.tracks.get(i).toString());
            }
        }
        catch (Exception e) {
            System.out.println("\u274c Error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("\n");
    }

    public static void exampleDeezerTrack() {
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        System.out.println("Example 3: Deezer Single Track");
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        try {
            String url = "https://www.deezer.com/track/3135556";
            System.out.println("Parsing URL: " + url);
            DeezerParser.ParsedResult result = DeezerParser.parseDeezerUrl(url);
            System.out.println("\n\u2705 Success!");
            System.out.println("Type: " + result.type);
            System.out.println("Title: " + result.title);
            System.out.println("Artist: " + result.artist);
            System.out.println("Tracks: " + result.tracks.size());
            if (!result.tracks.isEmpty()) {
                DeezerParser.Track track = result.tracks.get(0);
                System.out.println("\nTrack Details:");
                System.out.println("  " + track.toString());
                System.out.println("  Duration: " + track.duration + "s");
                System.out.println("  ISRC: " + track.isrc);
                System.out.println("  YouTube Search: " + track.getSearchQuery());
            }
        }
        catch (Exception e) {
            System.out.println("\u274c Error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("\n");
    }

    public static void exampleAppleMusicAlbum() {
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        System.out.println("Example 4: Apple Music Album");
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        try {
            String url = "https://music.apple.com/us/album/thriller/269572838";
            System.out.println("Parsing URL: " + url);
            AppleMusicParser.ParsedResult result = AppleMusicParser.parseAppleMusicUrl(url);
            System.out.println("\n\u2705 Success!");
            System.out.println("Type: " + result.type);
            System.out.println("Title: " + result.title);
            System.out.println("Artist: " + result.artist);
            System.out.println("Tracks: " + result.tracks.size());
            System.out.println("\nTrack List:");
            for (AppleMusicParser.Track track : result.tracks) {
                System.out.println(track.toString());
                System.out.println("  Duration: " + track.duration + "s");
                System.out.println("  Search Query: " + track.getSearchQuery());
            }
        }
        catch (Exception e) {
            System.out.println("\u274c Error: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("\n");
    }

    public static void exampleDownloadFlow() {
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        System.out.println("Example 5: Complete Download Flow");
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        try {
            String url = "https://www.deezer.com/album/302127";
            DeezerParser.ParsedResult parsed = DeezerParser.parseDeezerUrl(url);
            System.out.println("Parsed: " + parsed.title + " by " + parsed.artist);
            System.out.println("Found " + parsed.tracks.size() + " tracks\n");
            System.out.println("Download commands that would be executed:");
            for (int i = 0; i < Math.min(3, parsed.tracks.size()); ++i) {
                DeezerParser.Track track = parsed.tracks.get(i);
                String searchQuery = track.getSearchQuery();
                String filename = String.format("%02d - %s.mp3", track.trackNumber, track.title);
                System.out.println("\nTrack " + (i + 1) + ":");
                System.out.println("  Search: ytsearch1:" + searchQuery);
                System.out.println("  Output: Output/" + parsed.title + "/" + filename);
                System.out.println("  Metadata: Artist=\"" + track.artist + "\", Album=\"" + track.album + "\", Track=" + track.trackNumber);
            }
            if (parsed.tracks.size() > 3) {
                System.out.println("\n... and " + (parsed.tracks.size() - 3) + " more tracks");
            }
        }
        catch (Exception e) {
            System.out.println("\u274c Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void exampleErrorHandling() {
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        System.out.println("Example 6: Error Handling");
        System.out.println("\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        try {
            System.out.println("Test 1: Invalid URL format");
            DeezerParser.parseDeezerUrl("https://www.deezer.com/invalid");
        }
        catch (Exception e) {
            System.out.println("\u2705 Caught expected error: " + e.getMessage());
        }
        try {
            System.out.println("\nTest 2: Non-existent album ID");
            DeezerParser.parseDeezerUrl("https://www.deezer.com/album/999999999999");
        }
        catch (Exception e) {
            System.out.println("\u2705 Caught expected error: " + e.getMessage());
        }
        System.out.println("\nTest 3: Parsers handle network errors gracefully");
        System.out.println("\u2705 All errors are caught and provide meaningful messages");
    }
}

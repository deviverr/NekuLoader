/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.json.JSONArray
 *  org.json.JSONObject
 */
package com.musicdownloader.downloader;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class AppleMusicParser {
    public static ParsedResult parseAppleMusicUrl(String appleMusicUrl) throws Exception {
        String searchTerm = AppleMusicParser.extractSearchTermFromUrl(appleMusicUrl);
        if (appleMusicUrl.contains("/album/")) {
            if (appleMusicUrl.contains("?i=") || appleMusicUrl.contains("&i=")) {
                return AppleMusicParser.parseTrackFromAlbum(appleMusicUrl, searchTerm);
            }
            return AppleMusicParser.parseAlbum(appleMusicUrl, searchTerm);
        }
        if (appleMusicUrl.contains("/playlist/")) {
            return AppleMusicParser.parsePlaylist(appleMusicUrl, searchTerm);
        }
        throw new IllegalArgumentException("Unsupported Apple Music URL format. Please use album, track, or playlist URLs.");
    }

    private static ParsedResult parseTrackFromAlbum(String url, String searchTerm) throws Exception {
        String trackId = AppleMusicParser.extractTrackId(url);
        if (trackId == null || trackId.isEmpty()) {
            throw new Exception("Could not extract track ID from URL");
        }
        String apiUrl = "https://itunes.apple.com/lookup?id=" + trackId;
        JSONObject json = AppleMusicParser.fetchJson(apiUrl);
        JSONArray results = json.getJSONArray("results");
        if (results.length() == 0) {
            throw new Exception("Track not found in iTunes database");
        }
        ParsedResult result = new ParsedResult();
        result.type = "track";
        JSONObject trackJson = results.getJSONObject(0);
        Track track = new Track();
        track.title = trackJson.getString("trackName");
        track.artist = trackJson.getString("artistName");
        track.album = trackJson.optString("collectionName", "Unknown Album");
        track.trackNumber = trackJson.optInt("trackNumber", 1);
        track.duration = trackJson.optInt("trackTimeMillis", 0) / 1000;
        track.isrc = "";
        result.title = track.title;
        result.artist = track.artist;
        result.tracks.add(track);
        return result;
    }

    private static ParsedResult parseAlbum(String url, String searchTerm) throws Exception {
        JSONArray results;
        String apiUrl;
        String albumId = AppleMusicParser.extractAlbumId(url);
        if (albumId != null && !albumId.isEmpty()) {
            apiUrl = "https://itunes.apple.com/lookup?id=" + albumId + "&entity=song&limit=200";
        } else {
            apiUrl = "https://itunes.apple.com/search?term=" + URLEncoder.encode(searchTerm, "UTF-8") + "&entity=album&limit=1";
            JSONObject searchResult = AppleMusicParser.fetchJson(apiUrl);
            results = searchResult.getJSONArray("results");
            if (results.length() == 0) {
                throw new Exception("Album not found in iTunes database");
            }
            String collectionId = results.getJSONObject(0).getString("collectionId");
            apiUrl = "https://itunes.apple.com/lookup?id=" + collectionId + "&entity=song&limit=200";
        }
        JSONObject json = AppleMusicParser.fetchJson(apiUrl);
        results = json.getJSONArray("results");
        if (results.length() == 0) {
            throw new Exception("No tracks found for this album");
        }
        ParsedResult result = new ParsedResult();
        result.type = "album";
        JSONObject albumInfo = results.getJSONObject(0);
        result.title = albumInfo.optString("collectionName", "Unknown Album");
        result.artist = albumInfo.optString("artistName", "Unknown Artist");
        int trackCount = albumInfo.optInt("trackCount", 0);
        for (int i = 1; i < results.length(); ++i) {
            JSONObject trackJson = results.getJSONObject(i);
            if (!trackJson.getString("wrapperType").equals("track")) continue;
            Track track = new Track();
            track.title = trackJson.getString("trackName");
            track.artist = trackJson.getString("artistName");
            track.album = trackJson.getString("collectionName");
            track.trackNumber = trackJson.optInt("trackNumber", i);
            track.duration = trackJson.optInt("trackTimeMillis", 0) / 1000;
            track.isrc = "";
            result.tracks.add(track);
        }
        if (trackCount > 0 && result.tracks.size() < trackCount) {
            throw new Exception("Warning: Album has " + trackCount + " tracks but iTunes API only returned " + result.tracks.size() + " tracks. iTunes API limit is 200 tracks per request. Some tracks may be missing.");
        }
        return result;
    }

    private static ParsedResult parsePlaylist(String url, String searchTerm) throws Exception {
        throw new Exception("Apple Music playlist parsing requires Apple Music API authentication.\nPlease:\n1. Open the playlist in Apple Music\n2. Note down all the song names\n3. Search for the album containing these songs\n4. Use the album URL instead\n\nAlternatively, find the same playlist on YouTube Music for easier downloading.");
    }

    private static String extractSearchTermFromUrl(String url) {
        try {
            if (url.contains("/album/")) {
                int albumIndex = url.indexOf("/album/");
                String albumPart = url.substring(albumIndex + 7);
                int slashIndex = albumPart.indexOf(47);
                if (slashIndex != -1) {
                    albumPart = albumPart.substring(0, slashIndex);
                }
                return albumPart.replace("-", " ");
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return "";
    }

    private static String extractAlbumId(String url) {
        try {
            int lastSlash = url.lastIndexOf(47);
            if (lastSlash != -1) {
                String idPart = url.substring(lastSlash + 1);
                int queryIndex = idPart.indexOf(63);
                if (queryIndex != -1) {
                    idPart = idPart.substring(0, queryIndex);
                }
                if (idPart.matches("\\d+")) {
                    return idPart;
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private static String extractTrackId(String url) {
        try {
            String pattern = "i=";
            int startIndex = url.indexOf(pattern);
            if (startIndex == -1) {
                pattern = "&i=";
                startIndex = url.indexOf(pattern);
            }
            if (startIndex != -1) {
                String idPart = url.substring(startIndex + pattern.length());
                int endIndex = idPart.indexOf(38);
                if (endIndex != -1) {
                    idPart = idPart.substring(0, endIndex);
                }
                if (idPart.matches("\\d+")) {
                    return idPart;
                }
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private static JSONObject fetchJson(String urlString) throws Exception {
        String inputLine;
        URL url = new URI(urlString).toURL();
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            throw new Exception("iTunes API returned code: " + responseCode);
        }
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder content = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        conn.disconnect();
        return new JSONObject(content.toString());
    }

    public static class ParsedResult {
        public String type;
        public String title;
        public String artist;
        public List<Track> tracks = new ArrayList<Track>();
    }

    public static class Track {
        public String title;
        public String artist;
        public String album;
        public int trackNumber;
        public int duration;
        public String isrc;

        public String getSearchQuery() {
            return this.artist + " - " + this.title;
        }

        public String toString() {
            return String.format("%02d. %s - %s (Album: %s)", this.trackNumber, this.artist, this.title, this.album);
        }
    }
}

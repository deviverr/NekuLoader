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
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class DeezerParser {
    public static ParsedResult parseDeezerUrl(String deezerUrl) throws Exception {
        if (deezerUrl.contains("/track/")) {
            return DeezerParser.parseTrack(deezerUrl);
        }
        if (deezerUrl.contains("/album/")) {
            return DeezerParser.parseAlbum(deezerUrl);
        }
        if (deezerUrl.contains("/playlist/")) {
            return DeezerParser.parsePlaylist(deezerUrl);
        }
        throw new IllegalArgumentException("Unsupported Deezer URL format");
    }

    private static ParsedResult parseTrack(String url) throws Exception {
        String trackId = DeezerParser.extractId(url, "/track/");
        String apiUrl = "https://api.deezer.com/track/" + trackId;
        JSONObject json = DeezerParser.fetchJson(apiUrl);
        ParsedResult result = new ParsedResult();
        result.type = "track";
        Track track = new Track();
        track.title = json.getString("title");
        track.artist = json.getJSONObject("artist").getString("name");
        track.album = json.getJSONObject("album").getString("title");
        track.trackNumber = json.optInt("track_position", 1);
        track.duration = json.optInt("duration", 0);
        track.isrc = json.optString("isrc", "");
        result.title = track.title;
        result.artist = track.artist;
        result.tracks.add(track);
        return result;
    }

    private static ParsedResult parseAlbum(String url) throws Exception {
        String albumId = DeezerParser.extractId(url, "/album/");
        String apiUrl = "https://api.deezer.com/album/" + albumId;
        JSONObject json = DeezerParser.fetchJson(apiUrl);
        ParsedResult result = new ParsedResult();
        result.type = "album";
        result.title = json.getString("title");
        result.artist = json.getJSONObject("artist").getString("name");
        List<JSONObject> tracks = DeezerParser.fetchTrackObjects(json);
        for (int i = 0; i < tracks.size(); ++i) {
            JSONObject trackJson = tracks.get(i);
            Track track = new Track();
            track.title = trackJson.getString("title");
            track.artist = trackJson.getJSONObject("artist").getString("name");
            track.album = result.title;
            track.trackNumber = trackJson.optInt("track_position", i + 1);
            track.duration = trackJson.optInt("duration", 0);
            track.isrc = trackJson.optString("isrc", "");
            result.tracks.add(track);
        }
        return result;
    }

    private static ParsedResult parsePlaylist(String url) throws Exception {
        String playlistId = DeezerParser.extractId(url, "/playlist/");
        String apiUrl = "https://api.deezer.com/playlist/" + playlistId;
        JSONObject json = DeezerParser.fetchJson(apiUrl);
        ParsedResult result = new ParsedResult();
        result.type = "playlist";
        result.title = json.getString("title");
        result.artist = json.getJSONObject("creator").getString("name");
        List<JSONObject> tracks = DeezerParser.fetchTrackObjects(json);
        for (int i = 0; i < tracks.size(); ++i) {
            JSONObject trackJson = tracks.get(i);
            Track track = new Track();
            track.title = trackJson.getString("title");
            track.artist = trackJson.getJSONObject("artist").getString("name");
            track.album = trackJson.getJSONObject("album").getString("title");
            track.trackNumber = i + 1;
            track.duration = trackJson.optInt("duration", 0);
            track.isrc = trackJson.optString("isrc", "");
            result.tracks.add(track);
        }
        return result;
    }

    private static String extractId(String url, String pattern) {
        int startIndex = url.indexOf(pattern);
        if (startIndex == -1) {
            throw new IllegalArgumentException("Invalid URL format");
        }
        String idPart = url.substring(startIndex + pattern.length());
        int endIndex = idPart.indexOf(63);
        if (endIndex != -1) {
            idPart = idPart.substring(0, endIndex);
        }
        if ((endIndex = idPart.indexOf(47)) != -1) {
            idPart = idPart.substring(0, endIndex);
        }
        return idPart;
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
            throw new Exception("Deezer API returned code: " + responseCode);
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

    private static List<JSONObject> fetchTrackObjects(JSONObject root) throws Exception {
        ArrayList<JSONObject> tracks = new ArrayList<JSONObject>();
        JSONObject page = root;
        for (int pageNumber = 0; pageNumber < 100 && page != null; ++pageNumber) {
            Object tracksValue = page.opt("tracks");
            JSONArray data;
            String nextUrl = "";
            if (tracksValue instanceof JSONObject) {
                JSONObject tracksObject = (JSONObject)tracksValue;
                data = tracksObject.optJSONArray("data");
                nextUrl = tracksObject.optString("next", "");
            } else if (tracksValue instanceof JSONArray) {
                data = (JSONArray)tracksValue;
            } else {
                data = null;
            }
            if (data != null) {
                for (int i = 0; i < data.length(); ++i) {
                    Object value = data.opt(i);
                    if (value instanceof JSONObject) {
                        tracks.add((JSONObject)value);
                    }
                }
            }
            if (nextUrl.isEmpty()) {
                break;
            }
            page = DeezerParser.fetchJson(nextUrl);
        }
        if (tracks.isEmpty()) {
            throw new Exception("Deezer response did not contain any tracks");
        }
        return tracks;
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

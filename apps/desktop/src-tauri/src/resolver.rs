//! Catalog metadata for providers that do not serve downloadable audio.
//!
//! Deezer and Apple Music links are resolved to a track list through their
//! public, keyless APIs (the same endpoints legacy 3.1 used). The downloader
//! then searches an accessible audio source for each track and writes the
//! catalog's tags over the result.

use serde_json::Value;
use url::Url;

use crate::models::{MediaKind, Provider};

#[derive(Debug, Clone)]
pub struct CatalogTrack {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub track_number: Option<u32>,
    pub duration_seconds: Option<u64>,
}

#[derive(Debug, Clone)]
pub struct Catalog {
    pub title: String,
    pub artist: String,
    pub kind: MediaKind,
    pub thumbnail: Option<String>,
    pub tracks: Vec<CatalogTrack>,
}

impl CatalogTrack {
    pub fn search_query(&self) -> String {
        format!("{} - {}", self.artist, self.title)
    }
}

const MAX_TRACKS: usize = 2_000;

fn client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(20))
        .user_agent(concat!("NekuLoader/", env!("CARGO_PKG_VERSION")))
        .build()
        .map_err(|error| error.to_string())
}

async fn get_json(client: &reqwest::Client, url: &str) -> Result<Value, String> {
    let response = client
        .get(url)
        .send()
        .await
        .map_err(|error| format!("The catalog could not be reached: {error}"))?;
    if !response.status().is_success() {
        return Err(format!("The catalog answered HTTP {}", response.status()));
    }
    let bytes = response
        .bytes()
        .await
        .map_err(|error| format!("The catalog could not be read: {error}"))?;
    let value: Value = serde_json::from_slice(&bytes)
        .map_err(|error| format!("The catalog returned invalid data: {error}"))?;
    if let Some(error) = value.get("error") {
        let message = error
            .get("message")
            .and_then(Value::as_str)
            .unwrap_or("unknown error");
        return Err(format!("The catalog refused the request: {message}"));
    }
    Ok(value)
}

pub async fn fetch(url: &str, provider: &Provider) -> Result<Catalog, String> {
    let parsed = Url::parse(url).map_err(|error| error.to_string())?;
    match provider {
        Provider::Deezer => deezer(&parsed).await,
        Provider::AppleMusic => apple(&parsed).await,
        Provider::Spotify => spotify(&parsed).await,
        _ => Err("This provider does not use catalog matching".into()),
    }
}

// Spotify's public embed supplies catalog metadata without an API key.
// Audio still comes from an accessible matching source, never its preview URL.
async fn spotify(url: &Url) -> Result<Catalog, String> {
    let segments: Vec<_> = url.path_segments().into_iter().flatten().collect();
    let (index, kind) = segments.iter().enumerate()
        .find(|(_, part)| matches!(**part, "track" | "album" | "playlist"))
        .ok_or("Use a Spotify track, album, or playlist link")?;
    let id = segments.get(index + 1)
        .filter(|id| id.len() == 22 && id.chars().all(|c| c.is_ascii_alphanumeric()))
        .ok_or("The Spotify link has an invalid id")?;
    let mut response = client()?.get(format!("https://open.spotify.com/embed/{kind}/{id}"))
        .send().await.map_err(|error| format!("Spotify metadata could not be reached: {error}"))?
        .error_for_status().map_err(|error| format!("Spotify metadata is unavailable: {error}"))?;
    let mut bytes = Vec::new();
    while let Some(chunk) = response.chunk().await.map_err(|e| e.to_string())? {
        if bytes.len() + chunk.len() > 4 * 1024 * 1024 {
            return Err("Spotify metadata exceeded the size limit".into());
        }
        bytes.extend_from_slice(&chunk);
    }
    spotify_embed(&String::from_utf8_lossy(&bytes))
}

fn spotify_embed(html: &str) -> Result<Catalog, String> {
    let pattern = regex::Regex::new(r#"(?s)<script\b[^>]*\bid="__NEXT_DATA__"[^>]*>(.*?)</script>"#)
        .map_err(|e| e.to_string())?;
    let captures = pattern.captures(html).ok_or("Spotify's public metadata is unavailable; try SpotDL or a direct source link")?;
    let data: Value = serde_json::from_str(&captures[1]).map_err(|e| e.to_string())?;
    let entity = data.pointer("/props/pageProps/state/data/entity")
        .ok_or("Spotify returned no catalog metadata")?;
    let kind = match text(entity, "/type").as_str() {
        "track" => MediaKind::Track,
        "album" => MediaKind::Album,
        // Public embeds may truncate playlists without exposing their full count.
        // Refuse a potentially incomplete fallback rather than report it complete.
        "playlist" => return Err("Spotify playlist fallback cannot verify the complete track list. Use SpotDL or a direct-source playlist link.".into()),
        _ => return Err("This Spotify item is not a music track or album".into()),
    };
    let title = text(entity, "/title");
    let artist = if kind == MediaKind::Track {
        entity.get("artists").and_then(Value::as_array).map(|artists| {
            artists.iter().map(|a| text(a, "/name")).filter(|a| !a.is_empty()).collect::<Vec<_>>().join(", ")
        }).unwrap_or_default()
    } else { text(entity, "/subtitle") };
    let entries = if kind == MediaKind::Track { vec![entity] } else {
        entity.get("trackList").and_then(Value::as_array)
            .ok_or("Spotify returned no album track list")?.iter().collect()
    };
    if entries.len() > MAX_TRACKS { return Err("Spotify album exceeds the track limit".into()); }
    let mut tracks = Vec::new();
    for (index, entry) in entries.iter().enumerate() {
        let track_title = text(entry, "/title");
        let track_artist = if kind == MediaKind::Track { artist.clone() } else { text(entry, "/subtitle") };
        if track_title.is_empty() || track_artist.is_empty() {
            return Err("Spotify returned incomplete track metadata".into());
        }
        tracks.push(CatalogTrack {
            title: track_title, artist: track_artist,
            album: if kind == MediaKind::Album { title.clone() } else { String::new() },
            track_number: (kind == MediaKind::Album).then_some(index as u32 + 1),
            duration_seconds: entry.get("duration").and_then(Value::as_u64).map(|ms| ms / 1000).filter(|s| *s > 0),
        });
    }
    if title.is_empty() || tracks.is_empty() { return Err("Spotify returned an empty catalog".into()); }
    Ok(Catalog { title, artist, kind, tracks,
        thumbnail: entity.pointer("/visualIdentity/image/0/url").and_then(Value::as_str).map(str::to_string),
    })
}

fn id_after(url: &Url, marker: &str) -> Option<String> {
    let segments: Vec<&str> = url.path_segments()?.collect();
    let index = segments.iter().position(|segment| *segment == marker)?;
    segments
        .get(index + 1)
        .filter(|id| !id.is_empty() && id.chars().all(|c| c.is_ascii_digit()))
        .map(|id| id.to_string())
}

fn text(value: &Value, pointer: &str) -> String {
    value
        .pointer(pointer)
        .and_then(Value::as_str)
        .unwrap_or_default()
        .trim()
        .to_string()
}

fn deezer_track(value: &Value, album: Option<&str>) -> Option<CatalogTrack> {
    let title = text(value, "/title");
    let artist = text(value, "/artist/name");
    if title.is_empty() || artist.is_empty() {
        return None;
    }
    Some(CatalogTrack {
        title,
        artist,
        album: album
            .map(str::to_string)
            .unwrap_or_else(|| text(value, "/album/title")),
        track_number: value
            .get("track_position")
            .and_then(Value::as_u64)
            .map(|n| n as u32),
        duration_seconds: value.get("duration").and_then(Value::as_u64),
    })
}

async fn deezer_paged(
    client: &reqwest::Client,
    first: String,
    album: Option<&str>,
) -> Result<Vec<CatalogTrack>, String> {
    let mut tracks = Vec::new();
    let mut next = Some(first);
    while let Some(url) = next.take() {
        let page = get_json(client, &url).await?;
        if let Some(items) = page.get("data").and_then(Value::as_array) {
            tracks.extend(items.iter().filter_map(|item| deezer_track(item, album)));
        }
        if tracks.len() >= MAX_TRACKS {
            tracks.truncate(MAX_TRACKS);
            break;
        }
        // Only follow pagination that stays on the Deezer API host.
        next = page
            .get("next")
            .and_then(Value::as_str)
            .filter(|link| link.starts_with("https://api.deezer.com/"))
            .map(str::to_string);
    }
    Ok(tracks)
}

async fn deezer(url: &Url) -> Result<Catalog, String> {
    let client = client()?;
    if let Some(id) = id_after(url, "track") {
        let value = get_json(&client, &format!("https://api.deezer.com/track/{id}")).await?;
        let track = deezer_track(&value, None).ok_or("Deezer returned an incomplete track")?;
        return Ok(Catalog {
            title: track.title.clone(),
            artist: track.artist.clone(),
            kind: MediaKind::Track,
            thumbnail: Some(text(&value, "/album/cover_xl")).filter(|v| !v.is_empty()),
            tracks: vec![track],
        });
    }
    if let Some(id) = id_after(url, "album") {
        let value = get_json(&client, &format!("https://api.deezer.com/album/{id}")).await?;
        let title = text(&value, "/title");
        let mut tracks = deezer_paged(
            &client,
            format!("https://api.deezer.com/album/{id}/tracks?limit=500"),
            Some(&title),
        )
        .await?;
        for (index, track) in tracks.iter_mut().enumerate() {
            track.track_number.get_or_insert(index as u32 + 1);
        }
        return Ok(Catalog {
            artist: text(&value, "/artist/name"),
            thumbnail: Some(text(&value, "/cover_xl")).filter(|v| !v.is_empty()),
            title,
            kind: MediaKind::Album,
            tracks,
        });
    }
    if let Some(id) = id_after(url, "playlist") {
        let value = get_json(&client, &format!("https://api.deezer.com/playlist/{id}")).await?;
        let tracks = deezer_paged(
            &client,
            format!("https://api.deezer.com/playlist/{id}/tracks?limit=500"),
            None,
        )
        .await?;
        return Ok(Catalog {
            title: text(&value, "/title"),
            artist: text(&value, "/creator/name"),
            kind: MediaKind::Playlist,
            thumbnail: Some(text(&value, "/picture_xl")).filter(|v| !v.is_empty()),
            tracks,
        });
    }
    Err("Use a Deezer track, album, or playlist link".into())
}

fn apple_country(url: &Url) -> String {
    url.path_segments()
        .and_then(|mut segments| segments.next())
        .filter(|segment| segment.len() == 2 && segment.chars().all(|c| c.is_ascii_alphabetic()))
        .unwrap_or("us")
        .to_ascii_lowercase()
}

fn apple_track(value: &Value) -> Option<CatalogTrack> {
    if value.get("wrapperType").and_then(Value::as_str) != Some("track") {
        return None;
    }
    Some(CatalogTrack {
        title: text(value, "/trackName"),
        artist: text(value, "/artistName"),
        album: text(value, "/collectionName"),
        track_number: value
            .get("trackNumber")
            .and_then(Value::as_u64)
            .map(|n| n as u32),
        duration_seconds: value
            .get("trackTimeMillis")
            .and_then(Value::as_u64)
            .map(|ms| ms / 1000),
    })
    .filter(|track| !track.title.is_empty() && !track.artist.is_empty())
}

fn artwork(value: &Value) -> Option<String> {
    value
        .get("artworkUrl100")
        .and_then(Value::as_str)
        .map(|url| url.replace("100x100bb", "1000x1000bb"))
}

async fn apple(url: &Url) -> Result<Catalog, String> {
    let client = client()?;
    let country = apple_country(url);
    let segments: Vec<&str> = url.path_segments().map(|s| s.collect()).unwrap_or_default();

    let song_id = url
        .query_pairs()
        .find(|(key, _)| key == "i")
        .map(|(_, value)| value.into_owned())
        .or_else(|| {
            segments
                .iter()
                .position(|segment| *segment == "song")
                .and_then(|_| segments.last().map(|id| id.to_string()))
        })
        .filter(|id| id.chars().all(|c| c.is_ascii_digit()));
    if let Some(id) = song_id {
        let value = get_json(
            &client,
            &format!("https://itunes.apple.com/lookup?id={id}&country={country}"),
        )
        .await?;
        let first = value
            .pointer("/results/0")
            .ok_or("Apple Music did not return this song")?;
        let track = apple_track(first).ok_or("Apple Music returned an incomplete song")?;
        return Ok(Catalog {
            title: track.title.clone(),
            artist: track.artist.clone(),
            kind: MediaKind::Track,
            thumbnail: artwork(first),
            tracks: vec![track],
        });
    }

    if segments.contains(&"album") {
        let id = segments
            .last()
            .filter(|id| id.chars().all(|c| c.is_ascii_digit()))
            .ok_or("This Apple Music album link has no album id")?;
        let value = get_json(
            &client,
            &format!("https://itunes.apple.com/lookup?id={id}&entity=song&limit=200&country={country}"),
        )
        .await?;
        let results = value
            .get("results")
            .and_then(Value::as_array)
            .ok_or("Apple Music did not return this album")?;
        let collection = results
            .iter()
            .find(|item| item.get("wrapperType").and_then(Value::as_str) == Some("collection"))
            .ok_or("Apple Music did not return this album")?;
        return Ok(Catalog {
            title: text(collection, "/collectionName"),
            artist: text(collection, "/artistName"),
            kind: MediaKind::Album,
            thumbnail: artwork(collection),
            tracks: results.iter().filter_map(apple_track).collect(),
        });
    }

    if segments.contains(&"playlist") {
        return Err("Apple Music playlists are not available through Apple's public catalog. Use album or song links.".into());
    }
    Err("Use an Apple Music song or album link".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn embed_fixture(entity: Value) -> String {
        format!(r#"<script id="__NEXT_DATA__" type="application/json">{}</script>"#,
            serde_json::json!({"props":{"pageProps":{"state":{"data":{"entity":entity}}}}}))
    }

    #[test]
    fn spotify_fallback_preserves_album_order_and_duration() {
        let catalog = spotify_embed(&embed_fixture(serde_json::json!({
            "type":"album", "title":"Test album", "subtitle":"Test artist",
            "trackList":[
                {"title":"First", "subtitle":"Test artist", "duration":65000},
                {"title":"Second", "subtitle":"Guest", "duration":200000}
            ]
        }))).unwrap();
        assert_eq!(catalog.kind, MediaKind::Album);
        assert_eq!(catalog.tracks.len(), 2);
        assert_eq!(catalog.tracks[0].duration_seconds, Some(65));
        assert_eq!(catalog.tracks[1].track_number, Some(2));
        assert_eq!(catalog.tracks[1].artist, "Guest");
        assert_eq!(catalog.tracks[1].album, "Test album");
    }

    #[test]
    fn spotify_fallback_handles_tracks_and_rejects_incomplete_collections() {
        let catalog = spotify_embed(&embed_fixture(serde_json::json!({
            "type":"track", "title":"Test song", "artists":[{"name":"One"},{"name":"Two"}],
            "duration":210000
        }))).unwrap();
        assert_eq!(catalog.artist, "One, Two");
        assert_eq!(catalog.tracks[0].duration_seconds, Some(210));
        assert!(spotify_embed("<html>unavailable</html>").is_err());
        assert!(spotify_embed(&embed_fixture(serde_json::json!({"type":"album","title":"Empty","trackList":[]}))).is_err());
        assert!(spotify_embed(&embed_fixture(serde_json::json!({"type":"playlist"}))).unwrap_err().contains("complete track list"));
    }

    #[tokio::test]
    #[ignore = "network; public Spotify catalog metadata only"]
    async fn fetches_live_spotify_metadata() {
        let catalog = fetch("https://open.spotify.com/track/4cOdK2wGLETKBW3PvgPWqT", &Provider::Spotify).await.unwrap();
        assert_eq!(catalog.kind, MediaKind::Track);
        assert_eq!(catalog.tracks.len(), 1);
        assert!(!catalog.artist.is_empty());
        let album = fetch("https://open.spotify.com/album/4LH4d3cOWNNsVw41Gqt2kv", &Provider::Spotify).await.unwrap();
        assert_eq!(album.kind, MediaKind::Album);
        assert_eq!(album.tracks.len(), 10);
    }

    #[test]
    fn extracts_ids_and_country() {
        let url = Url::parse("https://www.deezer.com/en/album/302127").unwrap();
        assert_eq!(id_after(&url, "album").as_deref(), Some("302127"));
        assert_eq!(id_after(&url, "track"), None);
        let apple =
            Url::parse("https://music.apple.com/gb/album/discovery/697194953?i=697195462").unwrap();
        assert_eq!(apple_country(&apple), "gb");
    }

    #[test]
    fn parses_catalog_records() {
        let deezer: Value = serde_json::json!({
            "title": "One More Time", "duration": 320, "track_position": 1,
            "artist": {"name": "Daft Punk"}, "album": {"title": "Discovery"}
        });
        let track = deezer_track(&deezer, None).unwrap();
        assert_eq!(track.search_query(), "Daft Punk - One More Time");
        assert_eq!(track.album, "Discovery");

        let apple: Value = serde_json::json!({
            "wrapperType": "track", "trackName": "Aerodynamic", "artistName": "Daft Punk",
            "collectionName": "Discovery", "trackNumber": 2, "trackTimeMillis": 212000
        });
        let track = apple_track(&apple).unwrap();
        assert_eq!(track.duration_seconds, Some(212));
        assert_eq!(track.track_number, Some(2));
    }

    #[tokio::test]
    #[ignore = "network"]
    async fn fetches_live_catalogs() {
        let album = fetch("https://www.deezer.com/album/302127", &Provider::Deezer)
            .await
            .unwrap();
        assert!(album.tracks.len() > 5);
        let apple = fetch(
            "https://music.apple.com/us/album/in-between-dreams/1440857781",
            &Provider::AppleMusic,
        )
        .await
        .unwrap();
        assert!(!apple.tracks.is_empty());
    }
}

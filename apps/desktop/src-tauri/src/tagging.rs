//! Embedded audio tag reading.
//!
//! Legacy 3.1 shelled out to `ffprobe` for every track's title/artist/album and
//! to `ffmpeg` to dump cover art. Doing that per file makes a library scan spawn
//! two processes per track, so 3.2 reads tags in-process with `lofty` instead.
//! Anything unreadable falls back to the path heuristics `library::scan` has
//! always used, so an untagged collection still lists exactly as it did before.

use std::{
    fs,
    path::{Path, PathBuf},
};

use lofty::{
    file::{AudioFile, TaggedFileExt},
    prelude::ItemKey,
    probe::Probe,
    tag::Accessor,
};
use sha2::{Digest, Sha256};

#[derive(Debug, Clone, Default)]
pub struct TrackTags {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub track_number: Option<u32>,
    pub duration_seconds: Option<u64>,
    pub has_artwork: bool,
}

/// Reads tags and duration. Returns `None` when the file cannot be parsed at
/// all; individual fields stay `None` when a readable file simply lacks them.
pub fn read_tags(path: &Path) -> Option<TrackTags> {
    let tagged = Probe::open(path).ok()?.read().ok()?;

    let mut tags = TrackTags {
        duration_seconds: Some(tagged.properties().duration().as_secs()),
        ..TrackTags::default()
    };

    // `primary_tag` is the format's native tag; fall back to whatever else the
    // file carries (e.g. an ID3v2 block riding along in a FLAC).
    let Some(tag) = tagged.primary_tag().or_else(|| tagged.first_tag()) else {
        tags.has_artwork = folder_cover(path).is_some();
        return Some(tags);
    };

    // WAV keeps pictures in an ID3v2 chunk beside its primary RIFF INFO tag.
    tags.has_artwork = tagged.tags().iter().any(|tag| tag.picture_count() > 0)
        || folder_cover(path).is_some();
    tags.title = non_empty(tag.title().map(|value| value.to_string()));
    tags.artist = non_empty(tag.artist().map(|value| value.to_string()))
        .or_else(|| non_empty(tag.get_string(&ItemKey::AlbumArtist).map(str::to_string)));
    tags.album = non_empty(tag.album().map(|value| value.to_string()));
    tags.track_number = tag.track();

    Some(tags)
}

/// Metadata a resolver already knows from the catalog (Deezer, Apple Music).
/// Written over whatever the matched source embedded, because a search hit's
/// title is often "Artist - Song (Official Audio)".
#[derive(Debug, Clone, Default)]
pub struct CatalogTags {
    pub title: String,
    pub artist: String,
    pub album: String,
    pub album_artist: String,
    pub track_number: Option<u32>,
}

pub fn write_catalog_tags(path: &Path, tags: &CatalogTags) -> Result<(), String> {
    use lofty::{config::WriteOptions, tag::Tag};

    // Sniff the content: an `.ogg` file may hold Vorbis, Opus, or FLAC.
    let mut tagged = Probe::open(path)
        .and_then(|probe| Ok(probe.guess_file_type()?))
        .and_then(|probe| probe.read())
        .map_err(|error| format!("Cannot read tags: {error}"))?;
    if tagged.primary_tag().is_none() {
        let tag_type = tagged.primary_tag_type();
        tagged.insert_tag(Tag::new(tag_type));
    }
    let tag = tagged
        .primary_tag_mut()
        .ok_or_else(|| "This format cannot hold tags".to_string())?;
    tag.set_title(tags.title.clone());
    tag.set_artist(tags.artist.clone());
    if !tags.album.is_empty() {
        tag.set_album(tags.album.clone());
    }
    if !tags.album_artist.is_empty() {
        tag.insert_text(ItemKey::AlbumArtist, tags.album_artist.clone());
    }
    if let Some(number) = tags.track_number {
        tag.set_track(number);
    }
    tagged
        .save_to_path(path, WriteOptions::default())
        .map_err(|error| format!("Cannot write tags: {error}"))
}

/// Image files players conventionally keep next to an album's tracks.
const FOLDER_COVER_NAMES: &[&str] = &[
    "cover.jpg", "cover.png", "folder.jpg", "folder.png", "front.jpg", "front.png",
    "Cover.jpg", "Folder.jpg", "AlbumArt.jpg",
];

pub fn folder_cover(track: &Path) -> Option<PathBuf> {
    let folder = track.parent()?;
    FOLDER_COVER_NAMES
        .iter()
        .map(|name| folder.join(name))
        .find(|candidate| candidate.is_file())
}

/// Embeds a JPEG as the front cover. WAV, MP3 and AIFF get it in their ID3v2
/// tag (WAV's native RIFF INFO cannot hold pictures); other formats use their
/// own tag.
pub fn embed_cover(path: &Path, jpeg: Vec<u8>) -> Result<(), String> {
    use lofty::{
        config::WriteOptions,
        picture::{MimeType, Picture, PictureType},
        tag::{Tag, TagType},
    };

    let mut tagged = Probe::open(path)
        .and_then(|probe| Ok(probe.guess_file_type()?))
        .and_then(|probe| probe.read())
        .map_err(|error| format!("Cannot read tags: {error}"))?;
    // FLAC technically accepts ID3v2 too, but players only read its native
    // Vorbis pictures, so ID3v2 is reserved for the formats that need it.
    let tag_type = match tagged.file_type() {
        lofty::file::FileType::Wav | lofty::file::FileType::Aiff | lofty::file::FileType::Mpeg => {
            TagType::Id3v2
        }
        _ => tagged.primary_tag_type(),
    };
    // Drop an ID3v2 block an earlier build may have added to non-ID3 formats.
    if tag_type != TagType::Id3v2 && tagged.tag(TagType::Id3v2).is_some() {
        tagged.remove(TagType::Id3v2);
    }
    if tagged.tag(tag_type).is_none() {
        tagged.insert_tag(Tag::new(tag_type));
    }
    let tag = tagged
        .tag_mut(tag_type)
        .ok_or_else(|| "This format cannot hold artwork".to_string())?;
    tag.remove_picture_type(PictureType::CoverFront);
    tag.push_picture(Picture::new_unchecked(
        PictureType::CoverFront,
        Some(MimeType::Jpeg),
        None,
        jpeg,
    ));
    tagged
        .save_to_path(path, WriteOptions::default())
        .map_err(|error| format!("Cannot write artwork: {error}"))
}

fn non_empty(value: Option<String>) -> Option<String> {
    value
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
}

/// Writes the first embedded picture into `cache_dir` and returns its path.
///
/// Cover art is often several megabytes, so it is cached on disk and served
/// over the asset protocol rather than base64'd through the IPC bridge on every
/// track change. The cache key includes the file's mtime so re-downloading a
/// track with new art invalidates the old entry.
pub fn cache_artwork(path: &Path, cache_dir: &Path) -> Option<PathBuf> {
    let modified = fs::metadata(path)
        .and_then(|metadata| metadata.modified())
        .ok()?;
    let stamp = modified
        .duration_since(std::time::UNIX_EPOCH)
        .map(|value| value.as_secs())
        .unwrap_or_default();

    let mut hasher = Sha256::new();
    hasher.update(path.to_string_lossy().as_bytes());
    hasher.update(stamp.to_le_bytes());
    let key = format!("{:x}", hasher.finalize());

    // A hit avoids re-parsing the file entirely.
    for extension in ["jpg", "png"] {
        let candidate = cache_dir.join(format!("{key}.{extension}"));
        if candidate.is_file() {
            return Some(candidate);
        }
    }

    let tagged = Probe::open(path)
        .ok()?
        .guess_file_type()
        .ok()?
        .read()
        .ok();
    let picture = tagged.as_ref().and_then(|tagged| {
        tagged
            .tags()
            .iter()
            .find_map(|tag| tag.pictures().first().cloned())
    });
    let Some(picture) = picture else {
        // Untagged or unpictured files fall back to the album folder's image.
        return folder_cover(path);
    };

    let extension = match picture.mime_type().map(|mime| mime.as_str()) {
        Some("image/png") => "png",
        _ => "jpg",
    };

    fs::create_dir_all(cache_dir).ok()?;
    let destination = cache_dir.join(format!("{key}.{extension}"));
    fs::write(&destination, picture.data()).ok()?;
    Some(destination)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn unreadable_files_yield_no_tags() {
        let path =
            std::env::temp_dir().join(format!("nekuloader-tagging-{}.mp3", uuid::Uuid::new_v4()));
        fs::write(&path, b"definitely not audio").unwrap();

        assert!(read_tags(&path).is_none());

        fs::remove_file(path).unwrap();
    }

    #[test]
    fn blank_tag_values_are_discarded() {
        assert_eq!(non_empty(Some("  ".into())), None);
        assert_eq!(non_empty(Some("  Neku ".into())), Some("Neku".into()));
        assert_eq!(non_empty(None), None);
    }

    #[test]
    fn writes_catalog_tags_to_real_audio() {
        // Needs FFmpeg to synthesize fixtures; skipped on machines without it.
        let Ok(status) = std::process::Command::new("ffmpeg").arg("-version").output() else {
            return;
        };
        if !status.status.success() {
            return;
        }
        let dir = std::env::temp_dir().join(format!("neku-tags-{}", std::process::id()));
        fs::create_dir_all(&dir).unwrap();
        for extension in ["mp3", "m4a", "flac", "opus", "ogg"] {
            let path = dir.join(format!("tone.{extension}"));
            let mut ffmpeg = std::process::Command::new("ffmpeg");
            ffmpeg.args(["-y", "-loglevel", "error", "-f", "lavfi", "-i", "sine=duration=1"]);
            if extension == "ogg" {
                // Without libvorbis FFmpeg would put FLAC in the Ogg container.
                ffmpeg.args(["-c:a", "vorbis", "-strict", "-2", "-ac", "2"]);
            }
            let made = ffmpeg
                .arg(&path)
                .status()
                .unwrap();
            assert!(made.success(), "ffmpeg could not make {extension}");
            let tags = CatalogTags {
                title: "Тест".into(),
                artist: "Neku".into(),
                album: "Album".into(),
                album_artist: "Neku".into(),
                track_number: Some(3),
            };
            write_catalog_tags(&path, &tags).unwrap_or_else(|error| panic!("{extension}: {error}"));
            let read = read_tags(&path).unwrap();
            assert_eq!(read.title.as_deref(), Some("Тест"), "{extension}");
            assert_eq!(read.track_number, Some(3), "{extension}");
        }

        let wav = dir.join("tone.wav");
        let cover = dir.join("cover-src.jpg");
        let made = std::process::Command::new("ffmpeg")
            .args(["-y", "-loglevel", "error", "-f", "lavfi", "-i", "sine=duration=1"])
            .arg(&wav)
            .args(["-f", "lavfi", "-i", "color=red:s=16x16", "-frames:v", "1"])
            .arg(&cover)
            .status()
            .unwrap();
        assert!(made.success());
        embed_cover(&wav, fs::read(&cover).unwrap()).unwrap();
        assert!(read_tags(&wav).unwrap().has_artwork);
        let cached = cache_artwork(&wav, &dir.join("cache")).unwrap();
        assert_eq!(fs::read(cached).unwrap(), fs::read(&cover).unwrap());
        let _ = fs::remove_dir_all(dir);
    }
}

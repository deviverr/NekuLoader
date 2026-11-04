use std::{
    fs,
    path::{Path, PathBuf},
    time::SystemTime,
};

use crate::models::LibraryTrack;

const AUDIO_EXTENSIONS: &[&str] = &[
    "aac", "alac", "flac", "m4a", "mp3", "oga", "ogg", "opus", "wav",
];
const MAX_LIBRARY_FILES: usize = 100_000;

/// Directories that never contain a user's music and cost a lot to walk.
const SKIP_DIRECTORIES: &[&str] = &[
    "node_modules",
    "Library",
    "Applications",
    "System",
    ".git",
    ".cache",
    "AppData",
    "Windows",
    "Program Files",
    "Program Files (x86)",
];

/// Scans every configured root, de-duplicating tracks that appear under more
/// than one of them (a music folder nested inside the download folder, say).
pub fn scan_all(roots: &[PathBuf]) -> Result<Vec<LibraryTrack>, String> {
    let mut seen = std::collections::HashSet::new();
    let mut tracks = Vec::new();
    let mut failures = Vec::new();

    for root in roots {
        match scan(root) {
            Ok(found) => {
                for track in found {
                    if seen.insert(track.path.clone()) {
                        tracks.push(track);
                    }
                }
            }
            // One unreadable root should not lose the others.
            Err(error) => failures.push(error),
        }
    }

    if tracks.is_empty() && !failures.is_empty() {
        return Err(failures.join("; "));
    }

    tracks.sort_by(|left, right| {
        right
            .modified_at
            .cmp(&left.modified_at)
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase()))
    });
    Ok(tracks)
}

pub fn scan(root: &Path) -> Result<Vec<LibraryTrack>, String> {
    if !root.is_dir() {
        return Err(format!("{} is not a folder", root.display()));
    }

    let root = root
        .canonicalize()
        .map_err(|error| format!("Could not open the library folder: {error}"))?;
    let mut pending = vec![root];
    let mut tracks = Vec::new();

    while let Some(directory) = pending.pop() {
        let entries = fs::read_dir(&directory)
            .map_err(|error| format!("Could not read {}: {error}", directory.display()))?;
        for entry in entries {
            let entry = entry.map_err(|error| error.to_string())?;
            let file_type = entry.file_type().map_err(|error| error.to_string())?;
            if file_type.is_symlink() {
                continue;
            }
            if file_type.is_dir() {
                let name = entry.file_name();
                let name = name.to_string_lossy();
                // Hidden and system directories are skipped so a wide scan does
                // not spend its time in caches and application bundles.
                if name.starts_with('.') || SKIP_DIRECTORIES.contains(&name.as_ref()) {
                    continue;
                }
                pending.push(entry.path());
                continue;
            }
            // macOS writes "._name" AppleDouble companions next to every file
            // on exFAT/FAT drives; they share the audio extension but hold
            // only Finder metadata.
            if !file_type.is_file()
                || entry.file_name().to_string_lossy().starts_with('.')
                || !is_audio_file(&entry.path())
            {
                continue;
            }

            let path = entry.path();
            let metadata = entry.metadata().map_err(|error| error.to_string())?;
            let modified_at = metadata.modified().unwrap_or(SystemTime::UNIX_EPOCH).into();

            // Embedded tags win; the filename and parent folder remain the
            // fallback for untagged files, which is all legacy ever had.
            let tags = crate::tagging::read_tags(&path).unwrap_or_default();

            let title = tags.title.unwrap_or_else(|| {
                path.file_stem()
                    .and_then(|value| value.to_str())
                    .unwrap_or("Untitled track")
                    .to_string()
            });
            let album = tags.album.unwrap_or_else(|| {
                path.parent()
                    .and_then(Path::file_name)
                    .and_then(|value| value.to_str())
                    .unwrap_or_default()
                    .to_string()
            });
            let format = path
                .extension()
                .and_then(|value| value.to_str())
                .unwrap_or_default()
                .to_ascii_uppercase();

            tracks.push(LibraryTrack {
                id: path.to_string_lossy().into_owned(),
                title,
                artist: tags.artist.unwrap_or_default(),
                album,
                path: path.to_string_lossy().into_owned(),
                format,
                size_bytes: metadata.len(),
                modified_at,
                duration_seconds: tags.duration_seconds,
                track_number: tags.track_number,
                has_artwork: tags.has_artwork,
            });
            if tracks.len() >= MAX_LIBRARY_FILES {
                return Err(format!(
                    "The library contains more than {MAX_LIBRARY_FILES} audio files; narrow the output folder"
                ));
            }
        }
    }

    tracks.sort_by(|left, right| {
        right
            .modified_at
            .cmp(&left.modified_at)
            .then_with(|| left.title.to_lowercase().cmp(&right.title.to_lowercase()))
    });
    Ok(tracks)
}

fn is_audio_file(path: &Path) -> bool {
    path.extension()
        .and_then(|value| value.to_str())
        .map(|value| AUDIO_EXTENSIONS.contains(&value.to_ascii_lowercase().as_str()))
        .unwrap_or(false)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn scans_audio_and_ignores_other_files() {
        let root =
            std::env::temp_dir().join(format!("nekuloader-library-{}", uuid::Uuid::new_v4()));
        let album = root.join("Asterism");
        fs::create_dir_all(&album).unwrap();
        fs::write(album.join("01 - Mycelium Eyes.flac"), b"audio fixture").unwrap();
        fs::write(album.join("cover.jpg"), b"image fixture").unwrap();
        fs::write(album.join("._01 - Mycelium Eyes.flac"), b"appledouble").unwrap();

        let tracks = scan(&root).unwrap();
        assert_eq!(tracks.len(), 1);
        assert_eq!(tracks[0].title, "01 - Mycelium Eyes");
        assert_eq!(tracks[0].album, "Asterism");
        assert_eq!(tracks[0].format, "FLAC");

        fs::remove_dir_all(root).unwrap();
    }
}

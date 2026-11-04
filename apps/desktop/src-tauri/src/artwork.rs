//! Repairs cover art on tracks that are already on disk.
//!
//! Downloads made before square cropping carry a 16:9 video frame (or, for
//! WAV, nothing). yt-dlp records the source page in the tags (`purl` /
//! `comment`), so the thumbnail can be fetched again, cropped, and embedded.

use std::{
    path::{Path, PathBuf},
    process::Stdio,
    time::Duration,
};

use lofty::{file::TaggedFileExt, probe::Probe, tag::ItemValue};
use serde::Serialize;
use tauri::{AppHandle, Emitter, Runtime};
use tokio::process::Command;

use crate::{tagging, tooling};

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ArtworkProgress {
    pub completed: usize,
    pub total: usize,
    pub fixed: usize,
    pub file: String,
}

/// The source page yt-dlp wrote into the tags, if any.
pub fn source_url(path: &Path) -> Option<String> {
    let tagged = Probe::open(path).ok()?.guess_file_type().ok()?.read().ok()?;
    tagged.tags().iter().find_map(|tag| {
        tag.items().find_map(|item| match item.value() {
            ItemValue::Text(text) | ItemValue::Locator(text) => extract_source(text),
            _ => None,
        })
    })
}

fn extract_source(text: &str) -> Option<String> {
    text.split_whitespace()
        .find(|word| {
            word.starts_with("https://www.youtube.com/watch?v=")
                || word.starts_with("https://music.youtube.com/watch?v=")
                || word.starts_with("https://youtu.be/")
                || word.starts_with("https://soundcloud.com/")
        })
        .map(str::to_string)
}

pub async fn repair<R: Runtime>(
    app: &AppHandle<R>,
    app_data: &Path,
    tracks: Vec<PathBuf>,
) -> Result<usize, String> {
    let tools = tooling::discover(app_data);
    let yt_dlp = tools
        .yt_dlp
        .clone()
        .ok_or_else(|| "yt-dlp is missing. Open Settings → Components.".to_string())?;
    let ffmpeg_dir = tools
        .ffmpeg
        .as_deref()
        .and_then(Path::parent)
        .map(Path::to_path_buf)
        .ok_or_else(|| "FFmpeg is missing. Open Settings → Components.".to_string())?;
    let staging = app_data.join("artwork-staging");
    std::fs::create_dir_all(&staging).map_err(|error| error.to_string())?;

    let total = tracks.len();
    let mut fixed = 0;
    for (index, track) in tracks.iter().enumerate() {
        let name = track
            .file_name()
            .map(|value| value.to_string_lossy().into_owned())
            .unwrap_or_default();
        if let Some(url) = source_url(track) {
            let target = staging.join(format!("cover-{index}"));
            let mut command = Command::new(&yt_dlp);
            command
                .args([
                    "--ignore-config",
                    "--no-plugin-dirs",
                    "--no-exec",
                    "--no-update",
                    "--no-playlist",
                    "--skip-download",
                    "--write-thumbnail",
                    "--convert-thumbnails",
                    "jpg",
                    "--ppa",
                    "ThumbnailsConvertor+ffmpeg_o:-c:v mjpeg -qmin 1 -qscale:v 2 -vf crop=\"'if(gt(ih,iw),iw,ih)':'if(gt(iw,ih),ih,iw)'\"",
                ])
                .arg("--ffmpeg-location")
                .arg(&ffmpeg_dir)
                .arg("-o")
                .arg(target.with_extension("%(ext)s"))
                .arg("--")
                .arg(&url)
                .stdin(Stdio::null())
                .stdout(Stdio::null())
                .stderr(Stdio::null())
                .kill_on_drop(true);
            let jpeg = target.with_extension("jpg");
            let ran = tokio::time::timeout(Duration::from_secs(60), command.status()).await;
            if matches!(ran, Ok(Ok(status)) if status.success())
                && let Ok(bytes) = std::fs::read(&jpeg)
            {
                let path = track.clone();
                let cover_bytes = bytes.clone();
                let embedded =
                    tokio::task::spawn_blocking(move || tagging::embed_cover(&path, cover_bytes))
                        .await
                        .map_err(|error| error.to_string())?;
                if embedded.is_ok() {
                    fixed += 1;
                    if let Some(folder) = track.parent() {
                        let cover = folder.join("cover.jpg");
                        if !cover.exists() {
                            let _ = std::fs::write(cover, &bytes);
                        }
                    }
                }
            }
            let _ = std::fs::remove_file(&jpeg);
        }
        let _ = app.emit(
            "artwork:repair",
            ArtworkProgress {
                completed: index + 1,
                total,
                fixed,
                file: name,
            },
        );
    }
    Ok(fixed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn finds_the_source_page_in_tag_text() {
        assert_eq!(
            extract_source("https://www.youtube.com/watch?v=abc").as_deref(),
            Some("https://www.youtube.com/watch?v=abc")
        );
        assert_eq!(
            extract_source("Provided to YouTube by X https://youtu.be/xyz more").as_deref(),
            Some("https://youtu.be/xyz")
        );
        assert_eq!(extract_source("Music"), None);
        assert_eq!(extract_source("https://evil.example/watch?v=1"), None);
    }

    /// Repairs real files listed in `NEKULOADER_REPAIR_DIR`. Network.
    #[tokio::test]
    #[ignore = "network; rewrites the files in NEKULOADER_REPAIR_DIR"]
    async fn repairs_real_files() {
        let Some(dir) = std::env::var_os("NEKULOADER_REPAIR_DIR").map(PathBuf::from) else {
            return;
        };
        let app_data = PathBuf::from(std::env::var_os("NEKULOADER_TEST_APP_DATA").unwrap());
        let tracks: Vec<PathBuf> = std::fs::read_dir(&dir)
            .unwrap()
            .flatten()
            .map(|entry| entry.path())
            .filter(|path| matches!(path.extension().and_then(|e| e.to_str()), Some("wav" | "flac")))
            .collect();
        for track in &tracks {
            println!("{} -> {:?}", track.display(), source_url(track));
        }
        let app = tauri::test::mock_app();
        let fixed = repair(app.handle(), &app_data, tracks.clone()).await.unwrap();
        assert_eq!(fixed, tracks.len());
        for track in &tracks {
            assert!(tagging::read_tags(track).unwrap().has_artwork);
        }
    }
}

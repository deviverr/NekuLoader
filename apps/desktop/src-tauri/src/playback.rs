//! Player support.
//!
//! Audio decoding and output happen in the webview (`<audio>` fed through
//! `convertFileSrc`), so this module owns only two things Rust must own:
//! persisting the queue across restarts, and deciding which paths the webview
//! is allowed to load.
//!
//! The asset protocol scope in `tauri.conf.json` is static, but the library
//! folder is user-configurable at runtime, so the scope is deliberately broad
//! and [`authorize_path`] is the real gate — the same posture `provider.rs`
//! takes for URLs.

use std::path::{Path, PathBuf};

/// Confirms `candidate` is a regular file inside one of `roots`, defeating `..`
/// traversal and symlink escapes by comparing canonicalized paths.
///
/// Takes every configured music root rather than just the download folder,
/// because the player can be pointed at folders the user added themselves.
pub fn authorize_path(candidate: &Path, roots: &[PathBuf]) -> Result<PathBuf, String> {
    let resolved = candidate
        .canonicalize()
        .map_err(|_| "That track is no longer on disk".to_string())?;

    if !resolved.is_file() {
        return Err("That path is not a file".into());
    }

    let permitted = roots
        .iter()
        .filter_map(|root| root.canonicalize().ok())
        .any(|root| resolved.starts_with(&root));

    if !permitted {
        return Err("That file is outside your music folders".into());
    }
    Ok(resolved)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn fixture() -> PathBuf {
        let root = std::env::temp_dir().join(format!("nekuloader-playback-{}", uuid::Uuid::new_v4()));
        fs::create_dir_all(root.join("album")).unwrap();
        fs::write(root.join("album").join("track.mp3"), b"fixture").unwrap();
        root
    }

    #[test]
    fn accepts_a_file_inside_the_library() {
        let root = fixture();
        let track = root.join("album").join("track.mp3");
        assert!(authorize_path(&track, &[root.clone()]).is_ok());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_traversal_outside_the_library() {
        let root = fixture();
        let outside = root.join("album").join("..").join("..").join("etc");
        assert!(authorize_path(&outside, &[root.clone()]).is_err());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_directories() {
        let root = fixture();
        let directory = root.join("album");
        assert!(authorize_path(&directory, &[root.clone()]).is_err());
        fs::remove_dir_all(root).unwrap();
    }

    #[test]
    fn rejects_missing_files() {
        let root = fixture();
        let missing = root.join("album").join("nope.mp3");
        assert!(authorize_path(&missing, &[root.clone()]).is_err());
        fs::remove_dir_all(root).unwrap();
    }
}

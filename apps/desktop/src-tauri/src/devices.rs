//! Android transfer over ADB.
//!
//! Legacy 3.1 shipped only instructions for this; 3.2 drives `adb` directly.
//! `adb shell` joins its arguments into one shell string on the device, so every
//! value that reaches a remote shell is validated and single-quoted here.

use std::{
    env,
    path::{Path, PathBuf},
    process::Stdio,
};

use tauri::{AppHandle, Emitter, Runtime};
use tokio::process::Command;

use crate::{
    models::{AndroidDevice, TransferEvent},
    tooling,
};

pub fn find_adb(app_data: &Path) -> Option<PathBuf> {
    if let Some(found) = tooling::find_executable("adb", "NEKULOADER_ADB", app_data) {
        return Some(found);
    }
    let executable = if cfg!(windows) { "adb.exe" } else { "adb" };
    let mut sdk_roots = Vec::<PathBuf>::new();
    for variable in ["ANDROID_HOME", "ANDROID_SDK_ROOT"] {
        if let Some(value) = env::var_os(variable) {
            sdk_roots.push(PathBuf::from(value));
        }
    }
    if let Some(home) = env::var_os("HOME").or_else(|| env::var_os("USERPROFILE")) {
        let home = PathBuf::from(home);
        sdk_roots.push(home.join("Library").join("Android").join("sdk"));
        sdk_roots.push(home.join("Android").join("Sdk"));
    }
    if let Some(local) = env::var_os("LOCALAPPDATA") {
        sdk_roots.push(PathBuf::from(local).join("Android").join("Sdk"));
    }
    sdk_roots
        .into_iter()
        .map(|root| root.join("platform-tools").join(executable))
        .find(|candidate| candidate.is_file())
}

fn adb_command(adb: &Path) -> Command {
    let mut command = Command::new(adb);
    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    #[cfg(windows)]
    command.creation_flags(0x0800_0000);
    command
}

const ADB_MISSING: &str = "ADB was not found. Install Android Platform Tools (or Android Studio) and reconnect the phone.";

pub async fn list_devices(app_data: &Path) -> Result<Vec<AndroidDevice>, String> {
    let adb = find_adb(app_data).ok_or_else(|| ADB_MISSING.to_string())?;
    let output = adb_command(&adb)
        .args(["devices", "-l"])
        .output()
        .await
        .map_err(|error| format!("Could not run adb: {error}"))?;
    if !output.status.success() {
        return Err(last_line(&output.stderr, "adb devices failed"));
    }
    Ok(parse_devices(&String::from_utf8_lossy(&output.stdout)))
}

fn parse_devices(raw: &str) -> Vec<AndroidDevice> {
    raw.lines()
        .skip_while(|line| !line.starts_with("List of devices"))
        .skip(1)
        .filter_map(|line| {
            let mut fields = line.split_whitespace();
            let id = fields.next()?.to_string();
            let state = fields.next()?.to_string();
            let model = fields
                .find_map(|field| field.strip_prefix("model:"))
                .map(|value| value.replace('_', " "))
                .unwrap_or_default();
            Some(AndroidDevice { id, model, state })
        })
        .collect()
}

pub async fn push<R: Runtime>(
    app: &AppHandle<R>,
    app_data: &Path,
    device_id: &str,
    files: &[PathBuf],
    destination: &str,
) -> Result<(), String> {
    let adb = find_adb(app_data).ok_or_else(|| ADB_MISSING.to_string())?;
    push_with_adb(app, &adb, device_id, files, destination).await
}

async fn push_with_adb<R: Runtime>(
    app: &AppHandle<R>, adb: &Path, device_id: &str, files: &[PathBuf], destination: &str,
) -> Result<(), String> {
    validate_device_id(device_id)?;
    let destination = validate_destination(destination)?;
    let mkdir = adb_command(adb)
        .args(["-s", device_id, "shell", "mkdir", "-p", &shell_quote(&destination)])
        .output()
        .await
        .map_err(|error| format!("Could not run adb: {error}"))?;
    if !mkdir.status.success() {
        let detail = last_line(&mkdir.stderr, "Could not create the destination folder");
        if detail.contains("No such file or directory") {
            return Err(format!(
                "The phone's storage is not available yet. Unlock the phone and keep the screen on, then retry. ({detail})"
            ));
        }
        return Err(detail);
    }

    let total = files.len();
    let mut failures = 0;
    for (index, file) in files.iter().enumerate() {
        let name = file
            .file_name()
            .map(|value| value.to_string_lossy().into_owned())
            .unwrap_or_default();
        let remote = format!("{destination}/{name}");
        let result = adb_command(&adb)
            .args(["-s", device_id, "push"])
            .arg(file)
            .arg(&remote)
            .output()
            .await;
        let error = match result {
            Ok(output) if output.status.success() => {
                // Best effort: ask the media scanner to index the new file so
                // music players see it without a reboot.
                let uri = format!("file://{remote}");
                let _ = adb_command(&adb)
                    .args([
                        "-s",
                        device_id,
                        "shell",
                        "am",
                        "broadcast",
                        "-a",
                        "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                        "-d",
                        &shell_quote(&uri),
                    ])
                    .output()
                    .await;
                None
            }
            Ok(output) => Some(last_line(&output.stderr, "adb push failed")),
            Err(error) => Some(format!("Could not run adb: {error}")),
        };
        if error.is_some() {
            failures += 1;
        }
        let _ = app.emit(
            "device:transfer",
            TransferEvent {
                device_id: device_id.to_string(),
                file: name,
                completed: index + 1,
                total,
                error,
            },
        );
    }
    if failures > 0 {
        return Err(format!("{failures} of {total} files could not be copied"));
    }
    Ok(())
}

fn validate_device_id(id: &str) -> Result<(), String> {
    if !id.is_empty()
        && id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | ':' | '-' | '_'))
    {
        Ok(())
    } else {
        Err("Invalid device id".into())
    }
}

fn validate_destination(raw: &str) -> Result<String, String> {
    let trimmed = raw.trim().trim_end_matches('/');
    if !(trimmed.starts_with("/sdcard/") || trimmed.starts_with("/storage/")) {
        return Err("The destination must be inside /sdcard/ or /storage/".into());
    }
    if trimmed.split('/').any(|part| part == "..") {
        return Err("The destination cannot contain '..'".into());
    }
    if trimmed.chars().any(|c| c.is_control() || matches!(c, '\'' | '"' | '`' | '$' | '\\')) {
        return Err("The destination contains unsupported characters".into());
    }
    Ok(trimmed.to_string())
}

/// POSIX single-quoting for the device shell.
fn shell_quote(value: &str) -> String {
    format!("'{}'", value.replace('\'', r"'\''"))
}

fn last_line(bytes: &[u8], fallback: &str) -> String {
    String::from_utf8_lossy(bytes)
        .lines()
        .rev()
        .find(|line| !line.trim().is_empty())
        .map(|line| line.trim().chars().take(300).collect())
        .unwrap_or_else(|| fallback.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(unix)]
    #[tokio::test]
    async fn fake_adb_preserves_paths_and_reports_partial_failure() {
        use std::os::unix::fs::PermissionsExt;
        let root = std::env::temp_dir().join(format!("neku-fake-adb-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&root).unwrap();
        let adb = root.join("fake adb");
        std::fs::write(&adb, r#"#!/bin/sh
printf '%s\n' "$@" >> "$0.args"
if [ "$3" = push ]; then
  case "$4" in *fail.mp3) echo 'copy failed' >&2; exit 1;; esac
fi
exit 0
"#).unwrap();
        std::fs::set_permissions(&adb, std::fs::Permissions::from_mode(0o755)).unwrap();
        let app = tauri::test::mock_app();
        let files = vec![root.join("Кот - it's music.mp3"), root.join("fail.mp3")];
        for file in &files { std::fs::write(file, b"test").unwrap(); }
        let result = push_with_adb(app.handle(), &adb, "test-device", &files, "/sdcard/Music/Neku Loader").await;
        assert!(result.unwrap_err().contains("1 of 2"));
        let args = std::fs::read_to_string(root.join("fake adb.args")).unwrap();
        assert!(args.contains("/sdcard/Music/Neku Loader/Кот - it's music.mp3"));
        assert!(args.contains("android.intent.action.MEDIA_SCANNER_SCAN_FILE"));
        assert!(args.contains(&shell_quote("file:///sdcard/Music/Neku Loader/Кот - it's music.mp3")));
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn parses_adb_device_list() {
        let raw = "List of devices attached\nR58M123 device usb:1-1 product:x model:Galaxy_S21 device:o1s\nemulator-5554 unauthorized\n\n";
        let devices = parse_devices(raw);
        assert_eq!(devices.len(), 2);
        assert_eq!(devices[0].id, "R58M123");
        assert_eq!(devices[0].model, "Galaxy S21");
        assert_eq!(devices[0].state, "device");
        assert_eq!(devices[1].state, "unauthorized");
    }

    #[test]
    fn validates_destination_and_device() {
        assert_eq!(
            validate_destination("/sdcard/Music/Neku Loader/").unwrap(),
            "/sdcard/Music/Neku Loader"
        );
        assert!(validate_destination("/data/local").is_err());
        assert!(validate_destination("/sdcard/../data").is_err());
        assert!(validate_destination("/sdcard/$(reboot)").is_err());
        assert!(validate_device_id("192.168.1.4:5555").is_ok());
        assert!(validate_device_id("abc;reboot").is_err());
        assert_eq!(shell_quote("it's"), r"'it'\''s'");
    }

    /// Pushes real files to the first connected device or emulator.
    #[tokio::test]
    #[ignore = "needs adb and a connected device"]
    async fn pushes_to_a_connected_device() {
        let app_data = std::env::temp_dir().join("neku-adb-test");
        let devices = list_devices(&app_data).await.unwrap();
        let device = devices.iter().find(|d| d.state == "device").expect("a ready device");
        println!("device: {} {}", device.id, device.model);

        let dir = std::env::temp_dir().join(format!("neku-adb-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let files: Vec<PathBuf> = ["Кот - песня.mp3", "it's a test.mp3"]
            .iter()
            .map(|name| {
                let path = dir.join(name);
                std::fs::write(&path, vec![1_u8; 50_000]).unwrap();
                path
            })
            .collect();

        let app = tauri::test::mock_app();
        push(app.handle(), &app_data, &device.id, &files, "/sdcard/Music/NekuLoader Test")
            .await
            .unwrap();

        let adb = find_adb(&app_data).unwrap();
        let listing = adb_command(&adb)
            .args(["-s", &device.id, "shell", "ls", "-l", &shell_quote("/sdcard/Music/NekuLoader Test")])
            .output()
            .await
            .unwrap();
        let listing = String::from_utf8_lossy(&listing.stdout);
        println!("{listing}");
        assert!(listing.contains("Кот - песня.mp3"));
        assert!(listing.contains("it's a test.mp3"));
        let _ = std::fs::remove_dir_all(dir);
    }
}

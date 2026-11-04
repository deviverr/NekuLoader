//! Windows Explorer album icons, enabled explicitly in settings.
#[cfg(windows)]
pub async fn update(root: &std::path::Path, ffmpeg: &std::path::Path) -> Result<(), String> {
    use std::{fs, process::Stdio};
    use tokio::process::Command;
    let mut pending = vec![root.to_path_buf()];
    while let Some(folder) = pending.pop() {
        let Ok(entries) = fs::read_dir(&folder) else { continue };
        for entry in entries.flatten() {
            if entry.file_type().map(|kind| kind.is_dir()).unwrap_or(false) {
                pending.push(entry.path());
            }
        }
        let cover = folder.join("cover.jpg");
        if !cover.is_file() { continue; }
        let ini = folder.join("desktop.ini");
        // Preserve customizations made by the user or another application.
        if ini.exists() { continue; }
        let icon = folder.join(".nekuloader.ico");
        let result = Command::new(ffmpeg)
            .args(["-nostdin", "-loglevel", "error", "-y", "-i"])
            .arg(&cover).args(["-vf", "scale=256:256"]).arg(&icon)
            .stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::piped())
            .creation_flags(0x08000000).kill_on_drop(true)
            .output().await.map_err(|e| e.to_string())?;
        if !result.status.success() { continue; }
        fs::write(&ini, explorer_ini()).map_err(|e| e.to_string())?;
        for (args, path) in [(["+s", "+h"], ini), (["+h", "+h"], icon), (["+s", "+s"], folder)] {
            let status = Command::new("attrib.exe").args(args).arg(path)
                .stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null())
                .creation_flags(0x08000000).kill_on_drop(true)
                .status().await.map_err(|e| e.to_string())?;
            if !status.success() { return Err("Windows could not apply an album folder icon".into()); }
        }
    }
    Ok(())
}

#[cfg(any(windows, test))]
fn explorer_ini() -> Vec<u8> {
    let text = "[.ShellClassInfo]\r\nIconResource=.nekuloader.ico,0\r\n";
    std::iter::once(0xfeffu16).chain(text.encode_utf16()).flat_map(u16::to_le_bytes).collect()
}

#[cfg(test)]
mod tests {
    #[test]
    fn writes_an_explorer_compatible_utf16_icon_reference() {
        let bytes = super::explorer_ini();
        assert_eq!(&bytes[..2], &[0xff, 0xfe]);
        let words = bytes[2..].chunks_exact(2).map(|b| u16::from_le_bytes([b[0], b[1]])).collect::<Vec<_>>();
        assert_eq!(String::from_utf16(&words).unwrap(), "[.ShellClassInfo]\r\nIconResource=.nekuloader.ico,0\r\n");
    }
}

//! Wi-Fi transfer to any phone (iOS or Android) without cables or apps.
//!
//! A short-lived HTTP server on the local network serves only the files the
//! user picked, behind an unguessable token in the URL. The phone scans a QR
//! code, opens the page in its browser, and saves single tracks or one ZIP.
//! The server stops on request or after [`SHARE_LIFETIME`].

use std::{
    io::Write,
    net::{IpAddr, Ipv4Addr, SocketAddr, UdpSocket},
    path::{Path, PathBuf},
    sync::Arc,
    time::Duration,
};

use chrono::{DateTime, Utc};
use qrcode::{QrCode, render::svg};
use serde::Serialize;
use tokio::{
    fs::File,
    io::{AsyncReadExt, AsyncSeekExt, AsyncWriteExt},
    net::{TcpListener, TcpStream},
    sync::Mutex,
};
use tokio_util::sync::CancellationToken;
use uuid::Uuid;

pub const SHARE_LIFETIME: Duration = Duration::from_secs(30 * 60);
const MAX_REQUEST_HEAD: usize = 8 * 1024;

#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ShareInfo {
    pub url: String,
    pub qr_svg: String,
    pub file_count: usize,
    pub total_bytes: u64,
    pub expires_at: DateTime<Utc>,
}

struct SharedFile {
    name: String,
    path: PathBuf,
    size: u64,
}

struct ShareContext {
    token: String,
    files: Vec<SharedFile>,
    zip_dir: PathBuf,
    zip: Mutex<Option<PathBuf>>,
}

#[derive(Default)]
pub struct ShareManager {
    active: Mutex<Option<ActiveShare>>,
}

struct ActiveShare {
    id: String,
    cancel: CancellationToken,
    info: ShareInfo,
}

impl ShareManager {
    pub async fn current(&self) -> Option<ShareInfo> {
        self.active.lock().await.as_ref().map(|share| share.info.clone())
    }

    pub async fn stop(&self) {
        if let Some(share) = self.active.lock().await.take() {
            share.cancel.cancel();
        }
    }

    /// `files` must already be authorized against the music roots.
    pub async fn start(
        self: &Arc<Self>,
        files: Vec<PathBuf>,
        cache_dir: PathBuf,
    ) -> Result<ShareInfo, String> {
        if files.is_empty() {
            return Err("Choose at least one track to share".into());
        }
        self.stop().await;

        let mut shared = Vec::with_capacity(files.len());
        let mut total_bytes = 0;
        for path in files {
            let size = std::fs::metadata(&path)
                .map_err(|error| format!("Cannot read {}: {error}", path.display()))?
                .len();
            total_bytes += size;
            let name = path
                .file_name()
                .map(|value| value.to_string_lossy().into_owned())
                .unwrap_or_else(|| "track".into());
            shared.push(SharedFile { name, path, size });
        }

        let ip = lan_address().ok_or_else(|| {
            "No local network connection was found. Connect this computer to Wi-Fi or Ethernet."
                .to_string()
        })?;
        let listener = TcpListener::bind(SocketAddr::new(IpAddr::V4(Ipv4Addr::UNSPECIFIED), 0))
            .await
            .map_err(|error| format!("Could not open a network port: {error}"))?;
        let port = listener
            .local_addr()
            .map_err(|error| error.to_string())?
            .port();

        let token = Uuid::new_v4().simple().to_string();
        let url = format!("http://{ip}:{port}/{token}/");
        let info = ShareInfo {
            qr_svg: qr_svg(&url)?,
            url,
            file_count: shared.len(),
            total_bytes,
            expires_at: Utc::now() + chrono::Duration::from_std(SHARE_LIFETIME).unwrap_or_default(),
        };

        let context = Arc::new(ShareContext {
            token,
            files: shared,
            zip_dir: cache_dir,
            zip: Mutex::new(None),
        });
        let cancel = CancellationToken::new();
        let share_id = context.token.clone();
        *self.active.lock().await = Some(ActiveShare {
            id: share_id.clone(),
            cancel: cancel.clone(),
            info: info.clone(),
        });

        let manager = Arc::clone(self);
        tokio::spawn(async move {
            let deadline = tokio::time::sleep(SHARE_LIFETIME);
            tokio::pin!(deadline);
            loop {
                tokio::select! {
                    _ = cancel.cancelled() => break,
                    _ = &mut deadline => break,
                    accepted = listener.accept() => {
                        if let Ok((stream, _)) = accepted {
                            let context = Arc::clone(&context);
                            tokio::spawn(async move {
                                let _ = tokio::time::timeout(
                                    Duration::from_secs(60 * 60),
                                    handle(stream, context),
                                )
                                .await;
                            });
                        }
                    }
                }
            }
            if let Some(zip) = context.zip.lock().await.take() {
                let _ = tokio::fs::remove_file(zip).await;
            }
            let mut active = manager.active.lock().await;
            if active.as_ref().is_some_and(|share| share.id == share_id) {
                *active = None;
            }
        });

        Ok(info)
    }
}

/// The address other devices on the LAN reach this machine at. Connecting a
/// UDP socket sends no packets; it only asks the OS which interface it would
/// route through.
fn lan_address() -> Option<IpAddr> {
    let socket = UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("8.8.8.8:80").ok()?;
    let ip = socket.local_addr().ok()?.ip();
    (!ip.is_loopback() && !ip.is_unspecified()).then_some(ip)
}

fn qr_svg(url: &str) -> Result<String, String> {
    let code = QrCode::new(url.as_bytes()).map_err(|error| error.to_string())?;
    Ok(code
        .render::<svg::Color>()
        .min_dimensions(220, 220)
        .quiet_zone(true)
        .build())
}

struct Request {
    path: String,
    range: Option<(u64, Option<u64>)>,
    head_only: bool,
}

async fn read_request(stream: &mut TcpStream) -> Option<Request> {
    let mut buffer = Vec::with_capacity(1024);
    let mut chunk = [0_u8; 1024];
    while !buffer.windows(4).any(|window| window == b"\r\n\r\n") {
        let count = stream.read(&mut chunk).await.ok()?;
        if count == 0 || buffer.len() + count > MAX_REQUEST_HEAD {
            return None;
        }
        buffer.extend_from_slice(&chunk[..count]);
    }
    let head = String::from_utf8_lossy(&buffer);
    let mut lines = head.lines();
    let mut request_line = lines.next()?.split_whitespace();
    let method = request_line.next()?;
    let path = request_line.next()?.to_string();
    let range = lines
        .filter_map(|line| line.split_once(':'))
        .find(|(name, _)| name.trim().eq_ignore_ascii_case("range"))
        .and_then(|(_, value)| parse_range(value.trim()));
    match method {
        "GET" | "HEAD" => Some(Request {
            path,
            range,
            head_only: method == "HEAD",
        }),
        _ => None,
    }
}

fn parse_range(value: &str) -> Option<(u64, Option<u64>)> {
    let spec = value.strip_prefix("bytes=")?;
    let (start, end) = spec.split_once('-')?;
    let start = start.trim().parse().ok()?;
    let end = match end.trim() {
        "" => None,
        value => Some(value.parse().ok()?),
    };
    Some((start, end))
}

async fn handle(mut stream: TcpStream, context: Arc<ShareContext>) {
    let Some(request) = read_request(&mut stream).await else {
        let _ = respond_text(&mut stream, 400, "Bad Request").await;
        return;
    };
    let prefix = format!("/{}/", context.token);
    let Some(rest) = request.path.strip_prefix(&prefix) else {
        let _ = respond_text(&mut stream, 404, "Not Found").await;
        return;
    };

    if rest.is_empty() {
        let page = index_page(&context);
        let _ = respond(
            &mut stream,
            200,
            "text/html; charset=utf-8",
            page.as_bytes(),
            request.head_only,
        )
        .await;
    } else if rest == "all.zip" {
        match ensure_zip(&context).await {
            Ok(zip) => {
                let _ = send_file(&mut stream, &zip, "NekuLoader.zip", &request).await;
            }
            Err(error) => {
                let _ = respond_text(&mut stream, 500, &error).await;
            }
        }
    } else if let Some(index) = rest
        .strip_prefix("f/")
        .and_then(|value| value.parse::<usize>().ok())
        && let Some(file) = context.files.get(index)
    {
        let _ = send_file(&mut stream, &file.path, &file.name, &request).await;
    } else {
        let _ = respond_text(&mut stream, 404, "Not Found").await;
    }
}

async fn ensure_zip(context: &ShareContext) -> Result<PathBuf, String> {
    let mut guard = context.zip.lock().await;
    if let Some(existing) = guard.as_ref().filter(|path| path.is_file()) {
        return Ok(existing.clone());
    }
    std::fs::create_dir_all(&context.zip_dir).map_err(|error| error.to_string())?;
    let target = context.zip_dir.join(format!("share-{}.zip", context.token));
    let entries: Vec<(String, PathBuf)> = context
        .files
        .iter()
        .map(|file| (file.name.clone(), file.path.clone()))
        .collect();
    let output = target.clone();
    tokio::task::spawn_blocking(move || build_zip(&output, &entries))
        .await
        .map_err(|error| error.to_string())??;
    *guard = Some(target.clone());
    Ok(target)
}

fn build_zip(target: &Path, entries: &[(String, PathBuf)]) -> Result<(), String> {
    let file = std::fs::File::create(target).map_err(|error| error.to_string())?;
    let mut writer = zip::ZipWriter::new(std::io::BufWriter::new(file));
    // Audio is already compressed; storing is fast and loses nothing.
    let options = zip::write::SimpleFileOptions::default()
        .compression_method(zip::CompressionMethod::Stored)
        .large_file(true);
    let mut used = std::collections::HashSet::new();
    for (name, path) in entries {
        let mut unique = name.clone();
        let mut counter = 2;
        while !used.insert(unique.clone()) {
            unique = format!("{counter} - {name}");
            counter += 1;
        }
        writer
            .start_file(unique, options)
            .map_err(|error| error.to_string())?;
        let mut source = std::fs::File::open(path).map_err(|error| error.to_string())?;
        std::io::copy(&mut source, &mut writer).map_err(|error| error.to_string())?;
    }
    writer
        .finish()
        .map_err(|error| error.to_string())?
        .flush()
        .map_err(|error| error.to_string())
}

async fn send_file(
    stream: &mut TcpStream,
    path: &Path,
    name: &str,
    request: &Request,
) -> std::io::Result<()> {
    let mut file = File::open(path).await?;
    let size = file.metadata().await?.len();
    let (status, start, end) = match request.range {
        Some((start, end)) if start < size => {
            let end = end.unwrap_or(size - 1).min(size - 1);
            if end < start {
                return respond_text(stream, 416, "Range Not Satisfiable").await;
            }
            (206, start, end)
        }
        Some(_) => return respond_text(stream, 416, "Range Not Satisfiable").await,
        None => (200, 0, size.saturating_sub(1)),
    };
    let length = if size == 0 { 0 } else { end - start + 1 };
    let mut head = format!(
        "HTTP/1.1 {status} {}\r\nContent-Type: {}\r\nContent-Length: {length}\r\nAccept-Ranges: bytes\r\nContent-Disposition: attachment; filename=\"{}\"; filename*=UTF-8''{}\r\nCache-Control: no-store\r\nConnection: close\r\n",
        reason(status),
        content_type(name),
        ascii_fallback(name),
        percent_encode(name),
    );
    if status == 206 {
        head.push_str(&format!("Content-Range: bytes {start}-{end}/{size}\r\n"));
    }
    head.push_str("\r\n");
    stream.write_all(head.as_bytes()).await?;
    if request.head_only || length == 0 {
        return stream.shutdown().await;
    }
    file.seek(std::io::SeekFrom::Start(start)).await?;
    let mut limited = file.take(length);
    tokio::io::copy(&mut limited, stream).await?;
    stream.shutdown().await
}

async fn respond(
    stream: &mut TcpStream,
    status: u16,
    content_type: &str,
    body: &[u8],
    head_only: bool,
) -> std::io::Result<()> {
    let head = format!(
        "HTTP/1.1 {status} {}\r\nContent-Type: {content_type}\r\nContent-Length: {}\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n",
        reason(status),
        body.len()
    );
    stream.write_all(head.as_bytes()).await?;
    if !head_only {
        stream.write_all(body).await?;
    }
    stream.shutdown().await
}

async fn respond_text(stream: &mut TcpStream, status: u16, body: &str) -> std::io::Result<()> {
    respond(stream, status, "text/plain; charset=utf-8", body.as_bytes(), false).await
}

fn reason(status: u16) -> &'static str {
    match status {
        200 => "OK",
        206 => "Partial Content",
        400 => "Bad Request",
        404 => "Not Found",
        416 => "Range Not Satisfiable",
        _ => "Internal Server Error",
    }
}

fn content_type(name: &str) -> &'static str {
    match name.rsplit('.').next().map(str::to_ascii_lowercase).as_deref() {
        Some("mp3") => "audio/mpeg",
        Some("m4a" | "alac") => "audio/mp4",
        Some("flac") => "audio/flac",
        Some("opus" | "ogg") => "audio/ogg",
        Some("wav") => "audio/wav",
        Some("zip") => "application/zip",
        _ => "application/octet-stream",
    }
}

fn ascii_fallback(name: &str) -> String {
    name.chars()
        .map(|c| if c.is_ascii_graphic() && c != '"' && c != '\\' || c == ' ' { c } else { '_' })
        .collect()
}

fn percent_encode(value: &str) -> String {
    value
        .bytes()
        .map(|byte| {
            if byte.is_ascii_alphanumeric() || b"-._~".contains(&byte) {
                (byte as char).to_string()
            } else {
                format!("%{byte:02X}")
            }
        })
        .collect()
}

fn html_escape(value: &str) -> String {
    value
        .replace('&', "&amp;")
        .replace('<', "&lt;")
        .replace('>', "&gt;")
        .replace('"', "&quot;")
        .replace('\'', "&#39;")
}

fn human_size(bytes: u64) -> String {
    let mb = bytes as f64 / (1024.0 * 1024.0);
    if mb >= 1024.0 {
        format!("{:.1} GB", mb / 1024.0)
    } else {
        format!("{mb:.1} MB")
    }
}

fn index_page(context: &ShareContext) -> String {
    let total: u64 = context.files.iter().map(|file| file.size).sum();
    let rows: String = context
        .files
        .iter()
        .enumerate()
        .map(|(index, file)| {
            format!(
                "<li><a href=\"f/{index}\" download>{}</a><span>{}</span></li>",
                html_escape(&file.name),
                human_size(file.size)
            )
        })
        .collect();
    format!(
        r#"<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>NekuLoader transfer</title>
<style>
:root{{color-scheme:light dark;--bg:#f4f4f4;--fg:#111;--muted:#666;--card:#fff;--line:#ddd;--accent:#111}}
@media (prefers-color-scheme:dark){{:root{{--bg:#0d0d0d;--fg:#eee;--muted:#999;--card:#181818;--line:#2a2a2a;--accent:#eee}}}}
body{{margin:0;font:16px/1.4 -apple-system,system-ui,sans-serif;background:var(--bg);color:var(--fg)}}
main{{max-width:640px;margin:0 auto;padding:20px 16px 40px}}
h1{{font-size:22px;margin:0 0 4px}}p{{color:var(--muted);margin:0 0 16px}}
.all{{display:block;text-align:center;padding:14px;border-radius:12px;background:var(--accent);color:var(--bg);text-decoration:none;font-weight:600;margin-bottom:16px}}
ul{{list-style:none;margin:0;padding:0;background:var(--card);border:1px solid var(--line);border-radius:12px;overflow:hidden}}
li{{display:flex;justify-content:space-between;gap:12px;padding:12px 14px;border-top:1px solid var(--line)}}li:first-child{{border-top:0}}
li a{{color:var(--fg);text-decoration:none;word-break:break-word}}li span{{color:var(--muted);white-space:nowrap;font-size:14px}}
small{{display:block;color:var(--muted);margin-top:16px}}
</style></head><body><main>
<h1>NekuLoader</h1><p>{count} tracks · {size}</p>
<a class="all" href="all.zip" download>Download all (ZIP)</a>
<ul>{rows}</ul>
<small>iPhone: files are saved to the Files app → Downloads. Android: files are saved to Downloads. Tap a track to save it on its own.</small>
</main></body></html>"#,
        count = context.files.len(),
        size = human_size(total),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_byte_ranges() {
        assert_eq!(parse_range("bytes=0-"), Some((0, None)));
        assert_eq!(parse_range("bytes=10-99"), Some((10, Some(99))));
        assert_eq!(parse_range("items=1-2"), None);
    }

    #[test]
    fn encodes_unicode_filenames() {
        assert_eq!(percent_encode("Кот 1.mp3"), "%D0%9A%D0%BE%D1%82%201.mp3");
        assert_eq!(ascii_fallback("Кот \"1\".mp3"), "___ _1_.mp3");
        assert_eq!(html_escape("<a&b>"), "&lt;a&amp;b&gt;");
    }

    #[test]
    fn renders_a_qr_svg() {
        assert!(qr_svg("http://192.168.1.2:5000/abc/").unwrap().starts_with("<?xml"));
    }

    #[tokio::test]
    async fn serves_only_tokenized_paths() {
        let dir = std::env::temp_dir().join(format!("neku-share-{}", Uuid::new_v4().simple()));
        std::fs::create_dir_all(&dir).unwrap();
        let track = dir.join("song.mp3");
        std::fs::write(&track, b"0123456789").unwrap();

        let context = Arc::new(ShareContext {
            token: "tok".into(),
            files: vec![SharedFile { name: "song.mp3".into(), path: track, size: 10 }],
            zip_dir: dir.clone(),
            zip: Mutex::new(None),
        });
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let address = listener.local_addr().unwrap();
        let server_context = Arc::clone(&context);
        tokio::spawn(async move {
            for _ in 0..4 {
                let (stream, _) = listener.accept().await.unwrap();
                handle(stream, Arc::clone(&server_context)).await;
            }
        });

        async fn get(address: SocketAddr, raw: &str) -> String {
            let mut stream = TcpStream::connect(address).await.unwrap();
            stream.write_all(raw.as_bytes()).await.unwrap();
            let mut response = Vec::new();
            stream.read_to_end(&mut response).await.unwrap();
            String::from_utf8_lossy(&response).into_owned()
        }

        assert!(get(address, "GET /wrong/ HTTP/1.1\r\n\r\n").await.starts_with("HTTP/1.1 404"));
        let page = get(address, "GET /tok/ HTTP/1.1\r\n\r\n").await;
        assert!(page.contains("song.mp3"));
        let partial = get(address, "GET /tok/f/0 HTTP/1.1\r\nRange: bytes=2-4\r\n\r\n").await;
        assert!(partial.starts_with("HTTP/1.1 206"));
        assert!(partial.ends_with("234"));
        let zip = get(address, "GET /tok/all.zip HTTP/1.1\r\n\r\n").await;
        assert!(zip.starts_with("HTTP/1.1 200"));

        let _ = std::fs::remove_dir_all(dir);
    }

    /// Serves a real file on the LAN address and downloads it back through
    /// that address, the way a phone on the same Wi-Fi would.
    #[tokio::test]
    #[ignore = "binds a LAN port"]
    async fn serves_over_the_lan_address() {
        let dir = std::env::temp_dir().join(format!("neku-lan-{}", Uuid::new_v4().simple()));
        std::fs::create_dir_all(&dir).unwrap();
        let track = dir.join("Кот.mp3");
        std::fs::write(&track, vec![7_u8; 300_000]).unwrap();

        let manager = Arc::new(ShareManager::default());
        let info = manager.start(vec![track], dir.clone()).await.unwrap();
        println!("share url: {}", info.url);
        assert!(!info.url.contains("127.0.0.1"));

        let client = reqwest::Client::new();
        let page = client.get(&info.url).send().await.unwrap().text().await.unwrap();
        assert!(page.contains("Кот.mp3"));
        let file = client.get(format!("{}f/0", info.url)).send().await.unwrap();
        assert_eq!(file.status(), 200);
        assert_eq!(file.bytes().await.unwrap().len(), 300_000);
        let zip = client.get(format!("{}all.zip", info.url)).send().await.unwrap();
        assert_eq!(zip.status(), 200);
        assert!(zip.bytes().await.unwrap().len() > 300_000);
        let wrong = client
            .get(info.url.replace(&info.url.split('/').nth(3).unwrap().to_string(), "nope"))
            .send()
            .await
            .unwrap();
        assert_eq!(wrong.status(), 404);

        manager.stop().await;
        tokio::time::sleep(Duration::from_millis(200)).await;
        assert!(manager.current().await.is_none());
        assert!(client.get(&info.url).send().await.is_err());
        let _ = std::fs::remove_dir_all(dir);
    }
}

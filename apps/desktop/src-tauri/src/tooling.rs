use std::{
    env, fs,
    fs::OpenOptions,
    io::{self, Read, Write},
    path::{Path, PathBuf},
    process::Stdio,
};

use reqwest::{Client, Response, redirect::Policy};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use tokio::{
    fs as async_fs,
    io::AsyncWriteExt,
    process::Command,
    time::{Duration, timeout},
};
use url::Url;
use uuid::Uuid;

use crate::models::ToolInfo;

const GITHUB_API: &str = "https://api.github.com";
const GITHUB_API_VERSION: &str = "2022-11-28";
const USER_AGENT: &str = "NekuLoader/3.2 (+https://github.com/deviverr/NekuLoader)";
const MAX_RELEASE_METADATA_BYTES: u64 = 2 * 1024 * 1024;
const MAX_YT_DLP_BYTES: u64 = 150 * 1024 * 1024;
const MAX_DENO_BYTES: u64 = 250 * 1024 * 1024;
const MAX_FFMPEG_ARCHIVE_BYTES: u64 = 350 * 1024 * 1024;
const MAX_FFMPEG_EXECUTABLE_BYTES: u64 = 250 * 1024 * 1024;
const MAX_SPOTDL_BYTES: u64 = 200 * 1024 * 1024;

#[derive(Debug, Clone)]
pub struct Toolchain {
    pub yt_dlp: Option<PathBuf>,
    pub ffmpeg: Option<PathBuf>,
    pub ffprobe: Option<PathBuf>,
    pub spotdl: Option<PathBuf>,
    pub js_runtime: Option<(String, PathBuf)>,
}

#[derive(Debug, Deserialize)]
struct GithubRelease {
    #[allow(dead_code)]
    tag_name: String,
    assets: Vec<GithubAsset>,
}

#[derive(Debug, Clone, Deserialize)]
struct GithubAsset {
    name: String,
    browser_download_url: String,
    digest: Option<String>,
    size: u64,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum FfmpegPackage {
    RawPair {
        repository: &'static str,
        ffmpeg_asset: &'static str,
        ffprobe_asset: &'static str,
    },
    Zip {
        repository: &'static str,
        asset: &'static str,
    },
    TarXz {
        repository: &'static str,
        asset: &'static str,
    },
}

pub async fn inspect(app_data: &Path) -> Vec<ToolInfo> {
    let tools = discover(app_data);
    let mut result = Vec::new();
    result.push(tool_info("yt-dlp", true, tools.yt_dlp.as_deref(), "--version").await);
    result.push(tool_info("FFmpeg", true, tools.ffmpeg.as_deref(), "-version").await);
    result.push(tool_info("FFprobe", false, tools.ffprobe.as_deref(), "-version").await);

    let mut spotdl = tool_info("SpotDL", false, tools.spotdl.as_deref(), "--version").await;
    if !spotdl.installed && spotdl_asset_suffix_for(env::consts::OS, env::consts::ARCH).is_none() {
        spotdl.status = format!(
            "Optional component; managed SpotDL is not published for {}/{} (manual installs are detected)",
            env::consts::OS,
            env::consts::ARCH
        );
    }
    result.push(spotdl);

    match tools.js_runtime {
        Some((name, path)) => {
            let mut info = tool_info(&name, true, Some(&path), "--version").await;
            info.name = "JavaScript runtime".into();
            result.push(info);
        }
        None => result.push(ToolInfo {
            name: "JavaScript runtime".into(),
            required: true,
            installed: false,
            path: None,
            version: None,
            status: "Deno or Node is required for reliable YouTube extraction".into(),
        }),
    }
    result
}

pub async fn install_or_update(app_data: &Path) -> Result<Vec<ToolInfo>, String> {
    let bin_dir = app_data.join("tools").join("bin");
    let staging_dir = app_data.join("tools").join("staging");
    fs::create_dir_all(&bin_dir).map_err(|error| error.to_string())?;
    fs::create_dir_all(&staging_dir).map_err(|error| error.to_string())?;

    let client = download_client()?;

    let yt_asset = yt_dlp_asset_name(env::consts::OS, env::consts::ARCH)?;
    install_release_executable(
        &client,
        "yt-dlp/yt-dlp",
        yt_asset,
        MAX_YT_DLP_BYTES,
        &staging_dir,
        &bin_dir.join(executable_name("yt-dlp")),
        "--version",
    )
    .await?;

    install_deno(&client, &staging_dir, &bin_dir).await?;
    if !has_healthy_external_ffmpeg_pair(app_data).await {
        install_ffmpeg(&client, &staging_dir, &bin_dir).await?;
    }

    let spotdl_warning = if spotdl_asset_suffix_for(env::consts::OS, env::consts::ARCH).is_some() {
        install_spotdl(&client, &staging_dir, &bin_dir).await.err()
    } else {
        None
    };

    let mut status = inspect(app_data).await;
    if let Some(error) = spotdl_warning
        && let Some(tool) = status.iter_mut().find(|tool| tool.name == "SpotDL")
    {
        tool.status = if tool.installed {
            format!("Ready; optional SpotDL update was skipped: {error}")
        } else {
            format!("Optional SpotDL update was skipped: {error}")
        };
    }
    let missing: Vec<_> = status
        .iter()
        .filter(|tool| tool.required && !tool.installed)
        .map(|tool| tool.name.clone())
        .collect();
    if !missing.is_empty() {
        return Err(format!(
            "Installed components failed verification: {}",
            missing.join(", ")
        ));
    }
    Ok(status)
}

pub fn discover(app_data: &Path) -> Toolchain {
    Toolchain {
        yt_dlp: find_executable("yt-dlp", "NEKULOADER_YT_DLP", app_data),
        ffmpeg: find_executable("ffmpeg", "NEKULOADER_FFMPEG", app_data),
        ffprobe: find_executable("ffprobe", "NEKULOADER_FFPROBE", app_data),
        spotdl: find_executable("spotdl", "NEKULOADER_SPOTDL", app_data),
        js_runtime: find_executable("deno", "NEKULOADER_DENO", app_data)
            .map(|path| ("deno".into(), path))
            .or_else(|| {
                find_executable("node", "NEKULOADER_NODE", app_data)
                    .map(|path| ("node".into(), path))
            }),
    }
}

fn find_executable(name: &str, override_name: &str, app_data: &Path) -> Option<PathBuf> {
    if let Some(path) = env::var_os(override_name)
        .map(PathBuf::from)
        .filter(|path| runnable(path))
    {
        return Some(path);
    }

    let executable = executable_name(name);
    let mut candidates = vec![
        app_data.join("tools").join("current").join(&executable),
        app_data.join("tools").join("bin").join(&executable),
    ];
    if let Some(path) = env::var_os("PATH") {
        candidates.extend(env::split_paths(&path).map(|directory| directory.join(&executable)));
    }
    if cfg!(target_os = "macos") {
        for directory in ["/opt/homebrew/bin", "/usr/local/bin", "/usr/bin"] {
            candidates.push(PathBuf::from(directory).join(&executable));
        }
        if name == "yt-dlp" || name == "python3" {
            let versions = Path::new("/Library/Frameworks/Python.framework/Versions");
            if let Ok(entries) = fs::read_dir(versions) {
                for entry in entries.flatten() {
                    candidates.push(entry.path().join("bin").join(&executable));
                }
            }
        }
    } else if cfg!(target_os = "linux") {
        for directory in ["/usr/local/bin", "/usr/bin", "/snap/bin"] {
            candidates.push(PathBuf::from(directory).join(&executable));
        }
    }

    candidates.into_iter().find(|path| runnable(path))
}

fn executable_name(name: &str) -> String {
    if cfg!(windows) {
        format!("{name}.exe")
    } else {
        name.to_string()
    }
}

fn runnable(path: &Path) -> bool {
    if !path.is_file() {
        return false;
    }
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        path.metadata()
            .map(|meta| meta.permissions().mode() & 0o111 != 0)
            .unwrap_or(false)
    }
    #[cfg(not(unix))]
    true
}

async fn has_healthy_external_ffmpeg_pair(app_data: &Path) -> bool {
    let tools = discover(app_data);
    let (Some(ffmpeg), Some(ffprobe)) = (tools.ffmpeg, tools.ffprobe) else {
        return false;
    };
    if is_app_managed(&ffmpeg, app_data) || is_app_managed(&ffprobe, app_data) {
        return false;
    }
    verify_executable(&ffmpeg, "-version").await.is_ok()
        && verify_executable(&ffprobe, "-version").await.is_ok()
}

fn is_app_managed(path: &Path, app_data: &Path) -> bool {
    let tools = app_data.join("tools");
    path.starts_with(tools.join("bin")) || path.starts_with(tools.join("current"))
}

fn download_client() -> Result<Client, String> {
    Client::builder()
        .user_agent(USER_AGENT)
        .https_only(true)
        .connect_timeout(Duration::from_secs(20))
        .timeout(Duration::from_secs(15 * 60))
        .redirect(Policy::limited(8))
        .build()
        .map_err(|error| format!("Could not initialize the secure download client: {error}"))
}

async fn install_release_executable(
    client: &Client,
    repository: &str,
    asset_name: &str,
    maximum_bytes: u64,
    staging_dir: &Path,
    destination: &Path,
    version_arg: &str,
) -> Result<(), String> {
    let release = latest_release(client, repository).await?;
    let asset = release_asset(&release, asset_name)?;
    stage_and_promote_executable(
        client,
        repository,
        asset,
        maximum_bytes,
        staging_dir,
        destination,
        version_arg,
    )
    .await
}

async fn stage_and_promote_executable(
    client: &Client,
    repository: &str,
    asset: &GithubAsset,
    maximum_bytes: u64,
    staging_dir: &Path,
    destination: &Path,
    version_arg: &str,
) -> Result<(), String> {
    let staged = temporary_path(staging_dir, destination);
    let result = async {
        download_asset(client, repository, asset, maximum_bytes, &staged).await?;
        make_executable(&staged)?;
        verify_executable(&staged, version_arg).await?;
        promote(&staged, destination)
    }
    .await;
    if result.is_err() {
        let _ = async_fs::remove_file(&staged).await;
    }
    result
}

async fn install_deno(client: &Client, staging_dir: &Path, bin_dir: &Path) -> Result<(), String> {
    let archive_name = deno_asset_name(env::consts::OS, env::consts::ARCH)?;
    let release = latest_release(client, "denoland/deno").await?;
    let asset = release_asset(&release, archive_name)?;
    let archive_path = temporary_path(staging_dir, Path::new(archive_name));
    let executable = executable_name("deno");
    let executable_path = temporary_path(staging_dir, Path::new(&executable));

    download_asset(
        client,
        "denoland/deno",
        asset,
        MAX_DENO_BYTES,
        &archive_path,
    )
    .await?;

    let archive_for_task = archive_path.clone();
    let executable_for_task = executable_path.clone();
    let executable_for_archive = executable.clone();
    let extraction = tokio::task::spawn_blocking(move || {
        extract_zip_executables(
            &archive_for_task,
            &[(
                executable_for_archive.as_str(),
                executable_for_task.as_path(),
            )],
        )
    })
    .await
    .map_err(|error| format!("Deno extraction task failed: {error}"))
    .and_then(|result| result);
    let _ = async_fs::remove_file(&archive_path).await;

    let result = async {
        extraction?;
        make_executable(&executable_path)?;
        verify_executable(&executable_path, "--version").await?;
        promote(&executable_path, &bin_dir.join(&executable))
    }
    .await;
    if result.is_err() {
        let _ = async_fs::remove_file(&executable_path).await;
    }
    result
}

async fn install_ffmpeg(client: &Client, staging_dir: &Path, bin_dir: &Path) -> Result<(), String> {
    let package = ffmpeg_package_for(env::consts::OS, env::consts::ARCH)?;
    match package {
        FfmpegPackage::RawPair {
            repository,
            ffmpeg_asset,
            ffprobe_asset,
        } => {
            let release = latest_release(client, repository).await?;
            let ffmpeg = release_asset(&release, ffmpeg_asset)?;
            let ffprobe = release_asset(&release, ffprobe_asset)?;
            let ffmpeg_destination = bin_dir.join(executable_name("ffmpeg"));
            let ffprobe_destination = bin_dir.join(executable_name("ffprobe"));
            let ffmpeg_staged = temporary_path(staging_dir, &ffmpeg_destination);
            let ffprobe_staged = temporary_path(staging_dir, &ffprobe_destination);

            let result = async {
                download_asset(
                    client,
                    repository,
                    ffmpeg,
                    MAX_FFMPEG_EXECUTABLE_BYTES,
                    &ffmpeg_staged,
                )
                .await?;
                download_asset(
                    client,
                    repository,
                    ffprobe,
                    MAX_FFMPEG_EXECUTABLE_BYTES,
                    &ffprobe_staged,
                )
                .await?;
                make_executable(&ffmpeg_staged)?;
                make_executable(&ffprobe_staged)?;
                verify_executable(&ffmpeg_staged, "-version").await?;
                verify_executable(&ffprobe_staged, "-version").await?;
                promote_all(&[
                    (&ffmpeg_staged, &ffmpeg_destination),
                    (&ffprobe_staged, &ffprobe_destination),
                ])
            }
            .await;
            if result.is_err() {
                let _ = async_fs::remove_file(&ffmpeg_staged).await;
                let _ = async_fs::remove_file(&ffprobe_staged).await;
            }
            result
        }
        FfmpegPackage::Zip { repository, asset } | FfmpegPackage::TarXz { repository, asset } => {
            install_ffmpeg_archive(client, package, repository, asset, staging_dir, bin_dir).await
        }
    }
}

async fn install_ffmpeg_archive(
    client: &Client,
    package: FfmpegPackage,
    repository: &str,
    asset_name: &str,
    staging_dir: &Path,
    bin_dir: &Path,
) -> Result<(), String> {
    let release = latest_release(client, repository).await?;
    let asset = release_asset(&release, asset_name)?;
    let archive_path = temporary_path(staging_dir, Path::new(asset_name));
    let ffmpeg_name = executable_name("ffmpeg");
    let ffprobe_name = executable_name("ffprobe");
    let ffmpeg_destination = bin_dir.join(&ffmpeg_name);
    let ffprobe_destination = bin_dir.join(&ffprobe_name);
    let ffmpeg_staged = temporary_path(staging_dir, &ffmpeg_destination);
    let ffprobe_staged = temporary_path(staging_dir, &ffprobe_destination);

    download_asset(
        client,
        repository,
        asset,
        MAX_FFMPEG_ARCHIVE_BYTES,
        &archive_path,
    )
    .await?;

    let archive_for_task = archive_path.clone();
    let ffmpeg_for_task = ffmpeg_staged.clone();
    let ffprobe_for_task = ffprobe_staged.clone();
    let ffmpeg_name_for_task = ffmpeg_name.clone();
    let ffprobe_name_for_task = ffprobe_name.clone();
    let extraction = tokio::task::spawn_blocking(move || match package {
        FfmpegPackage::Zip { .. } => extract_zip_executables(
            &archive_for_task,
            &[
                (ffmpeg_name_for_task.as_str(), ffmpeg_for_task.as_path()),
                (ffprobe_name_for_task.as_str(), ffprobe_for_task.as_path()),
            ],
        ),
        FfmpegPackage::TarXz { .. } => extract_tar_xz_executables(
            &archive_for_task,
            &[
                (ffmpeg_name_for_task.as_str(), ffmpeg_for_task.as_path()),
                (ffprobe_name_for_task.as_str(), ffprobe_for_task.as_path()),
            ],
        ),
        FfmpegPackage::RawPair { .. } => Err("Invalid FFmpeg package type".into()),
    })
    .await
    .map_err(|error| format!("FFmpeg extraction task failed: {error}"))
    .and_then(|result| result);
    let _ = async_fs::remove_file(&archive_path).await;

    let result = async {
        extraction?;
        make_executable(&ffmpeg_staged)?;
        make_executable(&ffprobe_staged)?;
        verify_executable(&ffmpeg_staged, "-version").await?;
        verify_executable(&ffprobe_staged, "-version").await?;
        promote_all(&[
            (&ffmpeg_staged, &ffmpeg_destination),
            (&ffprobe_staged, &ffprobe_destination),
        ])
    }
    .await;
    if result.is_err() {
        let _ = async_fs::remove_file(&ffmpeg_staged).await;
        let _ = async_fs::remove_file(&ffprobe_staged).await;
    }
    result
}

async fn install_spotdl(client: &Client, staging_dir: &Path, bin_dir: &Path) -> Result<(), String> {
    let suffix = spotdl_asset_suffix_for(env::consts::OS, env::consts::ARCH)
        .ok_or_else(|| "Managed SpotDL is unavailable for this architecture".to_string())?;
    let repository = "spotDL/spotify-downloader";
    let release = latest_release(client, repository).await?;
    let asset = release_asset_with_suffix(&release, "spotdl-", suffix)?;
    stage_and_promote_executable(
        client,
        repository,
        asset,
        MAX_SPOTDL_BYTES,
        staging_dir,
        &bin_dir.join(executable_name("spotdl")),
        "--version",
    )
    .await
}

async fn latest_release(client: &Client, repository: &str) -> Result<GithubRelease, String> {
    let url = format!("{GITHUB_API}/repos/{repository}/releases/latest");
    let response = client
        .get(&url)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
        .send()
        .await
        .map_err(|error| format!("Could not query the {repository} release: {error}"))?;
    if !response.status().is_success() {
        return Err(format!(
            "GitHub returned HTTP {} while querying the {repository} release",
            response.status()
        ));
    }
    let bytes =
        response_bytes_limited(response, MAX_RELEASE_METADATA_BYTES, "release metadata").await?;
    serde_json::from_slice(&bytes).map_err(|error| {
        format!("GitHub returned invalid release metadata for {repository}: {error}")
    })
}

fn release_asset<'a>(release: &'a GithubRelease, name: &str) -> Result<&'a GithubAsset, String> {
    release
        .assets
        .iter()
        .find(|asset| asset.name == name)
        .ok_or_else(|| format!("The latest upstream release does not contain {name}"))
}

fn release_asset_with_suffix<'a>(
    release: &'a GithubRelease,
    prefix: &str,
    suffix: &str,
) -> Result<&'a GithubAsset, String> {
    let mut matches = release
        .assets
        .iter()
        .filter(|asset| asset.name.starts_with(prefix) && asset.name.ends_with(suffix));
    let asset = matches
        .next()
        .ok_or_else(|| format!("The latest upstream release has no {suffix} executable"))?;
    if matches.next().is_some() {
        return Err(format!(
            "The latest upstream release contains ambiguous {suffix} executables"
        ));
    }
    Ok(asset)
}

async fn download_asset(
    client: &Client,
    repository: &str,
    asset: &GithubAsset,
    maximum_bytes: u64,
    destination: &Path,
) -> Result<(), String> {
    let expected = expected_sha256(asset)?;
    validate_download_url(repository, asset)?;
    if asset.size == 0 || asset.size > maximum_bytes {
        return Err(format!(
            "{} has an unexpected published size of {} bytes",
            asset.name, asset.size
        ));
    }

    let result = async {
        let mut response = client
            .get(&asset.browser_download_url)
            .send()
            .await
            .map_err(|error| format!("Could not download {}: {error}", asset.name))?;
        if !response.status().is_success() {
            return Err(format!(
                "Upstream returned HTTP {} while downloading {}",
                response.status(),
                asset.name
            ));
        }
        if response
            .content_length()
            .is_some_and(|length| length > maximum_bytes)
        {
            return Err(format!("{} exceeds the download size limit", asset.name));
        }

        let mut output = async_fs::OpenOptions::new()
            .write(true)
            .create_new(true)
            .open(destination)
            .await
            .map_err(|error| format!("Could not create the staged {}: {error}", asset.name))?;
        let mut hasher = Sha256::new();
        let mut downloaded = 0_u64;
        while let Some(chunk) = response
            .chunk()
            .await
            .map_err(|error| format!("Download of {} was interrupted: {error}", asset.name))?
        {
            downloaded = downloaded
                .checked_add(chunk.len() as u64)
                .ok_or_else(|| format!("{} exceeded the download size limit", asset.name))?;
            if downloaded > maximum_bytes || downloaded > asset.size {
                return Err(format!("{} exceeded its published size", asset.name));
            }
            hasher.update(&chunk);
            output
                .write_all(&chunk)
                .await
                .map_err(|error| format!("Could not stage {}: {error}", asset.name))?;
        }
        output
            .flush()
            .await
            .map_err(|error| format!("Could not flush {}: {error}", asset.name))?;
        output
            .sync_all()
            .await
            .map_err(|error| format!("Could not persist {}: {error}", asset.name))?;

        if downloaded != asset.size {
            return Err(format!(
                "{} was truncated (expected {} bytes, received {downloaded})",
                asset.name, asset.size
            ));
        }
        let actual = format!("{:x}", hasher.finalize());
        if actual != expected {
            return Err(format!("SHA-256 verification failed for {}", asset.name));
        }
        Ok(())
    }
    .await;

    if result.is_err() {
        let _ = async_fs::remove_file(destination).await;
    }
    result
}

async fn response_bytes_limited(
    mut response: Response,
    maximum_bytes: u64,
    label: &str,
) -> Result<Vec<u8>, String> {
    if response
        .content_length()
        .is_some_and(|length| length > maximum_bytes)
    {
        return Err(format!("The {label} response is unexpectedly large"));
    }
    let mut bytes = Vec::new();
    let mut total = 0_u64;
    while let Some(chunk) = response
        .chunk()
        .await
        .map_err(|error| format!("Could not read {label}: {error}"))?
    {
        total = total
            .checked_add(chunk.len() as u64)
            .ok_or_else(|| format!("The {label} response is unexpectedly large"))?;
        if total > maximum_bytes {
            return Err(format!("The {label} response is unexpectedly large"));
        }
        bytes.extend_from_slice(&chunk);
    }
    Ok(bytes)
}

fn expected_sha256(asset: &GithubAsset) -> Result<String, String> {
    let digest = asset
        .digest
        .as_deref()
        .ok_or_else(|| format!("GitHub did not publish a digest for {}", asset.name))?;
    let hash = digest
        .strip_prefix("sha256:")
        .ok_or_else(|| format!("GitHub published an unsupported digest for {}", asset.name))?;
    if hash.len() != 64 || !hash.bytes().all(|byte| byte.is_ascii_hexdigit()) {
        return Err(format!(
            "GitHub published an invalid SHA-256 digest for {}",
            asset.name
        ));
    }
    Ok(hash.to_ascii_lowercase())
}

fn validate_download_url(repository: &str, asset: &GithubAsset) -> Result<(), String> {
    let url = Url::parse(&asset.browser_download_url)
        .map_err(|error| format!("Invalid download URL for {}: {error}", asset.name))?;
    let expected_prefix = format!("/{repository}/releases/download/");
    if url.scheme() != "https"
        || url.host_str() != Some("github.com")
        || !url.path().starts_with(&expected_prefix)
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return Err(format!(
            "GitHub returned an unexpected download URL for {}",
            asset.name
        ));
    }
    Ok(())
}

fn extract_zip_executables(archive_path: &Path, outputs: &[(&str, &Path)]) -> Result<(), String> {
    let file = fs::File::open(archive_path).map_err(|error| error.to_string())?;
    let mut archive = zip::ZipArchive::new(file).map_err(|error| error.to_string())?;
    let mut indexes = vec![None; outputs.len()];

    for index in 0..archive.len() {
        let entry = archive.by_index(index).map_err(|error| error.to_string())?;
        if entry.is_dir() {
            continue;
        }
        let Some(name) = Path::new(entry.name())
            .file_name()
            .and_then(|name| name.to_str())
        else {
            continue;
        };
        for (target_index, (target, _)) in outputs.iter().enumerate() {
            if name == *target && indexes[target_index].replace(index).is_some() {
                return Err(format!("Archive contains more than one {target}"));
            }
        }
    }

    for (target_index, (target, destination)) in outputs.iter().enumerate() {
        let index =
            indexes[target_index].ok_or_else(|| format!("Archive does not contain {target}"))?;
        let mut entry = archive.by_index(index).map_err(|error| error.to_string())?;
        let size = entry.size();
        validate_archive_entry(size, target)?;
        copy_archive_entry(&mut entry, size, destination, target)?;
    }
    Ok(())
}

fn extract_tar_xz_executables(
    archive_path: &Path,
    outputs: &[(&str, &Path)],
) -> Result<(), String> {
    let file = fs::File::open(archive_path).map_err(|error| error.to_string())?;
    let decoder = xz2::read::XzDecoder::new(file);
    let mut archive = tar::Archive::new(decoder);
    let mut found = vec![false; outputs.len()];

    for entry in archive.entries().map_err(|error| error.to_string())? {
        let mut entry = entry.map_err(|error| error.to_string())?;
        if !entry.header().entry_type().is_file() {
            continue;
        }
        let path = entry.path().map_err(|error| error.to_string())?;
        let Some(name) = path.file_name().and_then(|name| name.to_str()) else {
            continue;
        };
        for (target_index, (target, destination)) in outputs.iter().enumerate() {
            if name == *target {
                if found[target_index] {
                    return Err(format!("Archive contains more than one {target}"));
                }
                let size = entry.size();
                validate_archive_entry(size, target)?;
                copy_archive_entry(&mut entry, size, destination, target)?;
                found[target_index] = true;
                break;
            }
        }
    }

    for (target_index, (target, _)) in outputs.iter().enumerate() {
        if !found[target_index] {
            return Err(format!("Archive does not contain {target}"));
        }
    }
    Ok(())
}

fn validate_archive_entry(size: u64, name: &str) -> Result<(), String> {
    if size == 0 || size > MAX_FFMPEG_EXECUTABLE_BYTES {
        return Err(format!("Archived {name} has an unexpected size"));
    }
    Ok(())
}

fn copy_archive_entry<R: Read>(
    reader: &mut R,
    declared_size: u64,
    destination: &Path,
    name: &str,
) -> Result<(), String> {
    let mut output = OpenOptions::new()
        .write(true)
        .create_new(true)
        .open(destination)
        .map_err(|error| format!("Could not create staged {name}: {error}"))?;
    let copied = io::copy(
        &mut reader.take(MAX_FFMPEG_EXECUTABLE_BYTES + 1),
        &mut output,
    )
    .map_err(|error| format!("Could not extract {name}: {error}"))?;
    if copied != declared_size || copied > MAX_FFMPEG_EXECUTABLE_BYTES {
        return Err(format!("Archived {name} did not match its declared size"));
    }
    output
        .flush()
        .and_then(|_| output.sync_all())
        .map_err(|error| format!("Could not persist staged {name}: {error}"))
}

fn yt_dlp_asset_name(os: &str, arch: &str) -> Result<&'static str, String> {
    match (os, arch) {
        ("macos", "aarch64" | "x86_64") => Ok("yt-dlp_macos"),
        ("windows", "aarch64") => Ok("yt-dlp_arm64.exe"),
        ("windows", "x86_64") => Ok("yt-dlp.exe"),
        ("linux", "aarch64") => Ok("yt-dlp_linux_aarch64"),
        ("linux", "x86_64") => Ok("yt-dlp_linux"),
        _ => Err(format!("Managed yt-dlp is not available for {os}/{arch}")),
    }
}

fn deno_asset_name(os: &str, arch: &str) -> Result<&'static str, String> {
    match (os, arch) {
        ("macos", "aarch64") => Ok("deno-aarch64-apple-darwin.zip"),
        ("macos", "x86_64") => Ok("deno-x86_64-apple-darwin.zip"),
        ("windows", "x86_64") => Ok("deno-x86_64-pc-windows-msvc.zip"),
        ("windows", "aarch64") => Ok("deno-aarch64-pc-windows-msvc.zip"),
        ("linux", "x86_64") => Ok("deno-x86_64-unknown-linux-gnu.zip"),
        ("linux", "aarch64") => Ok("deno-aarch64-unknown-linux-gnu.zip"),
        _ => Err(format!("Managed Deno is not available for {os}/{arch}")),
    }
}

fn ffmpeg_package_for(os: &str, arch: &str) -> Result<FfmpegPackage, String> {
    match (os, arch) {
        ("macos", "aarch64") => Ok(FfmpegPackage::RawPair {
            repository: "eugeneware/ffmpeg-static",
            ffmpeg_asset: "ffmpeg-darwin-arm64",
            ffprobe_asset: "ffprobe-darwin-arm64",
        }),
        ("macos", "x86_64") => Ok(FfmpegPackage::RawPair {
            repository: "eugeneware/ffmpeg-static",
            ffmpeg_asset: "ffmpeg-darwin-x64",
            ffprobe_asset: "ffprobe-darwin-x64",
        }),
        ("windows", "aarch64") => Ok(FfmpegPackage::Zip {
            repository: "yt-dlp/FFmpeg-Builds",
            asset: "ffmpeg-master-latest-winarm64-gpl.zip",
        }),
        ("windows", "x86_64") => Ok(FfmpegPackage::Zip {
            repository: "yt-dlp/FFmpeg-Builds",
            asset: "ffmpeg-master-latest-win64-gpl.zip",
        }),
        ("linux", "aarch64") => Ok(FfmpegPackage::TarXz {
            repository: "yt-dlp/FFmpeg-Builds",
            asset: "ffmpeg-master-latest-linuxarm64-gpl.tar.xz",
        }),
        ("linux", "x86_64") => Ok(FfmpegPackage::TarXz {
            repository: "yt-dlp/FFmpeg-Builds",
            asset: "ffmpeg-master-latest-linux64-gpl.tar.xz",
        }),
        _ => Err(format!("Managed FFmpeg is not available for {os}/{arch}")),
    }
}

fn spotdl_asset_suffix_for(os: &str, arch: &str) -> Option<&'static str> {
    match (os, arch) {
        ("macos", "aarch64") => Some("-darwin"),
        ("linux", "x86_64") => Some("-linux"),
        ("windows", "x86_64") => Some("-win32.exe"),
        _ => None,
    }
}

fn temporary_path(staging_dir: &Path, destination: &Path) -> PathBuf {
    let stem = destination
        .file_stem()
        .and_then(|value| value.to_str())
        .unwrap_or("component");
    let id = Uuid::new_v4();
    match destination.extension().and_then(|value| value.to_str()) {
        Some(extension) => staging_dir.join(format!(".{stem}.{id}.part.{extension}")),
        None => staging_dir.join(format!(".{stem}.{id}.part")),
    }
}

fn promote(staged: &Path, destination: &Path) -> Result<(), String> {
    promote_all(&[(staged, destination)])
}

fn promote_all(pairs: &[(&Path, &Path)]) -> Result<(), String> {
    for (staged, destination) in pairs {
        if !staged.is_file() {
            return Err(format!("Staged component is missing: {}", staged.display()));
        }
        if pairs
            .iter()
            .filter(|(_, candidate)| candidate == destination)
            .count()
            != 1
        {
            return Err(format!(
                "Duplicate component destination: {}",
                destination.display()
            ));
        }
    }

    let backups: Vec<PathBuf> = pairs
        .iter()
        .map(|(_, destination)| destination.with_extension("previous"))
        .collect();
    for backup in &backups {
        if backup.exists() {
            fs::remove_file(backup).map_err(|error| error.to_string())?;
        }
    }

    let mut backed_up = Vec::new();
    for (index, (_, destination)) in pairs.iter().enumerate() {
        if destination.exists() {
            if let Err(error) = fs::rename(destination, &backups[index]) {
                for previous in backed_up.into_iter().rev() {
                    let (_, previous_destination) = pairs[previous];
                    let _ = fs::rename(&backups[previous], previous_destination);
                }
                return Err(format!(
                    "Could not preserve {} before updating: {error}",
                    destination.display()
                ));
            }
            backed_up.push(index);
        }
    }

    let mut installed = Vec::new();
    for (index, (staged, destination)) in pairs.iter().enumerate() {
        if let Err(error) = fs::rename(staged, destination) {
            for installed_index in installed.into_iter().rev() {
                let (_, installed_destination) = pairs[installed_index];
                let _ = fs::remove_file(installed_destination);
            }
            for backup_index in backed_up.into_iter().rev() {
                let (_, backup_destination) = pairs[backup_index];
                let _ = fs::rename(&backups[backup_index], backup_destination);
            }
            return Err(format!(
                "Could not activate {}: {error}",
                destination.display()
            ));
        }
        installed.push(index);
    }
    Ok(())
}

fn make_executable(_path: &Path) -> Result<(), String> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        let mut permissions = fs::metadata(_path)
            .map_err(|error| error.to_string())?
            .permissions();
        permissions.set_mode(0o755);
        fs::set_permissions(_path, permissions).map_err(|error| error.to_string())?;
    }
    Ok(())
}

async fn verify_executable(path: &Path, version_arg: &str) -> Result<(), String> {
    let mut command = Command::new(path);
    command
        .arg(version_arg)
        .stdin(Stdio::null())
        .kill_on_drop(true);
    match timeout(Duration::from_secs(20), command.output()).await {
        Ok(Ok(output)) if output.status.success() => Ok(()),
        Ok(Ok(output)) => Err(format!(
            "{} failed its health check with status {}",
            path.display(),
            output.status
        )),
        Ok(Err(error)) => Err(format!("Could not start {}: {error}", path.display())),
        Err(_) => Err(format!(
            "{} timed out during its health check",
            path.display()
        )),
    }
}

async fn tool_info(name: &str, required: bool, path: Option<&Path>, version_arg: &str) -> ToolInfo {
    let Some(path) = path else {
        return ToolInfo {
            name: name.into(),
            required,
            installed: false,
            path: None,
            version: None,
            status: if required {
                "Required component is missing"
            } else {
                "Optional component is not installed"
            }
            .into(),
        };
    };

    let mut command = Command::new(path);
    command
        .arg(version_arg)
        .stdin(Stdio::null())
        .kill_on_drop(true);
    let output = timeout(Duration::from_secs(10), command.output()).await;
    match output {
        Ok(Ok(output)) if output.status.success() => {
            let text = if output.stdout.is_empty() {
                &output.stderr
            } else {
                &output.stdout
            };
            let version = String::from_utf8_lossy(text)
                .lines()
                .next()
                .unwrap_or("Installed")
                .trim()
                .to_string();
            ToolInfo {
                name: name.into(),
                required,
                installed: true,
                path: Some(path.to_string_lossy().into_owned()),
                version: Some(version),
                status: "Ready".into(),
            }
        }
        _ => ToolInfo {
            name: name.into(),
            required,
            installed: false,
            path: Some(path.to_string_lossy().into_owned()),
            version: None,
            status: "Component was found but failed its health check".into(),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct TestDirectory(PathBuf);

    impl TestDirectory {
        fn new() -> Self {
            let path = env::temp_dir().join(format!("nekuloader-tooling-test-{}", Uuid::new_v4()));
            fs::create_dir_all(&path).expect("create test directory");
            Self(path)
        }
    }

    impl Drop for TestDirectory {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.0);
        }
    }

    fn asset(name: &str, digest: Option<&str>) -> GithubAsset {
        GithubAsset {
            name: name.into(),
            browser_download_url: format!(
                "https://github.com/owner/project/releases/download/v1/{name}"
            ),
            digest: digest.map(str::to_owned),
            size: 42,
        }
    }

    #[test]
    fn accepts_only_well_formed_sha256_asset_digests() {
        let hash = "0123456789abcdef".repeat(4);
        assert_eq!(
            expected_sha256(&asset("tool", Some(&format!("sha256:{hash}")))).unwrap(),
            hash
        );
        assert!(expected_sha256(&asset("tool", None)).is_err());
        assert!(expected_sha256(&asset("tool", Some("sha512:abcd"))).is_err());
        assert!(expected_sha256(&asset("tool", Some("sha256:not-a-hash"))).is_err());
    }

    #[test]
    fn validates_repository_scoped_https_download_urls() {
        let valid = asset("tool", Some(&format!("sha256:{}", "a".repeat(64))));
        assert!(validate_download_url("owner/project", &valid).is_ok());

        let mut wrong_repository = valid.clone();
        wrong_repository.browser_download_url =
            "https://github.com/other/project/releases/download/v1/tool".into();
        assert!(validate_download_url("owner/project", &wrong_repository).is_err());

        let mut insecure = valid;
        insecure.browser_download_url =
            "http://github.com/owner/project/releases/download/v1/tool".into();
        assert!(validate_download_url("owner/project", &insecure).is_err());
    }

    #[test]
    fn maps_supported_platform_assets_without_architecture_fallbacks() {
        assert_eq!(
            deno_asset_name("macos", "aarch64").unwrap(),
            "deno-aarch64-apple-darwin.zip"
        );
        assert_eq!(
            yt_dlp_asset_name("windows", "aarch64").unwrap(),
            "yt-dlp_arm64.exe"
        );
        assert_eq!(
            ffmpeg_package_for("linux", "x86_64").unwrap(),
            FfmpegPackage::TarXz {
                repository: "yt-dlp/FFmpeg-Builds",
                asset: "ffmpeg-master-latest-linux64-gpl.tar.xz",
            }
        );
        assert_eq!(spotdl_asset_suffix_for("macos", "aarch64"), Some("-darwin"));
        assert_eq!(spotdl_asset_suffix_for("macos", "x86_64"), None);
        assert!(deno_asset_name("linux", "x86").is_err());
        assert!(yt_dlp_asset_name("freebsd", "x86_64").is_err());
    }

    #[test]
    fn distinguishes_app_managed_tools_from_external_tools() {
        let app_data = Path::new("nekuloader-app-data");
        assert!(is_app_managed(
            &app_data.join("tools").join("bin").join("ffmpeg"),
            app_data
        ));
        assert!(is_app_managed(
            &app_data.join("tools").join("current").join("ffprobe"),
            app_data
        ));
        assert!(!is_app_managed(
            &Path::new("external-tools").join("ffmpeg"),
            app_data
        ));
    }

    #[test]
    fn extracts_only_named_zip_executables() {
        use zip::write::SimpleFileOptions;

        let directory = TestDirectory::new();
        let archive_path = directory.0.join("tools.zip");
        let file = fs::File::create(&archive_path).unwrap();
        let mut archive = zip::ZipWriter::new(file);
        archive
            .start_file("nested/bin/ffmpeg", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(b"ffmpeg-binary").unwrap();
        archive
            .start_file("nested/bin/ffprobe", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(b"ffprobe-binary").unwrap();
        archive
            .start_file("../../ignored", SimpleFileOptions::default())
            .unwrap();
        archive.write_all(b"ignored").unwrap();
        archive.finish().unwrap();

        let ffmpeg = directory.0.join("ffmpeg");
        let ffprobe = directory.0.join("ffprobe");
        extract_zip_executables(
            &archive_path,
            &[("ffmpeg", ffmpeg.as_path()), ("ffprobe", ffprobe.as_path())],
        )
        .unwrap();
        assert_eq!(fs::read(ffmpeg).unwrap(), b"ffmpeg-binary");
        assert_eq!(fs::read(ffprobe).unwrap(), b"ffprobe-binary");
        assert!(!directory.0.join("ignored").exists());
    }

    #[test]
    fn promotes_a_tool_pair_and_preserves_previous_versions() {
        let directory = TestDirectory::new();
        let staged_a = directory.0.join("new-a");
        let staged_b = directory.0.join("new-b");
        let destination_a = directory.0.join("tool-a");
        let destination_b = directory.0.join("tool-b");
        fs::write(&staged_a, b"new-a").unwrap();
        fs::write(&staged_b, b"new-b").unwrap();
        fs::write(&destination_a, b"old-a").unwrap();
        fs::write(&destination_b, b"old-b").unwrap();

        promote_all(&[
            (staged_a.as_path(), destination_a.as_path()),
            (staged_b.as_path(), destination_b.as_path()),
        ])
        .unwrap();

        assert_eq!(fs::read(&destination_a).unwrap(), b"new-a");
        assert_eq!(fs::read(&destination_b).unwrap(), b"new-b");
        assert_eq!(
            fs::read(destination_a.with_extension("previous")).unwrap(),
            b"old-a"
        );
        assert_eq!(
            fs::read(destination_b.with_extension("previous")).unwrap(),
            b"old-b"
        );
    }

    #[test]
    fn refuses_incomplete_promotions_before_changing_destinations() {
        let directory = TestDirectory::new();
        let staged = directory.0.join("new-a");
        let missing = directory.0.join("missing-b");
        let destination_a = directory.0.join("tool-a");
        let destination_b = directory.0.join("tool-b");
        fs::write(&staged, b"new-a").unwrap();
        fs::write(&destination_a, b"old-a").unwrap();
        fs::write(&destination_b, b"old-b").unwrap();

        assert!(
            promote_all(&[
                (staged.as_path(), destination_a.as_path()),
                (missing.as_path(), destination_b.as_path()),
            ])
            .is_err()
        );
        assert_eq!(fs::read(destination_a).unwrap(), b"old-a");
        assert_eq!(fs::read(destination_b).unwrap(), b"old-b");
    }

    #[tokio::test(flavor = "multi_thread")]
    #[ignore = "downloads and executes the current upstream toolchain"]
    async fn installs_and_health_checks_the_current_toolchain() {
        let directory = TestDirectory::new();
        let status = install_or_update(&directory.0)
            .await
            .expect("install the managed toolchain");
        for tool in &status {
            eprintln!(
                "{}: installed={}, path={}, version={}",
                tool.name,
                tool.installed,
                tool.path.as_deref().unwrap_or("-"),
                tool.version.as_deref().unwrap_or("-")
            );
        }
        assert!(
            status
                .iter()
                .filter(|tool| tool.required)
                .all(|tool| tool.installed),
            "required tool status: {status:#?}"
        );
    }
}

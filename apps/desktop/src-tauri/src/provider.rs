use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};

use url::Url;

use crate::models::{MediaKind, Provider, SourceDetection};

pub fn detect(raw_url: &str) -> Result<SourceDetection, String> {
    let trimmed = raw_url.trim();
    let parsed =
        Url::parse(trimmed).map_err(|_| "Enter a complete http or https link".to_string())?;
    if !matches!(parsed.scheme(), "http" | "https") {
        return Err("Only http and https links are supported".into());
    }
    if !parsed.username().is_empty() || parsed.password().is_some() {
        return Err("Links with embedded usernames or passwords are not allowed".into());
    }

    let host = parsed
        .host_str()
        .ok_or_else(|| "This link does not contain a valid host".to_string())?
        .to_ascii_lowercase();
    if is_private_host(&host) {
        return Err("Local and private-network links are not allowed".into());
    }

    let path = parsed.path().to_ascii_lowercase();
    let segments: Vec<&str> = path.split('/').filter(|part| !part.is_empty()).collect();
    let (provider, media_kind) = if host == "music.youtube.com" {
        (Provider::YouTubeMusic, youtube_kind(&path, &parsed))
    } else if host == "youtu.be" || host == "youtube.com" || host.ends_with(".youtube.com") {
        (Provider::YouTube, youtube_kind(&path, &parsed))
    } else if host == "spotify.com" || host.ends_with(".spotify.com") || host == "spotify.link" {
        (Provider::Spotify, segment_kind(&segments))
    } else if host == "soundcloud.com"
        || host.ends_with(".soundcloud.com")
        || host == "on.soundcloud.com"
    {
        let kind = if segments.contains(&"sets") {
            MediaKind::Playlist
        } else {
            MediaKind::Track
        };
        (Provider::SoundCloud, kind)
    } else if host == "deezer.com" || host.ends_with(".deezer.com") || host == "deezer.page.link" {
        (Provider::Deezer, segment_kind(&segments))
    } else if host == "music.apple.com" || host == "itunes.apple.com" {
        let kind = if parsed.query_pairs().any(|(key, _)| key == "i") {
            MediaKind::Track
        } else {
            segment_kind(&segments)
        };
        (Provider::AppleMusic, kind)
    } else {
        (Provider::Unknown, MediaKind::Unknown)
    };

    let supported = provider != Provider::Unknown;
    let message = if supported {
        format!(
            "{} {} detected",
            provider.label(),
            media_kind.label().to_lowercase()
        )
    } else {
        "This source is not supported yet".into()
    };

    Ok(SourceDetection {
        provider,
        media_kind,
        host,
        supported,
        normalized_url: parsed.to_string(),
        message,
    })
}

fn youtube_kind(path: &str, url: &Url) -> MediaKind {
    if path == "/playlist" || path.starts_with("/playlist/") {
        MediaKind::Playlist
    } else if path.starts_with("/browse/") {
        MediaKind::Album
    } else if path == "/watch" || url.host_str() == Some("youtu.be") || path.starts_with("/shorts/")
    {
        // A watch link remains a single track even when YouTube appends a list query.
        MediaKind::Track
    } else {
        MediaKind::Unknown
    }
}

fn segment_kind(segments: &[&str]) -> MediaKind {
    if segments.contains(&"track") {
        MediaKind::Track
    } else if segments.contains(&"album") {
        MediaKind::Album
    } else if segments.contains(&"playlist") {
        MediaKind::Playlist
    } else {
        MediaKind::Unknown
    }
}

fn is_private_host(host: &str) -> bool {
    // `Url` follows the WHATWG IPv4 parser, so unusual numeric forms such as
    // `127.1` reach this function normalized. Strip IPv6 brackets as well so
    // this helper remains correct when it is tested or reused independently.
    let normalized = host
        .trim_end_matches('.')
        .trim_start_matches('[')
        .trim_end_matches(']')
        .to_ascii_lowercase();

    if normalized == "localhost"
        || normalized.ends_with(".localhost")
        || normalized == "local"
        || normalized.ends_with(".local")
        || normalized == "home.arpa"
        || normalized.ends_with(".home.arpa")
    {
        return true;
    }

    normalized
        .parse::<IpAddr>()
        .is_ok_and(is_local_or_private_ip)
}

fn is_local_or_private_ip(address: IpAddr) -> bool {
    match address {
        IpAddr::V4(address) => is_local_or_private_ipv4(address),
        IpAddr::V6(address) => is_local_or_private_ipv6(address),
    }
}

fn is_local_or_private_ipv4(address: Ipv4Addr) -> bool {
    address.is_private()
        || address.is_loopback()
        || address.is_link_local()
        || address.is_unspecified()
}

fn is_local_or_private_ipv6(address: Ipv6Addr) -> bool {
    if let Some(mapped) = address.to_ipv4_mapped() {
        return is_local_or_private_ipv4(mapped);
    }

    let first_segment = address.segments()[0];
    address.is_loopback()
        || address.is_unspecified()
        || address.is_unique_local()
        || address.is_unicast_link_local()
        // FEC0::/10 was the deprecated IPv6 site-local range. Some operating
        // systems still route it locally, so it must not bypass this guard.
        || first_segment & 0xffc0 == 0xfec0
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn detects_youtube_music_before_youtube() {
        let result = detect("https://music.youtube.com/watch?v=abc&list=album").unwrap();
        assert_eq!(result.provider, Provider::YouTubeMusic);
        assert_eq!(result.media_kind, MediaKind::Track);
    }

    #[test]
    fn detects_spotify_album() {
        let result = detect("https://open.spotify.com/album/abc").unwrap();
        assert_eq!(result.provider, Provider::Spotify);
        assert_eq!(result.media_kind, MediaKind::Album);
    }

    #[test]
    fn blocks_local_network_urls() {
        assert!(detect("http://127.0.0.1/music").is_err());
        assert!(detect("http://192.168.1.5/music").is_err());
    }

    #[test]
    fn applies_the_exact_ipv4_private_boundaries() {
        for private in [
            "10.0.0.0",
            "10.255.255.255",
            "172.16.0.0",
            "172.31.255.255",
            "192.168.0.0",
            "192.168.255.255",
        ] {
            assert!(is_private_host(private), "{private} should be private");
        }

        for public in [
            "9.255.255.255",
            "11.0.0.0",
            "172.15.255.255",
            "172.32.0.0",
            "172.200.0.1",
            "192.167.255.255",
            "192.169.0.0",
        ] {
            assert!(!is_private_host(public), "{public} should not be private");
        }
    }

    #[test]
    fn blocks_all_loopback_and_link_local_forms() {
        for local in [
            "127.0.0.2",
            "127.255.255.255",
            "169.254.10.20",
            "0.0.0.0",
            "::",
            "::1",
            "fc00::1",
            "fdff::1",
            "fe80::1",
            "fec0::1",
            "::ffff:127.0.0.1",
            "::ffff:10.0.0.1",
        ] {
            assert!(is_private_host(local), "{local} should be local/private");
        }

        assert!(!is_private_host("2001:4860:4860::8888"));
        assert!(!is_private_host("::ffff:8.8.8.8"));
    }

    #[test]
    fn blocks_reserved_local_names_and_whatwg_ipv4_forms() {
        for local in [
            "localhost",
            "player.localhost.",
            "speaker.local",
            "home.arpa",
            "music.home.arpa.",
        ] {
            assert!(is_private_host(local), "{local} should be local");
        }

        // The URL parser normalizes shorthand IPv4 before validation.
        assert!(detect("http://127.1/music").is_err());
    }

    #[test]
    fn rejects_embedded_url_credentials() {
        for url in [
            "https://user@open.spotify.com/track/abc",
            "https://user:secret@music.youtube.com/watch?v=abc",
        ] {
            let error = detect(url).unwrap_err();
            assert!(error.contains("usernames or passwords"));
        }

        assert!(detect("https://open.spotify.com/track/abc").is_ok());
    }
}

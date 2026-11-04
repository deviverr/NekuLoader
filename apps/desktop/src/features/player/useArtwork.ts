import { useEffect, useState } from "react";
import * as api from "../../lib/api";

/**
 * Resolves a track's embedded cover art to a URL the webview can display.
 *
 * Results are memoised per path across the whole session: a library list asks
 * for the same covers every time it re-renders, and each miss costs an IPC
 * round trip plus a tag parse. `null` is cached too, so tracks without artwork
 * are only probed once.
 */
const cache = new Map<string, string | null>();
const inFlight = new Map<string, Promise<string | null>>();

function load(path: string): Promise<string | null> {
  const existing = inFlight.get(path);
  if (existing) return existing;

  const request = api
    .trackArtworkUrl(path)
    .catch(() => null)
    .then((url) => {
      cache.set(path, url);
      inFlight.delete(path);
      return url;
    });

  inFlight.set(path, request);
  return request;
}

/** Forgets resolved covers, e.g. after artwork was repaired on disk. */
export function invalidateArtwork(): void {
  cache.clear();
  inFlight.clear();
}

export function useArtwork(path: string | undefined, hasArtwork = true): string | null {
  const [url, setUrl] = useState<string | null>(() =>
    path ? (cache.get(path) ?? null) : null,
  );

  useEffect(() => {
    if (!path || !hasArtwork) {
      setUrl(null);
      return;
    }
    if (cache.has(path)) {
      setUrl(cache.get(path) ?? null);
      return;
    }
    let stale = false;
    void load(path).then((resolved) => {
      if (!stale) setUrl(resolved);
    });
    return () => {
      stale = true;
    };
  }, [path, hasArtwork]);

  return url;
}

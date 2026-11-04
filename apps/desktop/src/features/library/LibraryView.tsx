import { useEffect, useMemo, useState } from "react";
import { useT } from "../../lib/i18n";
import { Empty, IconButton, Notice, Panel, SectionHeading } from "../../components";
import * as api from "../../lib/api";
import { formatBytes, formatDuration } from "../../lib/formats";
import { usePlayer } from "../player/PlayerProvider";
import { invalidateArtwork, useArtwork } from "../player/useArtwork";
import type { LibraryTrack } from "../../types";
import "./library.css";

export function LibraryView({
  tracks,
  scanning,
  error,
  onRescan,
  onOpenPlayer,
}: {
  tracks: LibraryTrack[];
  scanning: boolean;
  error: string;
  onRescan: () => void;
  onOpenPlayer: () => void;
}) {
  const player = usePlayer();
  const t = useT();
  const [query, setQuery] = useState("");
  const [repair, setRepair] = useState<{ completed: number; total: number } | null>(null);
  const [repairNote, setRepairNote] = useState("");

  useEffect(() => {
    let stop: (() => void) | undefined;
    void api
      .onArtworkRepair((event) => setRepair({ completed: event.completed, total: event.total }))
      .then((unlisten) => {
        stop = unlisten;
      });
    return () => stop?.();
  }, []);

  async function fixCovers() {
    setRepairNote("");
    setRepair({ completed: 0, total: visible.length });
    try {
      const fixed = await api.repairArtwork(visible.map((track) => track.path));
      invalidateArtwork();
      setRepairNote(`${t("library.coversFixed")} ${fixed}`);
      onRescan();
    } catch (reason) {
      setRepairNote(String(reason));
    } finally {
      setRepair(null);
    }
  }

  const visible = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return tracks;
    return tracks.filter((track) =>
      [track.title, track.artist, track.album, track.format, track.path].some((value) =>
        value.toLocaleLowerCase().includes(needle),
      ),
    );
  }, [query, tracks]);

  function play(index: number) {
    // Playing from a filtered view queues what is on screen, which is what the
    // list implies — not the whole unfiltered library.
    player.playQueue(visible, index);
    onOpenPlayer();
  }

  return (
    <div className="view-stack">
      <Panel>
        <div className="library-toolbar">
          <div className="field">
            <span className="field__marker" aria-hidden="true">
              ⌕
            </span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("library.search")}
              aria-label="Search library"
            />
          </div>
          <button type="button" className="button" onClick={onRescan} disabled={scanning}>
            {scanning ? t("library.scanning") : t("library.rescan")}
          </button>
          <button
            type="button"
            className="button"
            disabled={visible.length === 0 || repair !== null}
            title="Downloads square cover art again from each track's source and embeds it (WAV and FLAC included)."
            onClick={() => void fixCovers()}
          >
            {repair
              ? `${t("library.fixingCovers")} ${repair.completed}/${repair.total}`
              : t("library.fixCovers")}
          </button>
          <button
            type="button"
            className="button button--primary"
            disabled={visible.length === 0}
            onClick={() => play(0)}
          >
            {t("library.playAll")}
          </button>
        </div>
        {repairNote && <p className="muted setting__note">{repairNote}</p>}
      </Panel>

      <Panel>
        <SectionHeading
          eyebrow="Local collection"
          title={t("library.title")}
          aside={
            <span className="muted">
              {query ? `${visible.length} of ${tracks.length}` : tracks.length} item
              {tracks.length === 1 ? "" : "s"}
            </span>
          }
        />

        {error && <Notice tone="error">{error}</Notice>}

        {!error && visible.length === 0 ? (
          <Empty
            title={
              query
                ? "No tracks match that search"
                : scanning
                  ? "Scanning your music folder"
                  : "No audio files found"
            }
            text={
              query
                ? "Try a title, artist, album, or format."
                : "Download music, or point Settings at a different folder and rescan."
            }
          />
        ) : (
          <div className="track-list">
            {visible.map((track, index) => (
              <TrackRow
                key={track.id}
                track={track}
                active={player.current?.id === track.id}
                playing={player.playing}
                onPlay={() => play(index)}
              />
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}

/** One library row, with its embedded cover art resolved lazily. */
function TrackRow({
  track,
  active,
  playing,
  onPlay,
}: {
  track: LibraryTrack;
  active: boolean;
  playing: boolean;
  onPlay: () => void;
}) {
  const cover = useArtwork(track.path, track.hasArtwork ?? true);

  return (
    <div className={active ? "track track--active" : "track"}>
      <button type="button" className="track__play" aria-label={`Play ${track.title}`} onClick={onPlay}>
        <span className="track__thumb">
          {cover ? <img src={cover} alt="" /> : <span aria-hidden="true">♪</span>}
        </span>
        <span className="track__overlay" aria-hidden="true">
          {active && playing ? "❚❚" : "▶"}
        </span>
      </button>

      <div className="track__meta">
        <strong title={track.title}>{track.title}</strong>
        <small title={track.path}>
          {[track.artist, track.album].filter(Boolean).join(" · ") || "Local audio"}
        </small>
      </div>

      <span className="track__badge">{track.format}</span>
      <span className="track__duration">{formatDuration(track.durationSeconds)}</span>
      <span className="track__size">{formatBytes(track.sizeBytes)}</span>
      <IconButton glyph="⌂" label={`Reveal ${track.title}`} onClick={() => void api.reveal(track.path)} />
    </div>
  );
}

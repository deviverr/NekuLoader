import { useMemo, useState } from "react";
import { Empty, Notice } from "../../components";
import { useT } from "../../lib/i18n";
import { PixelEqualizer, PixelIcon, PixelVinyl } from "./PixelDeck";
import { formatDuration } from "../../lib/formats";
import { usePlayer } from "./PlayerProvider";
import { useArtwork } from "./useArtwork";
import type { LibraryTrack } from "../../types";
import "./player.css";


/**
 * The player slide: a large cover with the transport beneath it and the play
 * queue alongside — the layout streaming players have converged on, because the
 * artwork is the thing worth the space and everything else reads as one column.
 */
export function PlayerScreen({ library, scanError }: { library: LibraryTrack[]; scanError?: string }) {
  const player = usePlayer();
  const t = useT();
  const { current, playing } = player;
  const cover = useArtwork(current?.path, current?.hasArtwork ?? true);
  const [panel, setPanel] = useState<"music" | "queue">("music");

  const side = (
    <div className="player__queue">
      <div className="tabs player__tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={panel === "music"}
          className={panel === "music" ? "tab tab--active" : "tab"}
          onClick={() => setPanel("music")}
        >
          {t("player.myMusic")} · {library.length}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={panel === "queue"}
          className={panel === "queue" ? "tab tab--active" : "tab"}
          onClick={() => setPanel("queue")}
        >
          {t("player.upNext")} · {player.queue.length}
        </button>
      </div>
      {panel === "music" && scanError && <Notice tone="warning">{scanError}</Notice>}
      {panel === "music" ? (
        <MusicBrowser library={library} />
      ) : (
        <>
          <div className="player__queue-head">
            <span className="eyebrow">{t("player.upNext")}</span>
            <button type="button" className="button" onClick={player.clear}>
              {t("player.clear")}
            </button>
          </div>
          <div className="play-queue">
            {player.queue.map((track, index) => (
              <QueueRow
                key={`${track.id}-${index}`}
                track={track}
                index={index}
                active={index === player.currentIndex}
                onPlay={() => player.playQueue(player.queue, index)}
                onRemove={() => player.removeAt(index)}
              />
            ))}
          </div>
        </>
      )}
    </div>
  );

  if (!current) {
    return (
      <div className="player">
        <div className="player__stage player__stage--empty">
          <div className="deck deck--bare">
            <div className="deck__vinyl">
              <PixelVinyl spinning={false} />
            </div>
            <div className="deck__sleeve">
              <span className="deck__placeholder" aria-hidden="true">
                <PixelIcon name="note" size={70} />
              </span>
            </div>
          </div>
          {library.length > 0 && <Empty title={t("player.nothing")} text="" />}
          {library.length > 0 && (
            <div className="player__start">
              <button type="button" className="button button--primary" onClick={() => player.playQueue(library, 0)}>
                {t("player.playAll")}
              </button>
              <button
                type="button"
                className="button"
                onClick={() => player.playQueue(shuffled(library), 0)}
              >
                ⤨ {t("player.shuffleAll")}
              </button>
            </div>
          )}
        </div>
        {side}
      </div>
    );
  }

  const duration = player.durationSeconds || current.durationSeconds || 0;
  const progress = duration ? (player.positionSeconds / duration) * 100 : 0;

  return (
    <div className="player">
      {player.error && <Notice tone="error">{player.error}</Notice>}

      {/* The cover, blurred and dimmed, lights the panel behind the art. */}
      {cover && (
        <div
          className="player__glow"
          aria-hidden="true"
          style={{ backgroundImage: `url("${cover}")` }}
        />
      )}

      <div className="player__stage">
        <div className={`deck${playing ? " deck--playing" : ""}${cover ? "" : " deck--bare"}`}>
          <div className="deck__vinyl">
            <PixelVinyl spinning={playing} />
          </div>
          <div className="deck__sleeve">
            {cover ? (
              <img src={cover} alt="" />
            ) : (
              <span className="deck__placeholder" aria-hidden="true">
                <PixelIcon name="note" size={70} />
              </span>
            )}
          </div>
        </div>

        <h2 className="player__title" title={current.title}>
          {current.title}
        </h2>
        <p className="player__artist">
          {current.artist || t("player.unknownArtist")}
          {current.album ? ` — ${current.album}` : ""}
        </p>

        <PixelEqualizer playing={playing} />

        <div className="player__seek">
          <span className="player__time">{formatDuration(player.positionSeconds)}</span>
          <div className="scrubber scrubber--pixel">
            <div className="scrubber__fill" style={{ width: `${progress}%` }} />
            <div className="scrubber__head" style={{ left: `${progress}%` }} />
            <input
              type="range"
              min={0}
              max={Math.max(duration, 1)}
              step={0.5}
              value={Math.min(player.positionSeconds, duration || 0)}
              aria-label="Seek"
              onChange={(event) => player.seek(Number(event.target.value))}
            />
          </div>
          <span className="player__time">{formatDuration(duration)}</span>
        </div>

        <div className="player__transport">
          <button
            type="button"
            className={player.shuffle ? "mode mode--on" : "mode"}
            aria-pressed={player.shuffle}
            title="Shuffle"
            onClick={player.toggleShuffle}
          >
            <PixelIcon name="shuffle" />
          </button>
          <button
            type="button"
            className="transport-button"
            aria-label="Previous track"
            onClick={player.previous}
          >
            <PixelIcon name="prev" />
          </button>
          <button
            type="button"
            className="transport-button transport-button--play"
            aria-label={playing ? "Pause" : "Play"}
            onClick={player.toggle}
          >
            <PixelIcon name={playing ? "pause" : "play"} size={20} />
          </button>
          <button
            type="button"
            className="transport-button"
            aria-label="Next track"
            onClick={player.next}
          >
            <PixelIcon name="next" />
          </button>
          <button
            type="button"
            className={player.repeatMode === "off" ? "mode" : "mode mode--on"}
            title={`Repeat: ${player.repeatMode}`}
            onClick={player.cycleRepeat}
          >
            <PixelIcon name={player.repeatMode === "one" ? "repeatOne" : "repeat"} />
          </button>
        </div>

        <label className="player__volume">
          <PixelIcon name="volume" />
          <input
            type="range"
            min={0}
            max={1}
            step={0.05}
            value={player.volume}
            aria-label="Volume"
            onChange={(event) => player.setVolume(Number(event.target.value))}
          />
        </label>
      </div>

      {side}
    </div>
  );
}

function QueueRow({
  track,
  index,
  active,
  onPlay,
  onRemove,
}: {
  track: LibraryTrack;
  index: number;
  active: boolean;
  onPlay: () => void;
  onRemove: () => void;
}) {
  const cover = useArtwork(track.path, track.hasArtwork ?? true);

  return (
    <div className={active ? "queue-row queue-row--active" : "queue-row"}>
      <button type="button" className="queue-row__main" onClick={onPlay}>
        <span className="queue-row__thumb">
          {cover ? <img src={cover} alt="" /> : <span aria-hidden="true">♪</span>}
        </span>
        <span className="queue-row__text">
          <strong>{track.title}</strong>
          <small>{track.artist || track.album || "Local audio"}</small>
        </span>
        <span className="player__time">{formatDuration(track.durationSeconds)}</span>
      </button>
      <button
        type="button"
        className="queue-row__remove"
        aria-label={`Remove ${track.title} from queue`}
        onClick={onRemove}
      >
        ✕
      </button>
      <span className="queue-row__index" aria-hidden="true">
        {active ? "▶" : String(index + 1).padStart(2, "0")}
      </span>
    </div>
  );
}

function shuffled<T>(items: T[]): T[] {
  const copy = [...items];
  for (let index = copy.length - 1; index > 0; index--) {
    const swap = Math.floor(Math.random() * (index + 1));
    [copy[index], copy[swap]] = [copy[swap], copy[index]];
  }
  return copy;
}

interface Album {
  key: string;
  title: string;
  artist: string;
  tracks: LibraryTrack[];
}

/** The user's library grouped into albums, newest first, with search. */
function MusicBrowser({ library }: { library: LibraryTrack[] }) {
  const player = usePlayer();
  const t = useT();
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState<string | null>(null);

  const albums = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    const matching = needle
      ? library.filter((track) =>
          [track.title, track.artist, track.album].some((value) => value.toLocaleLowerCase().includes(needle)),
        )
      : library;
    const groups = new Map<string, Album>();
    for (const track of matching) {
      const folder = track.path.slice(0, Math.max(track.path.lastIndexOf("/"), track.path.lastIndexOf("\\")));
      const key = `${track.album || "—"}::${folder}`;
      const album = groups.get(key) ?? { key, title: track.album || "—", artist: track.artist, tracks: [] };
      if (!album.artist && track.artist) album.artist = track.artist;
      album.tracks.push(track);
      groups.set(key, album);
    }
    for (const album of groups.values()) {
      album.tracks.sort(
        (left, right) =>
          (left.trackNumber ?? 9999) - (right.trackNumber ?? 9999) || left.title.localeCompare(right.title),
      );
    }
    return [...groups.values()];
  }, [library, query]);

  if (library.length === 0) {
    return <Empty title={t("player.empty")} text={t("player.emptyText")} />;
  }

  return (
    <div className="music-browser">
      <div className="field music-browser__search">
        <span className="field__marker" aria-hidden="true">
          ⌕
        </span>
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t("player.search")}
          aria-label={t("player.search")}
        />
      </div>
      <div className="album-list">
        {albums.map((album) => (
          <AlbumRow
            key={album.key}
            album={album}
            open={open === album.key || query.trim().length > 0 || albums.length === 1}
            onToggle={() => setOpen((current) => (current === album.key ? null : album.key))}
            onPlay={(index) => player.playQueue(album.tracks, index)}
            currentId={player.current?.id}
            playing={player.playing}
          />
        ))}
      </div>
    </div>
  );
}

function AlbumRow({
  album,
  open,
  onToggle,
  onPlay,
  currentId,
  playing,
}: {
  album: Album;
  open: boolean;
  onToggle: () => void;
  onPlay: (index: number) => void;
  currentId?: string;
  playing: boolean;
}) {
  const t = useT();
  const first = album.tracks[0];
  const cover = useArtwork(first?.path, first?.hasArtwork ?? true);
  const containsCurrent = album.tracks.some((track) => track.id === currentId);

  return (
    <div className={containsCurrent ? "album album--current" : "album"}>
      <div className="album__head">
        <button type="button" className="album__cover" aria-label={`${t("player.playAll")}: ${album.title}`} onClick={() => onPlay(0)}>
          {cover ? <img src={cover} alt="" /> : <span aria-hidden="true">♪</span>}
          <span className="album__play" aria-hidden="true">▶</span>
        </button>
        <button type="button" className="album__text" onClick={onToggle} aria-expanded={open}>
          <strong>{album.title}</strong>
          <small>
            {album.artist || t("player.unknownArtist")} · {album.tracks.length} {t("player.tracksCount")}
          </small>
        </button>
        <span className="album__chevron" aria-hidden="true">
          {open ? "▾" : "▸"}
        </span>
      </div>
      {open && (
        <ol className="album__tracks">
          {album.tracks.map((track, index) => {
            const active = track.id === currentId;
            return (
              <li key={track.id}>
                <button type="button" className={active ? "album-track album-track--active" : "album-track"} onClick={() => onPlay(index)}>
                  <span className="album-track__index">{active ? (playing ? "▶" : "❚❚") : String(track.trackNumber ?? index + 1).padStart(2, "0")}</span>
                  <span className="album-track__title">{track.title}</span>
                  <span className="album-track__meta">{track.format}</span>
                  <span className="album-track__time">{formatDuration(track.durationSeconds)}</span>
                </button>
              </li>
            );
          })}
        </ol>
      )}
    </div>
  );
}

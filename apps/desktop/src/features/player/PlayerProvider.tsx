import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import * as api from "../../lib/api";
import type { LibraryTrack, PlaybackState, RepeatMode } from "../../types";

interface PlayerContextValue {
  /** Tracks in play order. */
  queue: LibraryTrack[];
  current?: LibraryTrack;
  currentIndex: number | null;
  playing: boolean;
  positionSeconds: number;
  durationSeconds: number;
  volume: number;
  shuffle: boolean;
  repeatMode: RepeatMode;
  artworkUrl?: string;
  error?: string;
  /** Replaces the queue and starts at `index`. */
  playQueue: (tracks: LibraryTrack[], index: number) => void;
  toggle: () => void;
  next: () => void;
  previous: () => void;
  seek: (seconds: number) => void;
  setVolume: (value: number) => void;
  toggleShuffle: () => void;
  cycleRepeat: () => void;
  removeAt: (index: number) => void;
  clear: () => void;
}

const PlayerContext = createContext<PlayerContextValue | null>(null);

export function usePlayer(): PlayerContextValue {
  const value = useContext(PlayerContext);
  if (!value) throw new Error("usePlayer must be used inside PlayerProvider");
  return value;
}

const REPEAT_ORDER: RepeatMode[] = ["off", "one", "all"];

/** Restarts the track instead of stepping back when past this point. */
const RESTART_THRESHOLD_SECONDS = 3;

export function PlayerProvider({
  initial,
  library,
  children,
}: {
  initial: PlaybackState;
  /** Latest library scan, used to rehydrate a persisted queue of paths. */
  library: LibraryTrack[];
  children: ReactNode;
}) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [queue, setQueue] = useState<LibraryTrack[]>([]);
  const [currentIndex, setCurrentIndex] = useState<number | null>(null);
  const [playing, setPlaying] = useState(false);
  const [positionSeconds, setPosition] = useState(initial.positionSeconds);
  const [durationSeconds, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(initial.volume);
  const [shuffle, setShuffle] = useState(initial.shuffle);
  const [repeatMode, setRepeatMode] = useState<RepeatMode>(initial.repeatMode);
  const [artworkUrl, setArtworkUrl] = useState<string>();
  const [error, setError] = useState<string>();
  const restored = useRef(false);
  const [checkpoint, setCheckpoint] = useState(0);
  /** Whether the next loaded source should start playing on its own. */
  const wantPlay = useRef(false);
  /** Saved position, applied once to the restored track when it loads. */
  const pendingSeek = useRef<number | null>(null);

  const current = currentIndex == null ? undefined : queue[currentIndex];

  // Rehydrate the persisted queue once the first library scan lands — the saved
  // state is a list of paths, which only becomes tracks after a scan.
  useEffect(() => {
    if (restored.current || library.length === 0 || initial.queueTrackIds.length === 0) return;
    restored.current = true;
    const byId = new Map(library.map((track) => [track.id, track]));
    const restoredQueue = initial.queueTrackIds
      .map((id) => byId.get(id))
      .filter((track): track is LibraryTrack => Boolean(track));
    if (restoredQueue.length === 0) return;
    setQueue(restoredQueue);
    pendingSeek.current = initial.positionSeconds > 1 ? initial.positionSeconds : null;
    setCurrentIndex(
      initial.currentIndex != null && initial.currentIndex < restoredQueue.length
        ? initial.currentIndex
        : 0,
    );
    // Deliberately not auto-playing: restoring a queue should not make noise on
    // launch. The saved position is applied when the source loads.
  }, [library, initial]);

  // Load the current track's source and artwork.
  useEffect(() => {
    const audio = audioRef.current;
    if (!audio) return;
    if (!current) {
      wantPlay.current = false;
      if (audio.hasAttribute("src")) {
        audio.pause();
        audio.removeAttribute("src");
        audio.load();
      }
      setPlaying(false);
      setPosition(0);
      setDuration(0);
      setArtworkUrl(undefined);
      return;
    }
    audio.pause();
    let stale = false;
    setError(undefined);
    // A new source starts at zero; stale time from the last track must not show.
    setPosition(pendingSeek.current ?? 0);
    setDuration(current.durationSeconds ?? 0);
    void (async () => {
      try {
        const url = await api.playableUrl(current.path);
        if (stale) return;
        if (!url) {
          setError("Playback needs the installed desktop app.");
          return;
        }
        audio.src = url;
        audio.load();
        if (wantPlay.current) await audio.play().catch(() => undefined);
      } catch (reason) {
        if (!stale) setError(reason instanceof Error ? reason.message : String(reason));
      }
      try {
        const art = await api.trackArtworkUrl(current.path);
        if (!stale) setArtworkUrl(art ?? undefined);
      } catch {
        if (!stale) setArtworkUrl(undefined);
      }
    })();
    return () => {
      stale = true;
    };
    // `playing` is intentionally omitted: it must not re-load the source.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current?.path]);

  useEffect(() => {
    if (audioRef.current) audioRef.current.volume = volume;
  }, [volume]);

  const step = useCallback(
    (delta: number) => {
      setCurrentIndex((index) => {
        if (index == null || queue.length === 0) return index;
        const next = index + delta;
        if (next >= queue.length) return repeatMode === "all" ? 0 : index;
        if (next < 0) return repeatMode === "all" ? queue.length - 1 : 0;
        return next;
      });
    },
    [queue.length, repeatMode],
  );

  const next = useCallback(() => step(1), [step]);

  const previous = useCallback(() => {
    const audio = audioRef.current;
    // Matches the near-universal transport convention: restart, then step back.
    if (audio && audio.currentTime > RESTART_THRESHOLD_SECONDS) {
      audio.currentTime = 0;
      return;
    }
    step(-1);
  }, [step]);

  const handleEnded = useCallback(() => {
    const audio = audioRef.current;
    if (repeatMode === "one" && audio) {
      audio.currentTime = 0;
      void audio.play().catch(() => undefined);
      return;
    }
    if (currentIndex != null && currentIndex + 1 >= queue.length && repeatMode !== "all") {
      wantPlay.current = false;
      setPlaying(false);
      return;
    }
    // The element fires "pause" just before "ended"; carry on regardless.
    wantPlay.current = true;
    next();
  }, [currentIndex, next, queue.length, repeatMode]);

  const playQueue = useCallback((tracks: LibraryTrack[], index: number) => {
    // Shuffle is applied to the order stored here, so the queue panel always
    // shows the real play order rather than a hidden permutation.
    pendingSeek.current = null;
    wantPlay.current = true;
    setQueue(tracks);
    setCurrentIndex(index);
    setPlaying(true);
    // Same track again: the source effect will not re-run, so start it here.
    const audio = audioRef.current;
    if (audio && tracks[index]?.path === current?.path) {
      audio.currentTime = 0;
      void audio.play().catch(() => undefined);
    }
  }, [current?.path]);

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !current) return;
    if (audio.paused) {
      wantPlay.current = true;
      void audio.play().catch(() => undefined);
    } else {
      wantPlay.current = false;
      audio.pause();
    }
  }, [current]);

  const value = useMemo<PlayerContextValue>(
    () => ({
      queue,
      current,
      currentIndex,
      playing,
      positionSeconds,
      durationSeconds,
      volume,
      shuffle,
      repeatMode,
      artworkUrl,
      error,
      playQueue,
      toggle,
      next,
      previous,
      seek: (seconds) => {
        if (audioRef.current) {
          audioRef.current.currentTime = seconds;
          setPosition(seconds);
          setCheckpoint((value) => value + 1);
        }
      },
      setVolume: setVolumeState,
      toggleShuffle: () =>
        setShuffle((on) => {
          const nextOn = !on;
          setQueue((tracks) => {
            if (tracks.length < 2) return tracks;
            const pinned = currentIndex == null ? undefined : tracks[currentIndex];
            const rest = tracks.filter((track) => track !== pinned);
            if (nextOn) {
              for (let i = rest.length - 1; i > 0; i -= 1) {
                const j = Math.floor(Math.random() * (i + 1));
                [rest[i], rest[j]] = [rest[j], rest[i]];
              }
            } else {
              rest.sort((a, b) => a.path.localeCompare(b.path));
            }
            const reordered = pinned ? [pinned, ...rest] : rest;
            setCurrentIndex(pinned ? 0 : currentIndex);
            return reordered;
          });
          return nextOn;
        }),
      cycleRepeat: () =>
        setRepeatMode(
          (mode) => REPEAT_ORDER[(REPEAT_ORDER.indexOf(mode) + 1) % REPEAT_ORDER.length],
        ),
      removeAt: (index) =>
        setQueue((tracks) => {
          const trimmed = tracks.filter((_, position) => position !== index);
          setCurrentIndex((active) => {
            if (active == null) return active;
            if (index < active) return active - 1;
            if (index === active) return trimmed.length ? Math.min(active, trimmed.length - 1) : null;
            return active;
          });
          return trimmed;
        }),
      clear: () => {
        wantPlay.current = false;
        pendingSeek.current = null;
        setQueue([]);
        setCurrentIndex(null);
        setPlaying(false);
      },
    }),
    [
      queue,
      current,
      currentIndex,
      playing,
      positionSeconds,
      durationSeconds,
      volume,
      shuffle,
      repeatMode,
      artworkUrl,
      error,
      playQueue,
      toggle,
      next,
      previous,
    ],
  );

  // Save transport changes and explicit seeks without writing every time tick.
  useEffect(() => {
    if (!restored.current && initial.queueTrackIds.length > 0) return;
    const state: PlaybackState = {
      queueTrackIds: queue.map((track) => track.id),
      currentIndex,
      positionSeconds,
      shuffle,
      repeatMode,
      volume,
    };
    const timer = window.setTimeout(() => {
      void api.savePlaybackState(state).catch(() => undefined);
    }, 800);
    return () => window.clearTimeout(timer);
    // Position is captured on pause, seek, and structural changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [queue, currentIndex, shuffle, repeatMode, volume, playing, checkpoint]);

  return (
    <PlayerContext.Provider value={value}>
      <audio
        ref={audioRef}
        preload="metadata"
        onPlay={() => {
          wantPlay.current = true;
          setPlaying(true);
        }}
        onPause={(event) => {
          setPosition(event.currentTarget.currentTime);
          setPlaying(false);
          setCheckpoint((value) => value + 1);
        }}
        onSeeked={(event) => {
          setPosition(event.currentTarget.currentTime);
          setCheckpoint((value) => value + 1);
        }}
        onTimeUpdate={(event) => setPosition(event.currentTarget.currentTime)}
        onDurationChange={(event) => setDuration(event.currentTarget.duration || 0)}
        onLoadedMetadata={(event) => {
          if (pendingSeek.current != null) {
            event.currentTarget.currentTime = pendingSeek.current;
            setPosition(pendingSeek.current);
            pendingSeek.current = null;
          }
        }}
        onEnded={handleEnded}
        onError={() => setError("That track could not be played.")}
      />
      {children}
    </PlayerContext.Provider>
  );
}

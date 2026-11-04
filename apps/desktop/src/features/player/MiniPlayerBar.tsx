import { AsciiArt } from "../../ascii/AsciiArt";
import { cassetteFrames } from "../../ascii/frames/cassette";
import { IconButton } from "../../components";
import { formatDuration } from "../../lib/formats";
import { usePlayer } from "./PlayerProvider";
import "./player.css";

const REPEAT_GLYPH = { off: "↻", one: "↻¹", all: "↻∞" } as const;

/**
 * Always mounted in the shell, so playback survives navigating between views.
 * Renders nothing until a track is loaded.
 */
export function MiniPlayerBar({ onExpand }: { onExpand: () => void }) {
  const player = usePlayer();
  if (!player.current) return null;

  const { current, playing, positionSeconds, durationSeconds } = player;
  const duration = durationSeconds || current.durationSeconds || 0;

  return (
    <div className="mini-player">
      <div className="mini-player__art" aria-hidden="true">
        <AsciiArt
          frameSet={cassetteFrames[playing ? "playing" : "paused"]}
          playing={playing}
          density="compact"
          glow={false}
        />
      </div>

      <button type="button" className="mini-player__meta" onClick={onExpand}>
        <strong>{current.title}</strong>
        <small>{current.artist || current.album || "Local audio"}</small>
      </button>

      <div className="mini-player__transport">
        <IconButton glyph="⏮" label="Previous track" onClick={player.previous} />
        <IconButton
          glyph={playing ? "❚❚" : "▶"}
          label={playing ? "Pause" : "Play"}
          accent
          onClick={player.toggle}
        />
        <IconButton glyph="⏭" label="Next track" onClick={player.next} />
      </div>

      <div className="mini-player__seek">
        <span className="mini-player__time">{formatDuration(positionSeconds)}</span>
        <input
          type="range"
          min={0}
          max={Math.max(duration, 1)}
          step={0.5}
          value={Math.min(positionSeconds, duration || 0)}
          aria-label="Seek"
          onChange={(event) => player.seek(Number(event.target.value))}
        />
        <span className="mini-player__time">{formatDuration(duration)}</span>
      </div>

      <div className="mini-player__modes">
        <button
          type="button"
          className={player.shuffle ? "mode mode--on" : "mode"}
          aria-pressed={player.shuffle}
          title="Shuffle"
          onClick={player.toggleShuffle}
        >
          ⤨
        </button>
        <button
          type="button"
          className={player.repeatMode === "off" ? "mode" : "mode mode--on"}
          title={`Repeat: ${player.repeatMode}`}
          onClick={player.cycleRepeat}
        >
          {REPEAT_GLYPH[player.repeatMode]}
        </button>
        <input
          type="range"
          className="mini-player__volume"
          min={0}
          max={1}
          step={0.05}
          value={player.volume}
          aria-label="Volume"
          onChange={(event) => player.setVolume(Number(event.target.value))}
        />
      </div>
    </div>
  );
}

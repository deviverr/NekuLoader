import { AsciiArt } from "../../ascii/AsciiArt";
import { cassetteFrames } from "../../ascii/frames/cassette";
import { Empty, IconButton, Notice, Panel, SectionHeading } from "../../components";
import { formatDuration } from "../../lib/formats";
import { usePlayer } from "./PlayerProvider";
import "./player.css";

export function NowPlayingView({ onBack }: { onBack: () => void }) {
  const player = usePlayer();

  if (!player.current) {
    return (
      <Panel>
        <Empty
          title="Nothing is playing"
          text="Pick a track in Library to start the player."
        />
      </Panel>
    );
  }

  const { current, artworkUrl, playing } = player;
  const duration = player.durationSeconds || current.durationSeconds || 0;

  return (
    <div className="view-stack">
      <button type="button" className="button" onClick={onBack}>
        ← Back to library
      </button>

      {player.error && <Notice tone="error">{player.error}</Notice>}

      <div className="now-playing">
        <Panel>
          <div className="now-playing__stage">
            {artworkUrl ? (
              <img className="now-playing__cover" src={artworkUrl} alt="" />
            ) : (
              <AsciiArt
                frameSet={cassetteFrames[playing ? "playing" : "paused"]}
                playing={playing}
                className="ascii--hero"
                label="Cassette"
              />
            )}
            <h2 className="now-playing__title">{current.title}</h2>
            <p className="muted">{current.artist || "Unknown artist"}</p>
            {current.album && <p className="muted">{current.album}</p>}

            <div className="mini-player__seek" style={{ width: "100%" }}>
              <span className="mini-player__time">
                {formatDuration(player.positionSeconds)}
              </span>
              <input
                type="range"
                min={0}
                max={Math.max(duration, 1)}
                step={0.5}
                value={Math.min(player.positionSeconds, duration || 0)}
                aria-label="Seek"
                onChange={(event) => player.seek(Number(event.target.value))}
              />
              <span className="mini-player__time">{formatDuration(duration)}</span>
            </div>

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
          </div>
        </Panel>

        <Panel>
          <SectionHeading
            eyebrow="Play order"
            title="Queue"
            aside={
              <button type="button" className="button" onClick={player.clear}>
                Clear
              </button>
            }
          />
          <div className="play-queue">
            {player.queue.map((track, index) => (
              <div
                key={`${track.id}-${index}`}
                className={
                  index === player.currentIndex
                    ? "play-queue__row play-queue__row--active"
                    : "play-queue__row"
                }
              >
                <span className="play-queue__index">
                  {index === player.currentIndex ? "▶" : String(index + 1).padStart(2, "0")}
                </span>
                <button
                  type="button"
                  className="play-queue__title"
                  style={{ border: 0, background: "none", color: "inherit", textAlign: "left" }}
                  onClick={() => player.playQueue(player.queue, index)}
                >
                  {track.title}
                </button>
                <span className="mini-player__time">
                  {formatDuration(track.durationSeconds)}
                </span>
                <IconButton
                  glyph="✕"
                  label={`Remove ${track.title} from queue`}
                  onClick={() => player.removeAt(index)}
                />
              </div>
            ))}
          </div>
        </Panel>
      </div>
    </div>
  );
}

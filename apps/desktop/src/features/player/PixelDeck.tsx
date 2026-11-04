import { useEffect, useRef } from "react";

/**
 * Pixel graphics for the player: a vinyl record that spins while music plays,
 * and a little equalizer. Both are drawn on tiny canvases and scaled up with
 * `image-rendering: pixelated`, matching the sky and the cat.
 *
 * The equalizer is decorative — it animates with playback state rather than
 * analysing the audio, so it never touches the audio routing.
 */

function token(name: string, fallback: string): string {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
}

const DISC = 64;

export function PixelVinyl({ spinning }: { spinning: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const spinningRef = useRef(spinning);
  spinningRef.current = spinning;

  useEffect(() => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    let angle = 0;
    let raf = 0;
    let last = 0;
    const c = DISC / 2;

    const draw = () => {
      const ink = token("--ascii-fg", "#ffffff");
      const shade = token("--bg-0", "#000000");
      const groove = token("--surface-raised", "#161616");
      ctx.clearRect(0, 0, DISC, DISC);
      for (let y = 0; y < DISC; y++) {
        for (let x = 0; x < DISC; x++) {
          const dx = x - c + 0.5;
          const dy = y - c + 0.5;
          const d = Math.sqrt(dx * dx + dy * dy);
          if (d > c - 1) continue;
          let fill = shade;
          let alpha = 1;
          if (d > c - 2) {
            fill = ink;
            alpha = 0.55;
          } else if (d < 3) {
            fill = shade; // spindle hole
          } else if (d < 12) {
            fill = ink; // label
            alpha = d > 11 ? 0.6 : 0.92;
          } else if (Math.round(d) % 3 === 0) {
            fill = groove;
          }
          // Two glints opposite each other sweep round as it spins.
          if (d >= 12 && d < c - 2) {
            const a = Math.atan2(dy, dx) - angle;
            const glint = Math.abs(Math.sin(a));
            if (glint < 0.05 && Math.round(d) % 3 !== 0) {
              fill = ink;
              alpha = Math.cos(a) > 0 ? 0.28 : 0.14;
            }
          }
          ctx.globalAlpha = alpha;
          ctx.fillStyle = fill;
          ctx.fillRect(x, y, 1, 1);
        }
      }
      // A notch on the label makes the rotation readable.
      ctx.globalAlpha = 1;
      ctx.fillStyle = shade;
      const nx = Math.round(c + Math.cos(angle) * 7);
      const ny = Math.round(c + Math.sin(angle) * 7);
      ctx.fillRect(nx - 1, ny - 1, 2, 2);
    };

    const loop = (now: number) => {
      raf = requestAnimationFrame(loop);
      if (now - last < 1000 / 20) return;
      const dt = last ? (now - last) / 1000 : 0;
      last = now;
      if (spinningRef.current) {
        // 33⅓ rpm, quantised to the frame rate for a stepped, pixel feel.
        angle += dt * ((33.3 / 60) * Math.PI * 2);
        draw();
      }
    };
    draw();
    raf = requestAnimationFrame(loop);
    const observer = new MutationObserver(draw);
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme", "style"] });
    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
    };
  }, []);

  return <canvas ref={canvasRef} className="pixel-vinyl" width={DISC} height={DISC} aria-hidden="true" />;
}

const BARS = 24;
const LEVELS = 10;

export function PixelEqualizer({ playing }: { playing: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const playingRef = useRef(playing);
  playingRef.current = playing;

  useEffect(() => {
    const ctx = canvasRef.current?.getContext("2d");
    if (!ctx) return;
    const width = BARS * 3 - 1;
    const levels = Array.from({ length: BARS }, () => 1);
    const targets = Array.from({ length: BARS }, () => 1);

    const draw = () => {
      const ink = token("--ascii-fg", "#ffffff");
      ctx.clearRect(0, 0, width, LEVELS * 2);
      levels.forEach((level, bar) => {
        for (let step = 0; step < Math.round(level); step++) {
          // Top blocks are brighter, like an LED meter.
          ctx.globalAlpha = 0.35 + (step / LEVELS) * 0.65;
          ctx.fillStyle = ink;
          ctx.fillRect(bar * 3, (LEVELS - 1 - step) * 2, 2, 1);
        }
      });
      ctx.globalAlpha = 1;
    };

    const interval = window.setInterval(() => {
      for (let bar = 0; bar < BARS; bar++) {
        if (playingRef.current) {
          if (Math.random() < 0.35) {
            // Low bars carry more energy, like most music.
            const bias = 1 - bar / BARS / 1.6;
            targets[bar] = 1 + Math.random() * (LEVELS - 1) * bias;
          }
        } else {
          targets[bar] = 1;
        }
        levels[bar] += (targets[bar] - levels[bar]) * 0.45;
      }
      draw();
    }, 90);
    draw();
    return () => window.clearInterval(interval);
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="pixel-eq"
      width={BARS * 3 - 1}
      height={LEVELS * 2}
      aria-hidden="true"
    />
  );
}

/** 7×7 pixel glyphs for the transport, drawn as crisp SVG blocks. */
const ICONS: Record<string, string[]> = {
  play: ["1000000", "1100000", "1110000", "1111000", "1110000", "1100000", "1000000"],
  pause: ["1100110", "1100110", "1100110", "1100110", "1100110", "1100110", "1100110"],
  prev: ["1000001", "1000011", "1000111", "1001111", "1000111", "1000011", "1000001"],
  next: ["1000001", "1100001", "1110001", "1111001", "1110001", "1100001", "1000001"],
  shuffle: ["0000010", "1100111", "0011010", "0001000", "0010110", "1100111", "0000010"],
  repeat: ["0111110", "1000001", "1000001", "1000101", "1000011", "0000111", "0000000"],
  note: ["0001100", "0001110", "0001011", "0001001", "0111000", "1111000", "0110000"],
  volume: ["0001000", "0011010", "1111001", "1111001", "1111001", "0011010", "0001000"],
  repeatOne: ["0111110", "1000001", "1001001", "1011001", "1001011", "0001111", "0000000"],
};

export function PixelIcon({ name, size = 14 }: { name: keyof typeof ICONS | string; size?: number }) {
  const rows = ICONS[name] ?? ICONS.play;
  return (
    <svg
      className="pixel-icon"
      width={size}
      height={size}
      viewBox="0 0 7 7"
      shapeRendering="crispEdges"
      aria-hidden="true"
    >
      {rows.flatMap((row, y) =>
        [...row].map((cell, x) => (cell === "1" ? <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill="currentColor" /> : null)),
      )}
    </svg>
  );
}

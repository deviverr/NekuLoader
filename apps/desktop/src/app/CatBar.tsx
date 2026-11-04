import { useEffect, useRef, useState } from "react";
import { useTheme } from "../theme/ThemeProvider";
import { PixelCat } from "./PixelCat";

/**
 * Neku along the bottom edge of the window.
 *
 * While anything is downloading he runs left to right and loops around; when
 * the queue goes quiet he settles wherever he stopped and sleeps. The travel is
 * a transform on a wrapper rather than a re-render per step, so the run cycle
 * and the travel stay independent of each other.
 */
/** Where the cat sits when nothing is running, as a share of the strip. */
const REST_POSITION = 6;

export function CatBar({ running }: { running: boolean }) {
  const { motionScale } = useTheme();
  const [offset, setOffset] = useState(REST_POSITION);
  const wasRunning = useRef(false);

  useEffect(() => {
    // Coming to a stop, settle somewhere sensible rather than half off-screen.
    if (!running) {
      if (wasRunning.current) setOffset((current) => Math.min(Math.max(current, 4), 72));
      wasRunning.current = false;
      return;
    }
    wasRunning.current = true;
    if (motionScale === 0) return;

    let raf = 0;
    let last = performance.now();
    const step = (now: number) => {
      const delta = now - last;
      last = now;
      // Percent of the strip per second, scaled by the theme's motion setting.
      // Wraps past 100 so he leaves the right edge before reappearing left.
      setOffset((current) => (current + (delta / 1000) * 16 * motionScale) % 116);
      raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
    return () => cancelAnimationFrame(raf);
  }, [running, motionScale]);

  return (
    <div className="catbar" aria-hidden="true">
      <div
        className="catbar__travel"
        // Offset is a share of the strip, so travel is independent of art width.
        style={{ transform: `translate3d(${offset}cqw, 0, 0)` }}
      >
        <PixelCat pose={running ? "run" : "sleep"} playing={motionScale > 0} />
      </div>
      <div className="catbar__floor" />
    </div>
  );
}

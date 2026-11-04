import { useEffect, useState } from "react";
import { useTheme, type AsciiDensity } from "../theme/ThemeProvider";
import { DEFAULT_FRAME_INTERVAL_MS, type AsciiFrameSet } from "./types";

/** Picks the variant a density asks for, falling back when it was not drawn. */
export function pickVariant(frameSet: AsciiFrameSet, density: AsciiDensity): string[] {
  if (density === "compact" && frameSet.compact?.length) return frameSet.compact;
  return frameSet.full;
}

/**
 * Advances a frame set on a timer.
 *
 * The interval is divided by the theme's motion scale, so the Legacy skin
 * animates more slowly from the same source art, and a motion scale of 0
 * (reduced motion) holds the first frame without ever starting a timer.
 */
export function useAsciiFrames(
  frameSet: AsciiFrameSet,
  { playing = true, density }: { playing?: boolean; density?: AsciiDensity } = {},
): string {
  const theme = useTheme();
  const resolved = density ?? theme.density;
  const variant = pickVariant(frameSet, resolved);
  const [index, setIndex] = useState(0);

  // Frame counts differ between variants, so never index past the new array.
  useEffect(() => setIndex(0), [frameSet, resolved]);

  const { motionScale } = theme;
  const loop = frameSet.loop ?? true;
  const interval = (frameSet.frameIntervalMs ?? DEFAULT_FRAME_INTERVAL_MS) / motionScale;
  const animating = playing && motionScale > 0 && variant.length > 1;

  useEffect(() => {
    if (!animating) return;
    const timer = window.setInterval(() => {
      setIndex((current) => {
        const next = current + 1;
        if (next < variant.length) return next;
        return loop ? 0 : current;
      });
    }, interval);
    return () => window.clearInterval(timer);
  }, [animating, interval, loop, variant.length]);

  return variant[Math.min(index, variant.length - 1)] ?? "";
}

import type { AsciiDensity } from "../theme/ThemeProvider";
import { useAsciiFrames } from "./useAsciiFrames";
import type { AsciiFrameSet } from "./types";

interface AsciiArtProps {
  frameSet: AsciiFrameSet;
  /** Pause the cycle without unmounting — used when a job or track stops. */
  playing?: boolean;
  /** Force a variant; defaults to whatever the active theme asks for. */
  density?: AsciiDensity;
  /** Adds the theme's glow. Off for dense backdrops where it muddies the art. */
  glow?: boolean;
  /** Screen-reader label. The art itself is always hidden from the tree. */
  label?: string;
  className?: string;
}

/**
 * Renders one frame of hand-authored ASCII art.
 *
 * The art sizes itself from its container rather than a fixed font size: the
 * wrapper is a CSS container and the `<pre>` uses `cqw` units, so a 50-column
 * piece stays whole down to the 820px minimum window width instead of needing
 * per-breakpoint rules. Colour comes entirely from theme tokens, so call sites
 * never hardcode one.
 */
export function AsciiArt({
  frameSet,
  playing = true,
  density,
  glow = true,
  label,
  className,
}: AsciiArtProps) {
  const frame = useAsciiFrames(frameSet, { playing, density });
  const classes = ["ascii", glow ? "ascii--glow" : "", className ?? ""]
    .filter(Boolean)
    .join(" ");

  return (
    <div className={classes} role={label ? "img" : undefined} aria-label={label}>
      <pre aria-hidden="true">{frame}</pre>
    </div>
  );
}

import type { AsciiFrameSet } from "../types";

/**
 * The NekuLoader wordmark, set in a 5-row block face. Static art — the splash
 * screen animates it with a CSS reveal rather than by swapping frames, so the
 * letters never reflow.
 */
export const wordmarkFrames: AsciiFrameSet = {
  full: [
    `█  █ ████ █  █ █  █ █    ████ ████ ███  ████ ████
██ █ █    █ █  █  █ █    █  █ █  █ █  █ █    █  █
█ ██ ███  ██   █  █ █    █  █ ████ █  █ ███  ████
█  █ █    █ █  █  █ █    █  █ █  █ █  █ █    █ █
█  █ ████ █  █ ████ ████ ████ █  █ ███  ████ █  █`,
  ],
  compact: [
    `╔═╡ NEKULOADER ╞═╗
╚════════════════╝`,
  ],
};

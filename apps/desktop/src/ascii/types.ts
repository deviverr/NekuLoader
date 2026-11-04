/**
 * Contract for the hand-authored ASCII art the interface is built from.
 *
 * Every frame is a plain multi-line string of monospace glyphs. Animation is
 * frame swapping, not procedural rendering, so the art stays fully art-directed
 * and readable in source.
 *
 * Only ASCII, Block Elements (U+2580–U+259F) and Box Drawing (U+2500–U+257F)
 * are used, because those are the ranges monospace fonts reliably render at
 * exactly one cell wide. A proportional or double-width glyph would shear every
 * row below it.
 */
export interface AsciiFrameSet {
  /** One entry renders as static art; several cycle. */
  full: string[];
  /**
   * Smaller variant used when the theme asks for reduced density (the Legacy
   * skin) or the container is too narrow for the full piece. Falls back to
   * `full` when absent.
   */
  compact?: string[];
  /** Milliseconds per frame before the theme's motion scale is applied. */
  frameIntervalMs?: number;
  /** When false the cycle stops on its last frame instead of repeating. */
  loop?: boolean;
}

export type AsciiFrameSets<Key extends string> = Record<Key, AsciiFrameSet>;

export const DEFAULT_FRAME_INTERVAL_MS = 600;

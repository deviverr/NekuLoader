# ASCII art in NekuLoader 3.2

The interface is built around hand-authored ASCII art. Frames are plain strings
in `src/ascii/frames/`; animation is frame swapping, never procedural rendering.

## Character rules

Art frames may only use:

- ASCII
- Block Elements, `U+2580`–`U+259F` (`█ ▓ ▒ ░ ▀ ▄ ▌ ▐ ▖ ▗ ▘ ▝ ▙ ▚ ▛ ▜ ▞ ▟`)
- Box Drawing, `U+2500`–`U+257F` (`─ │ ┌ ┐ └ ┘ ═ ║ ╔ ╗ ╚ ╝ ╱ ╲ ╳ ╡ ╞`)

These render at exactly one cell wide in every monospace font we target. Emoji,
geometric shapes (`▶ ◀ ●`), and CJK are **not** safe inside a frame — a single
double-width glyph shears every row beneath it.

Interface chrome outside `<pre>` (nav glyphs, button markers) is not held to
this rule, since it is laid out with flexbox rather than character cells.

## Tone

Fur and large fills use the *dimmest* tone (`▒`), so solid `█` reads as a
highlight. That is why the cat's eyes glow: they are the only full-density
glyphs on the face. Inverting this makes the art turn into a silhouette.

## Frame sets

Each set is an `AsciiFrameSet` (`src/ascii/types.ts`):

```ts
{
  full: string[];       // one entry = static, several = a cycle
  compact?: string[];   // smaller variant for the Legacy skin / narrow panes
  frameIntervalMs?: number;
  loop?: boolean;
}
```

`compact` is required in practice: the Legacy skin sets `--ascii-density:
compact`, and without it the full piece is used at a smaller font size instead,
which turns dense art into mush.

## Sizing

`<AsciiArt>` never sets an absolute font size. The wrapper is a CSS container and
the `<pre>` uses `cqw`, so a 50-column piece stays whole as its box shrinks.
`--ascii-line-height` is pulled to ~0.62 so character cells come out near-square;
at the default 1.2 the art renders stretched to about double height.

## Redrawing the mascot

The cat is symmetric, so it is authored as a **left half** and mirrored. Drawing
both halves by hand is how alignment drifts. A minimal generator:

```js
const MATERIAL = { H: "▓", F: "▒", E: "░", M: "░", n: "█", ".": " " };
const half = ["....................HHHH", /* … 24 columns per row … */];
const mirror = (row) => [...row, ...[...row].reverse()];
const art = half.map(mirror)
  .map((row) => row.map((c) => (c === "o" ? eye : MATERIAL[c] ?? c)).join(""))
  .join("\n");
```

`o` marks an eye slot so one map yields open, blinking, and closed variants.
Render to a terminal to check it before pasting the result into `cat.ts`.

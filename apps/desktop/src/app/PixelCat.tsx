import { useEffect, useMemo, useRef } from "react";

/**
 * Neku, NekuLoader's own pixel cat.
 *
 * Every frame is built procedurally on a small grid — body, head, ears, legs,
 * tail — and then outlined automatically, which keeps the silhouette clean and
 * consistent between poses. Rendered to a canvas and scaled with
 * `image-rendering: pixelated`.
 */
export type CatPose = "run" | "sleep" | "pet";

const W = 28;
const H = 18;

/** Pixel kinds: 0 empty, 1 fur, 2 shade, 3 detail (eyes), 4 nose, 5 outline. */
type Grid = number[][];

function blank(): Grid {
  return Array.from({ length: H }, () => Array<number>(W).fill(0));
}

function rect(grid: Grid, x0: number, y0: number, x1: number, y1: number, value = 1) {
  for (let y = y0; y <= y1; y++)
    for (let x = x0; x <= x1; x++) if (grid[y]?.[x] !== undefined) grid[y][x] = value;
}

function set(grid: Grid, x: number, y: number, value: number) {
  if (grid[y]?.[x] !== undefined) grid[y][x] = value;
}

/** Turns every empty pixel touching fur into outline. */
function outline(grid: Grid): Grid {
  const out = grid.map((row) => [...row]);
  for (let y = 0; y < H; y++)
    for (let x = 0; x < W; x++) {
      if (grid[y][x] !== 0) continue;
      const near = [
        [0, 1],
        [0, -1],
        [1, 0],
        [-1, 0],
      ].some(([dx, dy]) => (grid[y + dy]?.[x + dx] ?? 0) > 0 && grid[y + dy][x + dx] < 5);
      if (near) out[y][x] = 5;
    }
  return out;
}

function head(grid: Grid, x: number, y: number, eyesOpen: boolean) {
  rect(grid, x, y + 2, x + 7, y + 7);
  rect(grid, x + 1, y + 1, x + 6, y + 1);
  // Ears.
  set(grid, x + 1, y, 1);
  set(grid, x + 1, y - 1, 1);
  set(grid, x + 2, y, 1);
  set(grid, x + 6, y, 1);
  set(grid, x + 6, y - 1, 1);
  set(grid, x + 5, y, 1);
  set(grid, x + 2, y + 1, 4);
  set(grid, x + 5, y + 1, 4);
  // Face.
  if (eyesOpen) {
    set(grid, x + 2, y + 4, 3);
    set(grid, x + 5, y + 4, 3);
  } else {
    set(grid, x + 2, y + 4, 3);
    set(grid, x + 1, y + 4, 3);
    set(grid, x + 5, y + 4, 3);
    set(grid, x + 6, y + 4, 3);
  }
  set(grid, x + 3, y + 5, 4);
  set(grid, x + 4, y + 5, 4);
  // Cheek shade.
  rect(grid, x + 1, y + 7, x + 6, y + 7, 2);
}

function runFrame(step: number): Grid {
  const g = blank();
  const bob = step % 2 === 0 ? 0 : -1;
  // Body.
  rect(g, 5, 8 + bob, 19, 12 + bob);
  rect(g, 6, 13 + bob, 18, 13 + bob, 2);
  head(g, 17, 3 + bob, true);
  // Legs: front and back pairs swing in opposite phase.
  const swing = [
    [0, 2],
    [1, 1],
    [2, 0],
    [1, 1],
  ][step % 4];
  const legs = [
    [6 - swing[0], 14],
    [9 + swing[0], 14],
    [15 - swing[1], 14],
    [18 + swing[1], 14],
  ];
  for (const [lx, ly] of legs) rect(g, lx, ly + bob, lx + 1, ly + 2 + bob);
  // Tail, waving with the stride.
  const tailY = [7, 6, 5, 6][step % 4] + bob;
  set(g, 4, 9 + bob, 1);
  set(g, 3, 8 + bob, 1);
  set(g, 2, tailY, 1);
  set(g, 1, tailY - 1, 1);
  set(g, 2, tailY + 1, 1);
  return outline(g);
}

function sleepFrame(breath: number): Grid {
  const g = blank();
  const rise = breath % 2 === 0 ? 0 : -1;
  // A curled loaf with the head resting on the front paws.
  rect(g, 4, 10 + rise, 20, 15);
  rect(g, 5, 9 + rise, 19, 9 + rise);
  rect(g, 5, 16, 19, 16, 2);
  head(g, 16, 7, false);
  // Tail wrapped along the front.
  rect(g, 3, 15, 13, 15, 2);
  set(g, 2, 14, 1);
  set(g, 2, 15, 1);
  return outline(g);
}

function petFrame(step: number): Grid {
  const g = blank();
  rect(g, 4, 10, 20, 15);
  rect(g, 5, 9, 19, 9);
  rect(g, 5, 16, 19, 16, 2);
  head(g, 16, 5, false);
  // A lifted tail and closed eyes while enjoying a scratch.
  const lift = step % 2;
  rect(g, 2, 9 - lift, 3, 14);
  rect(g, 3, 7 - lift, 5, 8 - lift);
  return outline(g);
}

function colors() {
  const style = getComputedStyle(document.documentElement);
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    1: read("--ascii-fg", "#ffffff"),
    2: read("--text-muted", "#a8a8a8"),
    3: read("--bg-0", "#000000"),
    4: read("--cat-accent", read("--text-faint", "#6b6b6b")),
    5: read("--bg-0", "#000000"),
  } as Record<number, string>;
}

/** The little "z" that rises above a sleeping cat. */
const Z = [
  [1, 1, 1],
  [0, 0, 1],
  [0, 1, 0],
  [1, 0, 0],
  [1, 1, 1],
];

export function PixelCat({ pose, scale = 4, playing = true }: { pose: CatPose; scale?: number; playing?: boolean }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const frames = useMemo(
    () => pose === "run" ? [0, 1, 2, 3].map(runFrame) : [0, 1].map(pose === "pet" ? petFrame : sleepFrame),
    [pose],
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !ctx) return;
    let palette = colors();
    let frame = 0;
    let tick = 0;

    const render = () => {
      ctx.clearRect(0, 0, W, H + 6);
      const grid = frames[frame % frames.length];
      for (let y = 0; y < H; y++)
        for (let x = 0; x < W; x++) {
          const kind = grid[y][x];
          if (!kind) continue;
          ctx.globalAlpha = kind === 5 ? 0.9 : 1;
          ctx.fillStyle = palette[kind];
          ctx.fillRect(x, y + 6, 1, 1);
        }
      if (pose === "sleep") {
        // Two z's drifting up and fading, offset in time.
        for (const offset of [0, 6]) {
          const t = (tick + offset) % 12;
          ctx.globalAlpha = Math.max(0, 1 - t / 12);
          ctx.fillStyle = palette[1];
          const zx = 22 + Math.floor(t / 4);
          const zy = 6 - Math.floor(t / 2);
          Z.forEach((row, dy) => row.forEach((on, dx) => on && ctx.fillRect(zx + dx, zy + dy, 1, 1)));
        }
      }
      ctx.globalAlpha = 1;
    };

    render();
    if (!playing) return;
    const interval = window.setInterval(
      () => {
        tick += 1;
        if (pose === "run" || tick % 4 === 0) frame += 1;
        palette = colors();
        render();
      },
      pose === "run" ? 110 : 280,
    );
    return () => window.clearInterval(interval);
  }, [frames, pose, playing]);

  return (
    <canvas
      ref={canvasRef}
      className="pixel-cat"
      width={W}
      height={H + 6}
      style={{ width: W * scale, height: (H + 6) * scale }}
      aria-hidden="true"
    />
  );
}

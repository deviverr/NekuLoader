import { useEffect, useRef } from "react";
import type { AppSettings } from "../types";

/**
 * The pixel sky behind every view.
 *
 * Drawn on a canvas at a fraction of the window resolution and scaled up with
 * `image-rendering: pixelated`, so every star and planet lands on a chunky,
 * crisp pixel grid. Stars fall top-to-bottom by default (the starAngle setting
 * still steers them) in three depth layers; the nearest layer twinkles as
 * little plus-shaped sparkles. Cached dithered planets drift independently.
 */
const PIXEL = 3;
const FRAME_MS = 1000 / 30;

interface Star {
  x: number;
  y: number;
  layer: 0 | 1 | 2;
  phase: number;
}

interface Planet {
  /** Centre as a share of the canvas, radius in sky pixels. */
  cx: number;
  cy: number;
  r: number;
  ringed: boolean;
  banded: boolean;
}

const PLANETS: Planet[] = [
  { cx: 0.07, cy: 0.18, r: 24, ringed: false, banded: true },
  { cx: 0.93, cy: 0.72, r: 12, ringed: true, banded: false },
  { cx: 0.88, cy: 0.16, r: 6, ringed: false, banded: false },
  { cx: 0.16, cy: 0.9, r: 16, ringed: false, banded: true },
];

/** Planets sit well behind the content. */
const PLANET_ALPHA = 0.8;

/** 4×4 ordered-dither threshold map. */
const BAYER = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5].map((v) => (v + 0.5) / 16);

function themeColors() {
  const style = getComputedStyle(document.documentElement);
  const read = (name: string, fallback: string) => style.getPropertyValue(name).trim() || fallback;
  return {
    star: read("--star", "#ffffff"),
    alpha: Number.parseFloat(read("--star-alpha", "0.5")) || 0.5,
    surface: read("--surface-raised", "#161616"),
    text: read("--text", "#f2f2f2"),
  };
}

function drawPlanet(ctx: CanvasRenderingContext2D, planet: Planet, w: number, h: number, colors: ReturnType<typeof themeColors>) {
  const cx = Math.round(planet.cx * w);
  const cy = Math.round(planet.cy * h);
  const r = planet.r;
  for (let y = -r; y <= r; y++) {
    for (let x = -r; x <= r; x++) {
      const d = Math.sqrt(x * x + y * y);
      if (d > r) continue;
      // Light from the upper left; the dither turns the gradient into pixels.
      const light = 1 - ((x + y) / (r * 2) + 0.5) * 0.9 - (d / r) * 0.25;
      const threshold = BAYER[((y + r) % 4) * 4 + ((x + r) % 4)];
      let fill: string | null = null;
      if (d > r - 1) fill = colors.text;
      else if (light > threshold + 0.25) fill = colors.text;
      else if (light > threshold - 0.15) fill = colors.surface;
      if (planet.banded && fill === colors.surface && Math.abs(((y + r) % 7) - 3) === 0) fill = null;
      if (!fill) continue;
      ctx.globalAlpha = (d > r - 1 ? 0.4 : fill === colors.text ? 0.12 : 0.32) * PLANET_ALPHA;
      ctx.fillStyle = fill;
      ctx.fillRect(cx + x, cy + y, 1, 1);
    }
  }
  if (planet.ringed) {
    ctx.globalAlpha = 0.3 * PLANET_ALPHA;
    ctx.fillStyle = colors.text;
    const rx = r * 2;
    const ry = Math.max(2, Math.round(r * 0.45));
    for (let t = 0; t < Math.PI * 2; t += 0.5 / rx) {
      const x = Math.round(Math.cos(t) * rx);
      const y = Math.round(Math.sin(t) * ry - Math.cos(t) * ry * 0.35);
      // The far half of the ring passes behind the planet.
      if (Math.sin(t) < 0 && x * x + y * y < r * r) continue;
      ctx.fillRect(cx + x, cy + y, 1, 1);
    }
  }
  ctx.globalAlpha = 1;
}

export function PixelSky({ settings }: { settings: AppSettings }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const settingsRef = useRef(settings);
  settingsRef.current = settings;

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    let width = 0;
    let height = 0;
    let stars: Star[] = [];
    let planets: HTMLCanvasElement[] = [];
    let planetTime = 0;
    let colors = themeColors();
    let raf = 0;
    let last = 0;

    const buildPlanets = () => {
      planets = PLANETS.map((planet) => {
        const sprite = document.createElement("canvas");
        sprite.width = sprite.height = planet.r * 4 + 4;
        const pctx = sprite.getContext("2d");
        if (pctx) drawPlanet(pctx, { ...planet, cx: 0.5, cy: 0.5 }, sprite.width, sprite.height, colors);
        return sprite;
      });
    };

    const resize = () => {
      width = Math.max(1, Math.ceil(window.innerWidth / PIXEL));
      height = Math.max(1, Math.ceil(window.innerHeight / PIXEL));
      canvas.width = width;
      canvas.height = height;
      const count = Math.round((width * height) / 260);
      stars = Array.from({ length: count }, (_, index) => ({
        x: Math.random() * width,
        y: Math.random() * height,
        layer: (index % 10 === 0 ? 2 : index % 3 === 0 ? 1 : 0) as Star["layer"],
        phase: Math.random() * Math.PI * 2,
      }));
      buildPlanets();
    };

    const draw = (now: number) => {
      const current = settingsRef.current;
      const moving = current.animateBackground && !current.reducedMotion;
      const dt = last ? Math.min(now - last, 100) / 1000 : 0;
      last = now;

      ctx.clearRect(0, 0, width, height);
      if (moving) planetTime += dt * Math.max(0, current.snowSpeed);
      planets.forEach((sprite, index) => {
        const planet = PLANETS[index];
        const phase = index * 1.7;
        const orbit = planetTime * (0.24 + index * 0.035);
        // Subtract the starting phase so reduced motion keeps the original layout.
        const dx = (Math.sin(orbit + phase) - Math.sin(phase)) * Math.min(12, width * 0.025);
        const dy = (Math.cos(orbit * 0.8 + phase) - Math.cos(phase)) * Math.min(9, height * 0.025);
        ctx.drawImage(sprite, Math.round(planet.cx * width - sprite.width / 2 + dx), Math.round(planet.cy * height - sprite.height / 2 + dy));
      });

      const angle = ((current.starAngle ?? 180) * Math.PI) / 180;
      const dirX = Math.sin(angle);
      const dirY = -Math.cos(angle);
      const speed = Math.max(current.snowSpeed, 0.05) * 9;
      const bold = current.snowBoldness >= 1.4;
      const baseAlpha = current.snowTransparency * Math.min(1, colors.alpha * 2);

      ctx.fillStyle = colors.star;
      for (const star of stars) {
        if (moving) {
          const layerSpeed = speed * (0.45 + star.layer * 0.55);
          star.x = (star.x + dirX * layerSpeed * dt + width) % width;
          star.y = (star.y + dirY * layerSpeed * dt + height) % height;
          star.phase += dt * (1.5 + star.layer);
        }
        const x = Math.floor(star.x);
        const y = Math.floor(star.y);
        const twinkle = 0.65 + Math.sin(star.phase) * 0.35;
        ctx.globalAlpha = baseAlpha * (star.layer === 0 ? 0.45 : star.layer === 1 ? 0.75 : twinkle);
        ctx.fillRect(x, y, 1, 1);
        if (star.layer === 2 || bold) {
          // A plus-shaped sparkle; the arms fade in and out with the twinkle.
          ctx.globalAlpha *= star.layer === 2 ? twinkle * 0.8 : 0.4;
          ctx.fillRect(x - 1, y, 1, 1);
          ctx.fillRect(x + 1, y, 1, 1);
          ctx.fillRect(x, y - 1, 1, 1);
          ctx.fillRect(x, y + 1, 1, 1);
        }
      }
      ctx.globalAlpha = 1;
    };

    const loop = (now: number) => {
      raf = requestAnimationFrame(loop);
      if (now - last < FRAME_MS) return;
      draw(now);
    };

    resize();
    draw(performance.now());
    raf = requestAnimationFrame(loop);

    // Theme switches change the CSS tokens; re-read them and redraw planets.
    const observer = new MutationObserver(() => {
      colors = themeColors();
      buildPlanets();
    });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme", "style"] });
    window.addEventListener("resize", resize);
    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
      window.removeEventListener("resize", resize);
    };
  }, []);

  return <canvas ref={canvasRef} className="pixel-sky" aria-hidden="true" />;
}

import { useEffect, useRef, useState, type PointerEvent } from "react";
import { useTheme } from "../theme/ThemeProvider";
import { PixelCat } from "./PixelCat";

const POSITION_KEY = "nekuloader.cat-position";
const CAT_WIDTH = 112;
const clamp = (value: number) => Math.min(100, Math.max(0, value));

function savedPosition() {
  try {
    const saved = window.localStorage.getItem(POSITION_KEY);
    const value = saved === null ? 6 : Number(saved);
    return Number.isFinite(value) ? clamp(value) : 6;
  } catch { return 6; }
}

/** A cat perched on the player; click to pet or drag along the edge. */
export function CatBar({ running }: { running: boolean }) {
  const { motionScale } = useTheme();
  const strip = useRef<HTMLDivElement>(null);
  const drag = useRef<{ pointer: number; x: number; offset: number; moved: boolean } | null>(null);
  const suppressClick = useRef(false);
  const petTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const [offset, setOffset] = useState(savedPosition);
  const [dragging, setDragging] = useState(false);
  const [petted, setPetted] = useState(false);
  const [petCount, setPetCount] = useState(0);

  useEffect(() => {
    try { window.localStorage.setItem(POSITION_KEY, String(offset)); } catch { /* Storage is optional. */ }
  }, [offset]);
  useEffect(() => () => clearTimeout(petTimer.current), []);

  const pet = () => {
    clearTimeout(petTimer.current);
    setPetted(true);
    setPetCount((count) => count + 1);
    petTimer.current = setTimeout(() => setPetted(false), 1800);
  };
  const finishDrag = (event: PointerEvent<HTMLButtonElement>) => {
    if (drag.current?.pointer !== event.pointerId) return;
    suppressClick.current = drag.current.moved;
    drag.current = null;
    setDragging(false);
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  };

  return (
    <div className="catbar" ref={strip}>
      <button
        type="button"
        className={`catbar__travel${dragging ? " catbar__travel--dragging" : ""}`}
        style={{ left: `calc(${offset}% - ${offset / 100 * CAT_WIDTH}px)` }}
        aria-label="Pet Neku"
        aria-describedby="cat-instructions"
        title="Click to pet · drag to move · arrow keys to move"
        onPointerDown={(event) => {
          if (event.button !== 0 || drag.current) return;
          suppressClick.current = false;
          drag.current = { pointer: event.pointerId, x: event.clientX, offset, moved: false };
          event.currentTarget.setPointerCapture?.(event.pointerId);
        }}
        onPointerMove={(event) => {
          const start = drag.current;
          if (!start || start.pointer !== event.pointerId) return;
          const delta = event.clientX - start.x;
          if (Math.abs(delta) > 4) start.moved = true;
          if (!start.moved) return;
          setDragging(true);
          const travel = Math.max(1, (strip.current?.getBoundingClientRect().width ?? CAT_WIDTH) - CAT_WIDTH);
          setOffset(clamp(start.offset + delta / travel * 100));
        }}
        onPointerUp={finishDrag}
        onPointerCancel={finishDrag}
        onLostPointerCapture={finishDrag}
        onClick={(event) => {
          if (event.detail === 0 || !suppressClick.current) pet();
          suppressClick.current = false;
        }}
        onKeyDown={(event) => {
          if (!["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) return;
          event.preventDefault();
          event.stopPropagation();
          const key = event.key;
          setOffset((current) => key === "Home" ? 0 : key === "End" ? 100 : clamp(current + (key === "ArrowLeft" ? -5 : 5)));
        }}
      >
        <PixelCat pose={petted ? "pet" : running && !dragging ? "run" : "sleep"} playing={motionScale > 0} />
        {petted && <span key={petCount} className="catbar__heart" aria-hidden="true">♥</span>}
      </button>
      <span id="cat-instructions" className="sr-only">Click or press Enter to pet. Drag, or use left and right arrow keys, to move along the player.</span>
      <span className="sr-only" role="status">{petted ? "Neku is purring." : ""}</span>
    </div>
  );
}

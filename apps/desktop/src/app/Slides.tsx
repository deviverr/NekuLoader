import { useEffect, useRef, type ReactNode } from "react";

export interface Slide {
  id: string;
  label: string;
  content: ReactNode;
}

/**
 * The three main screens, laid out side by side and translated horizontally.
 *
 * All three stay mounted so the player keeps running and the download form keeps
 * its state while you move between them — remounting per slide would reset both.
 * Navigation is by drag, arrow keys, or the indicator; the transition is driven
 * by the theme's motion scale like every other animation.
 */
export function Slides({
  slides,
  index,
  onIndex,
}: {
  slides: Slide[];
  index: number;
  onIndex: (index: number) => void;
}) {
  const dragStart = useRef<{ x: number; moved: boolean } | null>(null);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      // Never steal arrows from a field the user is typing or seeking in.
      const target = event.target as HTMLElement | null;
      if (target && /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName)) return;
      if (event.key === "ArrowRight") onIndex(Math.min(index + 1, slides.length - 1));
      if (event.key === "ArrowLeft") onIndex(Math.max(index - 1, 0));
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [index, onIndex, slides.length]);

  /** A drag counts as a swipe past this many pixels. */
  const SWIPE_THRESHOLD = 70;

  return (
    <div className="slides">
      <div
        className="slides__viewport"
        onPointerDown={(event) => {
          if ((event.target as HTMLElement).closest("input, button, select, textarea, a")) return;
          dragStart.current = { x: event.clientX, moved: false };
        }}
        onPointerUp={(event) => {
          const start = dragStart.current;
          dragStart.current = null;
          if (!start) return;
          const delta = event.clientX - start.x;
          if (delta <= -SWIPE_THRESHOLD) onIndex(Math.min(index + 1, slides.length - 1));
          if (delta >= SWIPE_THRESHOLD) onIndex(Math.max(index - 1, 0));
        }}
      >
        <div
          className="slides__track"
          style={{ transform: `translate3d(-${index * 100}%, 0, 0)` }}
        >
          {slides.map((slide, position) => (
            <section
              className="slides__slide"
              key={slide.id}
              aria-hidden={position !== index}
              // Keeps offscreen slides out of the tab order without unmounting
              // them, so the player and the download form keep their state.
              inert={position !== index}
            >
              <div className="slides__inner">{slide.content}</div>
            </section>
          ))}
        </div>
      </div>

      <div className="slides__dots" role="tablist" aria-label="Screens">
        {slides.map((slide, position) => (
          <button
            key={slide.id}
            type="button"
            role="tab"
            aria-selected={position === index}
            className={position === index ? "dot dot--active" : "dot"}
            onClick={() => onIndex(position)}
          >
            <span aria-hidden="true">{position === index ? "▰" : "▱"}</span>
            <span className="dot__label">{slide.label}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

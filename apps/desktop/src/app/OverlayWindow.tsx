import { useT } from "../lib/i18n";
import { useEffect, useRef, type ReactNode } from "react";

/**
 * A centered window drawn over the slides, used for everything that is not one
 * of the three main screens — the queue, history, devices, and settings.
 *
 * Modeled on a classic desktop dialog rather than a drawer: title bar with a
 * close box, dismissed by Escape or by clicking the backdrop.
 */
export function OverlayWindow({
  title,
  onClose,
  children,
  wide,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const t = useT();
  const panel = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const root = panel.current!;
    const focusable = () => Array.from(root.querySelectorAll<HTMLElement>(
      'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]',
    )).filter((element) => !element.hidden && element.getAttribute("aria-hidden") !== "true");
    (focusable()[0] ?? root).focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); event.stopPropagation(); close.current(); }
      if (event.key === "Tab") {
        const elements = focusable();
        const first = elements[0];
        const last = elements[elements.length - 1];
        if (!first) { event.preventDefault(); root.focus(); }
        else if (event.shiftKey && (document.activeElement === first || !root.contains(document.activeElement))) {
          event.preventDefault(); last.focus();
        } else if (!event.shiftKey && (document.activeElement === last || !root.contains(document.activeElement))) {
          event.preventDefault(); first.focus();
        }
      }
    };
    const containFocus = (event: FocusEvent) => {
      if (!root.contains(event.target as Node)) (focusable()[0] ?? root).focus();
    };
    document.addEventListener("keydown", onKey, true);
    document.addEventListener("focusin", containFocus);
    return () => {
      document.removeEventListener("keydown", onKey, true);
      document.removeEventListener("focusin", containFocus);
      previous?.focus();
    };
  }, []);

  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label={title}>
      <button
        type="button"
        className="overlay__backdrop"
        aria-label={`${t("Close")} ${title}`}
        tabIndex={-1}
        onClick={onClose}
      />
      <div ref={panel} tabIndex={-1} className={wide ? "window window--wide" : "window"}>
        <div className="window__bar">
          <span className="window__title">{title}</span>
          <button
            type="button"
            className="window__close"
            aria-label={t("Close")}
            onClick={onClose}
          >
            ✕
          </button>
        </div>
        <div className="window__body">{children}</div>
      </div>
    </div>
  );
}

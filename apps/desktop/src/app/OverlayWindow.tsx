import { useT } from "../lib/i18n";
import { useEffect, type ReactNode } from "react";

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
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [onClose]);

  return (
    <div className="overlay" role="dialog" aria-modal="true" aria-label={title}>
      <button
        type="button"
        className="overlay__backdrop"
        aria-label={`${t("Close")} ${title}`}
        tabIndex={-1}
        onClick={onClose}
      />
      <div className={wide ? "window window--wide" : "window"}>
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

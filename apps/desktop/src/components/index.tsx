import type { ReactNode } from "react";
import type { JobStatus } from "../types";

/** Panel with the shared box-drawing corner ticks. */
export function Panel({
  children,
  className,
  padded = true,
}: {
  children: ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <section className={["panel", className].filter(Boolean).join(" ")}>
      {padded ? <div className="panel__body">{children}</div> : children}
    </section>
  );
}

export function SectionHeading({
  eyebrow,
  title,
  aside,
}: {
  eyebrow?: string;
  title: string;
  aside?: ReactNode;
}) {
  return (
    <div className="section-heading">
      <div>
        {eyebrow && <p className="eyebrow">{eyebrow}</p>}
        <h2>{title}</h2>
      </div>
      {aside}
    </div>
  );
}

/**
 * Switch drawn with glyphs rather than a sliding pill, so it reads as part of
 * the same terminal chrome as everything around it.
 */
export function Toggle({
  label,
  value,
  onChange,
  disabled,
  hint,
}: {
  label: string;
  value: boolean;
  onChange: (value: boolean) => void;
  disabled?: boolean;
  hint?: string;
}) {
  return (
    <button
      type="button"
      className="toggle"
      role="switch"
      aria-checked={value}
      disabled={disabled}
      title={hint}
      onClick={() => onChange(!value)}
    >
      <span className="toggle__box" aria-hidden="true">
        {value ? "[▓]" : "[ ]"}
      </span>
      <span>{label}</span>
    </button>
  );
}

export function StatusChip({ status }: { status: JobStatus }) {
  return <span className={`chip chip--${status}`}>{status}</span>;
}

/**
 * Progress rendered as a run of block glyphs. `width` on the fill clips a
 * duplicate row of solid blocks over the empty track.
 */
export function Progress({ value, cells = 28 }: { value: number; cells?: number }) {
  const clamped = Math.max(0, Math.min(100, value));
  return (
    <div
      className="progress"
      role="progressbar"
      aria-valuenow={Math.round(clamped)}
      aria-valuemin={0}
      aria-valuemax={100}
    >
      <span aria-hidden="true">{"░".repeat(cells)}</span>
      <span className="progress__fill" style={{ width: `${clamped}%` }} aria-hidden="true">
        {"█".repeat(cells)}
      </span>
    </div>
  );
}

export function Empty({ title, text }: { title: string; text?: string }) {
  return (
    <div className="empty-state">
      <h3>{title}</h3>
      {text && <p className="muted">{text}</p>}
    </div>
  );
}

export function Notice({
  tone,
  children,
}: {
  tone: "error" | "warning" | "success" | "info";
  children: ReactNode;
}) {
  const glyph = { error: "✕", warning: "!", success: "✓", info: "i" }[tone];
  return (
    <div className={`notice notice--${tone}`} role={tone === "error" ? "alert" : "status"}>
      <span aria-hidden="true">{glyph}</span>
      <div>{children}</div>
    </div>
  );
}

export function IconButton({
  glyph,
  label,
  onClick,
  disabled,
  accent,
}: {
  glyph: string;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  accent?: boolean;
}) {
  return (
    <button
      type="button"
      className={accent ? "icon-button icon-button--accent" : "icon-button"}
      aria-label={label}
      title={label}
      disabled={disabled}
      onClick={onClick}
    >
      <span aria-hidden="true">{glyph}</span>
    </button>
  );
}

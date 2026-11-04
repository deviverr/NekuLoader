/**
 * Navigation model.
 *
 * Glyphs are plain text rather than icon components — the interface has no icon
 * font or SVG set, which keeps the chrome in the same typographic world as the
 * ASCII art. These sit outside `<pre>` blocks, so they are not held to the
 * strict single-width character ranges the art frames are.
 */
export type View =
  | "download"
  | "queue"
  | "library"
  | "devices"
  | "history"
  | "settings";

export interface NavItem {
  id: View;
  label: string;
  glyph: string;
  /** Shown above the view title in the header. */
  eyebrow: string;
}

export const navItems: NavItem[] = [
  { id: "download", label: "Download", glyph: "▼", eyebrow: "Smart link resolver" },
  { id: "queue", label: "Queue", glyph: "≡", eyebrow: "Persistent queue" },
  { id: "library", label: "Library", glyph: "▤", eyebrow: "Local collection" },
  { id: "devices", label: "Devices", glyph: "▯", eyebrow: "Transfer" },
  { id: "history", label: "History", glyph: "↺", eyebrow: "Activity archive" },
  { id: "settings", label: "Settings", glyph: "⚙", eyebrow: "Configuration" },
];

export function navItemFor(view: View): NavItem {
  return navItems.find((item) => item.id === view) ?? navItems[0];
}

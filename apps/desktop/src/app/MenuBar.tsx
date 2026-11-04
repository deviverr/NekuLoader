import { useEffect, useRef, useState } from "react";

export interface MenuAction {
  label: string;
  /** Shown right-aligned; purely informational, the shortcut is bound elsewhere. */
  shortcut?: string;
  onSelect?: () => void;
  /** Renders a rule instead of an item. */
  separator?: true;
  checked?: boolean;
  disabled?: boolean;
}

export interface Menu {
  label: string;
  items: MenuAction[];
}

/**
 * Classic in-window menu bar, drawn in text rather than using the OS menu so it
 * looks identical on every platform and stays in the same visual language as
 * the rest of the interface.
 *
 * Behaves the way desktop menus are expected to: click to open, hover to move
 * between menus once one is open, Escape or an outside click to dismiss.
 */
export function MenuBar({ menus }: { menus: Menu[] }) {
  const [open, setOpen] = useState<string | null>(null);
  const barRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (open === null) return;
    const dismiss = (event: MouseEvent) => {
      if (!barRef.current?.contains(event.target as Node)) setOpen(null);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(null);
    };
    document.addEventListener("mousedown", dismiss);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", dismiss);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  return (
    <div className="menubar" ref={barRef}>
      {menus.map((menu) => (
        <div className="menubar__slot" key={menu.label}>
          <button
            type="button"
            className={open === menu.label ? "menubar__title menubar__title--open" : "menubar__title"}
            aria-haspopup="menu"
            aria-expanded={open === menu.label}
            onClick={() => setOpen((current) => (current === menu.label ? null : menu.label))}
            onMouseEnter={() => setOpen((current) => (current === null ? current : menu.label))}
          >
            {menu.label}
          </button>

          {open === menu.label && (
            <div className="menu" role="menu">
              {menu.items.map((item, index) =>
                item.separator ? (
                  <div className="menu__rule" key={`rule-${index}`} role="separator" />
                ) : (
                  <button
                    key={item.label}
                    type="button"
                    role="menuitem"
                    className="menu__item"
                    disabled={item.disabled}
                    onClick={() => {
                      setOpen(null);
                      item.onSelect?.();
                    }}
                  >
                    <span className="menu__check" aria-hidden="true">
                      {item.checked ? "•" : " "}
                    </span>
                    <span className="menu__label">{item.label}</span>
                    {item.shortcut && <span className="menu__shortcut">{item.shortcut}</span>}
                  </button>
                ),
              )}
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

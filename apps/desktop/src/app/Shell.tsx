import type { ReactNode } from "react";
import { CatBar } from "./CatBar";
import { MenuBar, type Menu } from "./MenuBar";
import { Slides, type Slide } from "./Slides";
import { PixelSky } from "./PixelSky";
import type { AppSettings } from "../types";

interface ShellProps {
  menus: Menu[];
  slides: Slide[];
  slideIndex: number;
  onSlideIndex: (index: number) => void;
  settings: AppSettings;
  version: string;
  /** Drives the cat: running while anything is downloading, asleep otherwise. */
  downloading: boolean;
  backgroundUrl?: string;
  /** The persistent player strip, mounted regardless of the active slide. */
  player?: ReactNode;
  /** Whichever overlay window is open, if any. */
  overlay?: ReactNode;
  children?: ReactNode;
}

export function Shell({
  menus,
  slides,
  slideIndex,
  onSlideIndex,
  settings,
  version,
  downloading,
  backgroundUrl,
  player,
  overlay,
  children,
}: ShellProps) {
  return (
    <div className={`app ${settings.reducedMotion ? "reduced-motion" : ""}`}>
      {backgroundUrl && (
        <div
          className="backdrop"
          aria-hidden="true"
          style={{ backgroundImage: `url("${backgroundUrl}")` }}
        />
      )}
      <PixelSky settings={settings} />

      <header className="topbar">
        <MenuBar menus={menus} />
        <span className="topbar__mark">NEKULOADER</span>
        <span className="topbar__version">v{version}</span>
      </header>

      <main className="stage">
        <Slides slides={slides} index={slideIndex} onIndex={onSlideIndex} />
      </main>

      {player}

      <CatBar running={downloading} />

      {overlay}
      {children}
    </div>
  );
}

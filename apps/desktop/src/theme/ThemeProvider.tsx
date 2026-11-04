import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  type ReactNode,
} from "react";
import type { AppSettings, CustomThemeColors } from "../types";
import { applyCustomTheme, clearCustomTheme } from "./customTheme";
import { presetTheme } from "./presets";

/**
 * How much of the ASCII art a theme wants on screen. The Cosmic skin uses the
 * full pieces; the Legacy skin is deliberately quieter and uses the compact
 * variants, which is the whole substance of "muted cosmic".
 */
export type AsciiDensity = "full" | "compact";

export type Skin = "mono" | "cosmic" | "legacy";

export interface ThemeContextValue {
  /** Resolved built-in skin. A `custom:<name>` mode renders on top of `mono`. */
  skin: Skin;
  density: AsciiDensity;
  /**
   * Multiplier applied to every animation duration. 0 freezes motion entirely,
   * which is how reduced-motion is honoured without every component branching.
   */
  motionScale: number;
}

const ThemeContext = createContext<ThemeContextValue>({
  skin: "mono",
  density: "full",
  motionScale: 1,
});

export function useTheme(): ThemeContextValue {
  return useContext(ThemeContext);
}

function resolveSkin(themeMode: string): Skin {
  if (themeMode === "cosmic") return "cosmic";
  if (themeMode === "legacy") return "legacy";
  // Custom themes layer their colours over the monochrome base.
  return "mono";
}

export function ThemeProvider({
  settings,
  customThemes,
  children,
}: {
  settings: AppSettings;
  customThemes: CustomThemeColors[];
  children: ReactNode;
}) {
  const skin = resolveSkin(settings.themeMode);

  const value = useMemo<ThemeContextValue>(() => {
    // Legacy is the deliberately quiet skin: less art, slower motion.
    const density: AsciiDensity = skin === "legacy" ? "compact" : "full";
    if (settings.reducedMotion) return { skin, density, motionScale: 0 };
    return { skin, density, motionScale: skin === "legacy" ? 0.55 : 1 };
  }, [skin, settings.reducedMotion]);

  // The skin is an attribute rather than a class so the two token stylesheets
  // can be plain `:root[data-theme=…]` blocks that cascade into each other.
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.theme = value.skin;
    root.dataset.density = value.density;
    root.style.setProperty("--motion-scale", String(value.motionScale));
  }, [value]);

  // A named custom theme overrides both stylesheets via inline properties.
  useEffect(() => {
    if (settings.themeMode.startsWith("preset:")) {
      const preset = presetTheme(settings.themeMode.slice("preset:".length));
      if (preset) applyCustomTheme(preset);
      else clearCustomTheme();
      return;
    }
    if (!settings.themeMode.startsWith("custom:")) {
      clearCustomTheme();
      return;
    }
    const name = settings.themeMode.slice("custom:".length);
    const theme = customThemes.find((entry) => entry.name === name);
    if (theme) applyCustomTheme(theme);
    else clearCustomTheme();
  }, [settings.themeMode, customThemes]);

  // Legacy 3.1 let the log colour be themed separately from the chrome.
  useEffect(() => {
    document.documentElement.style.setProperty(
      "--log-text",
      `var(--log-${settings.logTextColor}, var(--text))`,
    );
  }, [settings.logTextColor]);

  useEffect(() => {
    document.documentElement.style.setProperty(
      "--frame-alpha",
      String(settings.frameTransparency),
    );
  }, [settings.frameTransparency]);

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

import type { CustomThemeColors } from "../types";

/**
 * Maps a saved custom theme onto the design-token custom properties.
 *
 * These are written as inline styles on `:root`, so they beat both
 * `themes/cosmic.css` and `themes/legacy.css` regardless of stylesheet order —
 * which is what makes a user theme a third layer rather than a fork of either.
 *
 * The key set mirrors legacy 3.1's Customize Theme dialog one-for-one so an
 * imported profile maps across without losing a colour.
 */
const TOKEN_FOR: Record<keyof Omit<CustomThemeColors, "name" | "starAlpha">, string[]> = {
  bgStart: ["--bg-0"],
  bgEnd: ["--bg-1", "--bg-2"],
  titleColor: ["--title", "--ascii-fg"],
  textColor: ["--text"],
  logBgColor: ["--log-bg"],
  logTextColor: ["--log-text"],
  buttonColor: ["--action", "--action-hover"],
  starColor: ["--star"],
  menuBarColor: ["--surface", "--surface-solid"],
};

export function applyCustomTheme(theme: CustomThemeColors): void {
  const root = document.documentElement;
  for (const [key, tokens] of Object.entries(TOKEN_FOR)) {
    const value = theme[key as keyof typeof TOKEN_FOR];
    if (!value) continue;
    for (const token of tokens) root.style.setProperty(token, value);
  }
  root.style.setProperty("--star-alpha", String(theme.starAlpha));
  // Derived tokens, so light themes keep readable secondary text and borders
  // instead of inheriting the dark skin's greys.
  for (const [token, value] of Object.entries(derivedTokens(theme))) {
    root.style.setProperty(token, value);
  }
}

const DERIVED = [
  "--text-muted", "--text-faint", "--border", "--border-soft", "--border-bright",
  "--surface-raised", "--focus", "--cyan", "--magenta", "--success", "--danger", "--warning",
];

function derivedTokens(theme: CustomThemeColors): Record<string, string> {
  const mix = (a: string, b: string, share: number) => `color-mix(in srgb, ${a} ${share}%, ${b})`;
  return {
    "--text-muted": mix(theme.textColor, theme.bgStart, 72),
    "--text-faint": mix(theme.textColor, theme.bgStart, 48),
    "--border": mix(theme.textColor, theme.bgStart, 32),
    "--border-soft": mix(theme.textColor, "transparent", 16),
    "--border-bright": mix(theme.titleColor, theme.textColor, 70),
    "--surface-raised": mix(theme.menuBarColor, theme.textColor, 88),
    "--focus": theme.titleColor,
    "--cyan": theme.titleColor,
    "--magenta": theme.titleColor,
    "--success": theme.logTextColor,
    "--danger": mix("#ff4d6a", theme.textColor, 80),
    "--warning": mix("#ffc04d", theme.textColor, 80),
  };
}

export function clearCustomTheme(): void {
  const root = document.documentElement;
  for (const tokens of Object.values(TOKEN_FOR)) {
    for (const token of tokens) root.style.removeProperty(token);
  }
  root.style.removeProperty("--star-alpha");
  for (const token of DERIVED) root.style.removeProperty(token);
}

/** Starting point for the theme editor when no custom theme exists yet. */
export const STARTER_CUSTOM_THEME: Omit<CustomThemeColors, "name"> = {
  bgStart: "#0a0713",
  bgEnd: "#171029",
  titleColor: "#c9a6ff",
  textColor: "#e9e2f5",
  logBgColor: "#080513",
  logTextColor: "#7dffa8",
  buttonColor: "#6d3ba8",
  starColor: "#b98cff",
  starAlpha: 0.47,
  menuBarColor: "#140d24",
};

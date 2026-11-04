import type { CustomThemeColors } from "../types";

/**
 * The colour themes of NekuLoader 3.1 (ThemeManager.java), carried over
 * value for value and rendered through the custom-theme token layer.
 */
export const PRESET_THEMES: CustomThemeColors[] = [
  {
    name: "Neku",
    bgStart: "#1e1428", bgEnd: "#3c2850", titleColor: "#ffc8ff", textColor: "#ffffff",
    logBgColor: "#140f1e", logTextColor: "#96ff96", buttonColor: "#503264",
    starColor: "#ffffc8", starAlpha: 0.59, menuBarColor: "#281e3c",
  },
  {
    name: "Dark",
    bgStart: "#141419", bgEnd: "#282832", titleColor: "#b4b4ff", textColor: "#dcdcdc",
    logBgColor: "#0f0f14", logTextColor: "#64ff64", buttonColor: "#3c3c50",
    starColor: "#9696c8", starAlpha: 0.39, menuBarColor: "#1e1e23",
  },
  {
    name: "Light",
    bgStart: "#f0f0f5", bgEnd: "#dcdce6", titleColor: "#643296", textColor: "#282828",
    logBgColor: "#ffffff", logTextColor: "#007800", buttonColor: "#c8c8dc",
    starColor: "#c8962a", starAlpha: 0.5, menuBarColor: "#e6e6f0",
  },
  {
    name: "Cyberpunk",
    bgStart: "#0a001e", bgEnd: "#320050", titleColor: "#00ffff", textColor: "#ff00ff",
    logBgColor: "#050014", logTextColor: "#00ffc8", buttonColor: "#500078",
    starColor: "#00ffff", starAlpha: 0.47, menuBarColor: "#140032",
  },
  {
    name: "Neon",
    bgStart: "#000000", bgEnd: "#1e0032", titleColor: "#ff00ff", textColor: "#00ffff",
    logBgColor: "#0a0014", logTextColor: "#00ff00", buttonColor: "#640096",
    starColor: "#ff00ff", starAlpha: 0.59, menuBarColor: "#14001e",
  },
  {
    name: "System",
    bgStart: "#32323c", bgEnd: "#50505a", titleColor: "#c8c8ff", textColor: "#dcdcdc",
    logBgColor: "#1e1e28", logTextColor: "#96ff96", buttonColor: "#646478",
    starColor: "#c8c8ff", starAlpha: 0.39, menuBarColor: "#3c3c46",
  },
  {
    name: "Absolute Black and White",
    bgStart: "#000000", bgEnd: "#000000", titleColor: "#ffffff", textColor: "#ffffff",
    logBgColor: "#000000", logTextColor: "#ffffff", buttonColor: "#282828",
    starColor: "#ffffff", starAlpha: 0.7, menuBarColor: "#000000",
  },
  {
    name: "Absolute White and Black",
    bgStart: "#ffffff", bgEnd: "#ffffff", titleColor: "#000000", textColor: "#000000",
    logBgColor: "#ffffff", logTextColor: "#000000", buttonColor: "#dcdcdc",
    starColor: "#000000", starAlpha: 0.7, menuBarColor: "#ffffff",
  },
];

export function presetTheme(name: string): CustomThemeColors | undefined {
  return PRESET_THEMES.find((theme) => theme.name === name);
}

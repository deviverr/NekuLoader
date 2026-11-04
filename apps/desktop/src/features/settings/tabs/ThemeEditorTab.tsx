import { useT } from "../../../lib/i18n";
import { useState } from "react";
import { Notice, Panel, SectionHeading } from "../../../components";
import * as api from "../../../lib/api";
import { errorMessage } from "../../../lib/formats";
import {
  applyCustomTheme,
  clearCustomTheme,
  STARTER_CUSTOM_THEME,
} from "../../../theme/customTheme";
import type { AppSettings, CustomThemeColors, ThemeMode } from "../../../types";

/** Editable colour slots, in the order legacy 3.1's dialog presented them. */
const SLOTS: Array<{
  key: keyof Omit<CustomThemeColors, "name" | "starAlpha">;
  label: string;
}> = [
  { key: "bgStart", label: "Background top" },
  { key: "bgEnd", label: "Background bottom" },
  { key: "titleColor", label: "Titles & art" },
  { key: "textColor", label: "Body text" },
  { key: "logBgColor", label: "Log background" },
  { key: "logTextColor", label: "Log text" },
  { key: "buttonColor", label: "Buttons" },
  { key: "starColor", label: "Stars" },
  { key: "menuBarColor", label: "Panels" },
];

export function ThemeEditorTab({
  draft,
  customThemes,
  onCustomThemes,
  onApply,
  toast,
}: {
  draft: AppSettings;
  customThemes: CustomThemeColors[];
  onCustomThemes: (value: CustomThemeColors[]) => void;
  onApply: (patch: Partial<AppSettings>) => Promise<boolean>;
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const active = draft.themeMode.startsWith("custom:")
    ? customThemes.find((theme) => theme.name === draft.themeMode.slice(7))
    : undefined;

  const [theme, setTheme] = useState<CustomThemeColors>(
    active ?? { name: t("My theme"), ...STARTER_CUSTOM_THEME },
  );
  const [previewing, setPreviewing] = useState(false);

  function edit(patch: Partial<CustomThemeColors>) {
    const next = { ...theme, ...patch };
    setTheme(next);
    // The editor previews live against the real token layer, so what you see
    // here is exactly what applying it produces.
    if (previewing) applyCustomTheme(next);
  }

  async function save() {
    if (!theme.name.trim()) {
      toast(t("ui.103"), "error");
      return;
    }
    try {
      await api.saveCustomTheme(theme);
      onCustomThemes(await api.listCustomThemes());
      toast(`${t("ui.109")}: ${theme.name}`, "success");
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  }

  async function remove(name: string) {
    try {
      await api.deleteCustomTheme(name);
      onCustomThemes(await api.listCustomThemes());
      if (draft.themeMode === `custom:${name}`)
        await onApply({ themeMode: "cosmic" });
      toast(`${t("ui.117")}: ${name}`, "info");
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  }

  function exportTheme() {
    const blob = new Blob([JSON.stringify(theme, null, 2)], {
      type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `${theme.name.replace(/[^\w-]+/g, "-")}.nekutheme.json`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div className="settings-grid">
      <Panel>
        <SectionHeading eyebrow={t("ui.104")} title={t("ui.105")} />

        <label className="setting">
          <span>{t("ui.106")} </span>
          <input
            className="text-input"
            value={theme.name}
            onChange={(event) => edit({ name: event.target.value })}
          />
        </label>

        <div className="color-grid">
          {SLOTS.map((slot) => (
            <label key={slot.key} className="color-slot">
              <input
                type="color"
                value={theme[slot.key]}
                onChange={(event) => edit({ [slot.key]: event.target.value })}
              />
              <span>{t(slot.label)}</span>
            </label>
          ))}
        </div>

        <div className="setting-actions">
          <button
            type="button"
            className={previewing ? "button button--primary" : "button"}
            onClick={() => {
              const next = !previewing;
              setPreviewing(next);
              if (next) applyCustomTheme(theme);
              else clearCustomTheme();
            }}
          >
            {previewing ? t("ui.107") : t("ui.108")}
          </button>
          <button type="button" className="button" onClick={() => void save()}>
            {t("ui.109")}{" "}
          </button>
          <button type="button" className="button" onClick={exportTheme}>
            {t("ui.110")}{" "}
          </button>
          <button
            type="button"
            className="button button--primary"
            onClick={async () => {
              await save();
              clearCustomTheme();
              setPreviewing(false);
              await onApply({ themeMode: `custom:${theme.name}` as ThemeMode });
            }}
          >
            {t("ui.111")}{" "}
          </button>
        </div>

        {previewing && <Notice tone="info">{t("ui.112")} </Notice>}
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.113")} title={t("ui.114")} />
        {customThemes.length === 0 ? (
          <p className="muted">{t("ui.115")} </p>
        ) : (
          <div className="theme-list">
            {customThemes.map((entry) => (
              <div key={entry.name} className="theme-row">
                <span
                  className="theme-swatch"
                  aria-hidden="true"
                  style={{
                    background: `linear-gradient(135deg, ${entry.bgStart}, ${entry.bgEnd})`,
                    borderColor: entry.titleColor,
                  }}
                />
                <strong>{entry.name}</strong>
                <button
                  type="button"
                  className="button"
                  onClick={() => setTheme(entry)}
                >
                  {t("ui.116")}{" "}
                </button>
                <button
                  type="button"
                  className="button button--danger"
                  onClick={() => void remove(entry.name)}
                >
                  {t("ui.117")}{" "}
                </button>
              </div>
            ))}
          </div>
        )}

        <SectionHeading eyebrow={t("ui.118")} title={t("ui.119")} />
        <p className="muted setting__note">{t("ui.120")} </p>
        <div className="setting-actions">
          <button
            type="button"
            className="button"
            onClick={async () => {
              const chosen = await api.chooseImage();
              if (!chosen) return;
              // Keep a short most-recent list, exactly as legacy 3.1 did.
              const recent = [
                chosen,
                ...draft.backgroundImageRecent.filter(
                  (entry) => entry !== chosen,
                ),
              ].slice(0, 5);
              await onApply({
                backgroundImagePath: chosen,
                backgroundImageRecent: recent,
              });
            }}
          >
            {t("ui.121")}{" "}
          </button>
          {draft.backgroundImagePath && (
            <button
              type="button"
              className="button"
              onClick={() => void onApply({ backgroundImagePath: null })}
            >
              {t("ui.122")}{" "}
            </button>
          )}
        </div>

        {draft.backgroundImageRecent.length > 0 && (
          <div className="recent-list">
            {draft.backgroundImageRecent.map((path) => (
              <button
                key={path}
                type="button"
                className="recent-item"
                title={path}
                onClick={() => void onApply({ backgroundImagePath: path })}
              >
                {path.split(/[/\\]/).pop()}
              </button>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}

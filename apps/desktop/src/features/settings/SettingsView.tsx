import { useEffect, useState } from "react";
import { Notice, Panel } from "../../components";
import * as api from "../../lib/api";
import { errorMessage } from "../../lib/formats";
import { playSound } from "../../lib/sound";
import { useT } from "../../lib/i18n";
import type { AppSettings, CustomThemeColors, ToolInfo } from "../../types";
import { AppearanceTab } from "./tabs/AppearanceTab";
import { ComponentsTab } from "./tabs/ComponentsTab";
import { GeneralTab } from "./tabs/GeneralTab";
import { ThemeEditorTab } from "./tabs/ThemeEditorTab";
import { AboutTab } from "./tabs/AboutTab";
import "./settings.css";

type Tab = "general" | "appearance" | "theme" | "components" | "about";

const TABS: Array<{ id: Tab; label: string }> = [
  { id: "general", label: "settings.general" },
  { id: "appearance", label: "settings.appearance" },
  { id: "theme", label: "settings.theme" },
  { id: "components", label: "settings.components" },
  { id: "about", label: "settings.about" },
];

export function SettingsView({
  settings,
  tools,
  customThemes,
  version,
  isWindows,
  activeDownloads,
  onSettings,
  onTools,
  onCustomThemes,
  onRescan,
  toast,
}: {
  settings: AppSettings;
  tools: ToolInfo[];
  customThemes: CustomThemeColors[];
  version: string;
  isWindows: boolean;
  activeDownloads: number;
  onSettings: (value: AppSettings) => void;
  onTools: (value: ToolInfo[]) => void;
  onCustomThemes: (value: CustomThemeColors[]) => void;
  /** Re-runs the library scan after the music sources change. */
  onRescan: () => void;
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const [tab, setTab] = useState<Tab>("general");
  const [draft, setDraft] = useState(settings);
  const [dirty, setDirty] = useState(false);

  useEffect(() => {
    setDraft(settings);
    setDirty(false);
  }, [settings]);

  function edit(patch: Partial<AppSettings>) {
    setDraft((current) => ({ ...current, ...patch }));
    setDirty(true);
  }

  /**
   * Appearance changes apply immediately so the user can see them, which means
   * they bypass the draft and save straight away.
   */
  async function applyNow(patch: Partial<AppSettings>) {
    const next = { ...draft, ...patch };
    setDraft(next);
    try {
      onSettings(await api.saveSettings(next));
      setDirty(false);
      return true;
    } catch (error) {
      setDirty(true);
      toast(errorMessage(error), "error");
      return false;
    }
  }

  async function save() {
    try {
      const saved = await api.saveSettings(draft);
      onSettings(saved);
      setDirty(false);
      playSound("saved", saved.soundEnabled, saved.soundVolume);
      toast(t("ui.0"), "success");
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  }

  return (
    <div className="view-stack">
      <div className="tabs" role="tablist">
        {TABS.map((entry) => (
          <button
            key={entry.id}
            type="button"
            role="tab"
            aria-selected={tab === entry.id}
            className={tab === entry.id ? "tab tab--active" : "tab"}
            onClick={() => setTab(entry.id)}
          >
            {t(entry.label)}
          </button>
        ))}
      </div>

      {tab === "general" && (
        <GeneralTab
          draft={draft}
          onEdit={edit}
          onApply={applyNow}
          onRescan={onRescan}
          isWindows={isWindows}
        />
      )}
      {tab === "appearance" && (
        <AppearanceTab
          draft={draft}
          onEdit={edit}
          onApply={applyNow}
          customThemes={customThemes}
        />
      )}
      {tab === "theme" && (
        <ThemeEditorTab
          draft={draft}
          customThemes={customThemes}
          onCustomThemes={onCustomThemes}
          onApply={applyNow}
          toast={toast}
        />
      )}
      {tab === "components" && (
        <ComponentsTab
          draft={draft}
          onEdit={edit}
          tools={tools}
          onTools={onTools}
          activeDownloads={activeDownloads}
          toast={toast}
        />
      )}
      {tab === "about" && <AboutTab version={version} />}

      {dirty && (
        <Panel>
          <div className="settings-save">
            <Notice tone="info">{t("ui.1")} </Notice>
            <button
              type="button"
              className="button button--primary"
              onClick={() => void save()}
            >
              {t("ui.2")}{" "}
            </button>
          </div>
        </Panel>
      )}
    </div>
  );
}

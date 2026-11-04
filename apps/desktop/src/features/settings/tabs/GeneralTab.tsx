import { Notice, Panel, SectionHeading, Toggle } from "../../../components";
import * as api from "../../../lib/api";
import type { AppSettings } from "../../../types";
import { LANGUAGES, useT } from "../../../lib/i18n";

/** Matches the allow-list yt-dlp accepts, mirrored in downloader.rs. */
const COOKIE_BROWSERS = [
  "brave",
  "chrome",
  "chromium",
  "edge",
  "firefox",
  "opera",
  "safari",
  "vivaldi",
  "whale",
];

export function GeneralTab({
  draft,
  onEdit,
  onApply,
  onRescan,
  isWindows,
}: {
  draft: AppSettings;
  onEdit: (patch: Partial<AppSettings>) => void;
  onApply: (patch: Partial<AppSettings>) => Promise<boolean>;
  onRescan: () => void;
  isWindows: boolean;
}) {
  const t = useT();

  /** Source changes save and rescan straight away — a draft would be confusing. */
  async function applyAndRescan(patch: Partial<AppSettings>) {
    if (await onApply(patch)) onRescan();
  }

  return (
    <div className="settings-grid">
      <Panel>
        <SectionHeading eyebrow={t("ui.60")} title={t("ui.61")} />

        <label className="setting">
          <span>
            {t("settings.language")}
            <small>{t("ui.62")} </small>
          </span>
          <select
            value={draft.language}
            onChange={(event) => onEdit({ language: event.target.value })}
          >
            {LANGUAGES.map((language) => (
              <option key={language} value={language}>
                {language}
              </option>
            ))}
          </select>
        </label>

        <div className="setting">
          <span>
            {t("ui.63")} <small>{t("ui.64")} </small>
          </span>
          <button
            type="button"
            className="button"
            onClick={async () => {
              const folder = await api.chooseFolder();
              if (folder) onEdit({ outputDir: folder });
            }}
          >
            {t("ui.65")}{" "}
          </button>
        </div>
        <p className="setting__value">{draft.outputDir}</p>

        <label className="setting">
          <span>
            {t("ui.66")} <small>{t("ui.67")} </small>
          </span>
          <select
            value={draft.concurrentDownloads}
            onChange={(event) =>
              onEdit({ concurrentDownloads: Number(event.target.value) })
            }
          >
            {[1, 2, 3, 4].map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </label>

        <div className="setting-stack">
          <Toggle
            label={t("ui.69")}
            value={draft.embedMetadata}
            onChange={(value) => onEdit({ embedMetadata: value })}
          />
          <Toggle
            label={t("ui.70")}
            value={draft.embedArtwork}
            onChange={(value) => onEdit({ embedArtwork: value })}
          />
          <Toggle
            label={t("ui.71")}
            value={draft.trackNumbers}
            onChange={(value) => onEdit({ trackNumbers: value })}
          />
        </div>
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.72")} title={t("ui.73")} />
        <p className="muted setting__note">{t("ui.74")} </p>

        <div className="setting-stack">
          <Toggle
            label={t("ui.75")}
            value={draft.scanSystemMusic}
            onChange={(value) =>
              void applyAndRescan({ scanSystemMusic: value })
            }
          />
          <Toggle
            label={t("ui.76")}
            value={draft.scanEverything}
            onChange={(value) => void applyAndRescan({ scanEverything: value })}
            hint={t("ui.77")}
          />
        </div>

        <div className="setting-actions">
          <button
            type="button"
            className="button"
            onClick={async () => {
              const folder = await api.chooseFolder();
              if (!folder || draft.musicFolders.includes(folder)) return;
              await applyAndRescan({
                musicFolders: [...draft.musicFolders, folder],
              });
            }}
          >
            {t("ui.78")}{" "}
          </button>
        </div>

        {draft.musicFolders.length > 0 && (
          <div className="recent-list">
            {draft.musicFolders.map((folder) => (
              <div className="folder-row" key={folder}>
                <span title={folder}>{folder}</span>
                <button
                  type="button"
                  className="icon-button"
                  aria-label={`Remove ${folder}`}
                  onClick={() =>
                    void applyAndRescan({
                      musicFolders: draft.musicFolders.filter(
                        (entry) => entry !== folder,
                      ),
                    })
                  }
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        )}
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.79")} title={t("ui.80")} />
        <p className="muted setting__note">{t("ui.81")} </p>
        <p className="muted setting__note">{t("ui.82")} </p>
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.83")} title={t("ui.84")} />
        <p className="muted setting__note">{t("ui.85")} </p>

        <label className="setting">
          <span>{t("ui.86")} </span>
          <select
            value={draft.cookieMode}
            onChange={(event) =>
              onEdit({
                cookieMode: event.target.value as AppSettings["cookieMode"],
              })
            }
          >
            <option value="none">{t("ui.87")} </option>
            <option value="browser">{t("ui.88")} </option>
            <option value="file">{t("ui.89")} </option>
          </select>
        </label>

        {draft.cookieMode === "browser" && (
          <label className="setting">
            <span>
              {t("ui.90")} <small>{t("ui.91")} </small>
            </span>
            <select
              value={draft.cookieBrowser}
              onChange={(event) =>
                onEdit({ cookieBrowser: event.target.value })
              }
            >
              {COOKIE_BROWSERS.map((browser) => (
                <option key={browser} value={browser}>
                  {browser}
                </option>
              ))}
            </select>
          </label>
        )}

        {isWindows &&
          draft.cookieMode === "browser" &&
          draft.cookieBrowser !== "firefox" && (
            <Notice tone="warning">{t("ui.92")} </Notice>
          )}

        {draft.cookieMode === "file" && (
          <>
            <div className="setting">
              <span>
                {t("ui.93")} <small>{t("ui.94")} </small>
              </span>
              <button
                type="button"
                className="button"
                onClick={async () => {
                  const chosen = await api.chooseCookieFile();
                  if (chosen) onEdit({ cookieFile: chosen });
                }}
              >
                {t("ui.65")}{" "}
              </button>
            </div>
            {draft.cookieFile && (
              <p className="setting__value">{draft.cookieFile}</p>
            )}
          </>
        )}

        {draft.cookieMode === "browser" && (
          <Notice tone="warning">{t("ui.95")} </Notice>
        )}
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.96")} title={t("ui.97")} />
        <div className="setting-stack">
          <Toggle
            label={t("ui.98")}
            value={draft.customFolderIcons}
            onChange={(value) => onEdit({ customFolderIcons: value })}
            disabled={!isWindows}
            hint={isWindows ? undefined : t("ui.99")}
          />
          <Toggle
            label={t("ui.100")}
            value={draft.trayNotifications}
            onChange={(value) => {
              if (!value) onEdit({ trayNotifications: false });
              else
                void api
                  .requestNotifications()
                  .then((granted) => onEdit({ trayNotifications: granted }))
                  .catch(() => onEdit({ trayNotifications: false }));
            }}
          />
          <Toggle
            label={t("ui.101")}
            value={draft.checkToolUpdates}
            onChange={(value) => onEdit({ checkToolUpdates: value })}
          />
        </div>
        {!isWindows && <Notice tone="info">{t("ui.102")} </Notice>}
      </Panel>
    </div>
  );
}

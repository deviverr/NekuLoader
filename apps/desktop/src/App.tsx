import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { AsciiArt } from "./ascii/AsciiArt";
import { catSleepFrames } from "./ascii/frames/catRunner";
import { wordmarkFrames } from "./ascii/frames/wordmark";
import { Shell } from "./app/Shell";
import type { Menu } from "./app/MenuBar";
import { OverlayWindow } from "./app/OverlayWindow";
import type { Slide } from "./app/Slides";
import { DownloadView } from "./features/download/DownloadView";
import { QueueView } from "./features/queue/QueueView";
import { LibraryView } from "./features/library/LibraryView";
import { HistoryView } from "./features/history/HistoryView";
import { DevicesView } from "./features/devices/DevicesView";
import { SettingsView } from "./features/settings/SettingsView";
import { MiniPlayerBar } from "./features/player/MiniPlayerBar";
import { PlayerScreen } from "./features/player/PlayerScreen";
import { PlayerProvider } from "./features/player/PlayerProvider";
import * as api from "./lib/api";
import { errorMessage } from "./lib/formats";
import { playSound } from "./lib/sound";
import { ThemeProvider } from "./theme/ThemeProvider";
import { LanguageProvider, translate } from "./lib/i18n";
import type {
  AppSettings,
  BootstrapResponse,
  CustomThemeColors,
  DownloadJob,
  HistoryEntry,
  JobStatus,
  LibraryTrack,
  ToolInfo,
} from "./types";
import "./theme/themes/mono.css";
import "./theme/themes/cosmic.css";
import "./theme/themes/legacy.css";
import "./styles/base.css";
import "./styles/layout.css";
import "./styles/components.css";
import "./styles/ascii.css";
import "./styles/pixel.css";
import "@fontsource/pixelify-sans/400.css";
import "@fontsource/pixelify-sans/500.css";
import "@fontsource/pixelify-sans/600.css";

type Toast = { id: number; tone: "success" | "error" | "info"; message: string };
type Overlay = "queue" | "history" | "devices" | "settings" | null;

const ACTIVE_STATUSES: JobStatus[] = ["preparing", "downloading", "converting"];
/** Ring buffer size for the per-job terminal panel, mirroring the Rust cap. */
const MAX_LOG_LINES = 2_000;

export default function App() {
  const [boot, setBoot] = useState<BootstrapResponse>();
  const [bootError, setBootError] = useState("");
  const [slide, setSlide] = useState(0);
  const [overlay, setOverlay] = useState<Overlay>(null);

  const [settings, setSettings] = useState<AppSettings>();
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [tools, setTools] = useState<ToolInfo[]>([]);
  const [customThemes, setCustomThemes] = useState<CustomThemeColors[]>([]);
  const [logs, setLogs] = useState<Record<string, string[]>>({});

  const [tracks, setTracks] = useState<LibraryTrack[]>([]);
  const [scanning, setScanning] = useState(false);
  const [libraryRevision, setLibraryRevision] = useState(0);
  const scanSequence = useRef(0);
  const [scanError, setScanError] = useState("");

  const [toasts, setToasts] = useState<Toast[]>([]);
  const previousStatuses = useRef(new Map<string, JobStatus>());

  const toast = useCallback((message: string, tone: Toast["tone"] = "info") => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, tone, message }]);
    window.setTimeout(
      () => setToasts((current) => current.filter((item) => item.id !== id)),
      3800,
    );
  }, []);

  useEffect(() => {
    api
      .bootstrap()
      .then((result) => {
        setBoot(result);
        setSettings(result.settings);
        setJobs(result.jobs);
        setHistory(result.history);
        setTools(result.tools);
        setCustomThemes(result.customThemes);
        result.jobs.forEach((job) => previousStatuses.current.set(job.id, job.status));
        if (result.legacyImported) {
          toast("Settings and history imported from NekuLoader 3.1.", "success");
        }
        if (api.isTauri() && result.tools.some((tool) => tool.required && !tool.installed)) {
          setOverlay("settings");
          toast("Downloader components are missing.", "info");
        }
        if (result.settings.checkToolUpdates && api.isTauri()) {
          void api.refreshTools().then(setTools).catch(() => undefined);
        }
      })
      .catch((error) => setBootError(errorMessage(error)));
  }, [toast]);

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;
    let disposed = false;
    void api
      .onJobUpdate((job) => {
        const previous = previousStatuses.current.get(job.id);
        previousStatuses.current.set(job.id, job.status);
        setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
        if (previous === job.status || !settings) return;
        if (job.status === "downloading") {
          playSound("started", settings.soundEnabled, settings.soundVolume);
        }
        if (job.status === "completed") {
          playSound("complete", settings.soundEnabled, settings.soundVolume);
          const message = job.unavailableTracks
            ? `${job.title}: ${job.stage}`
            : `${job.title} ${translate(settings.language, "toast.ready")}`;
          toast(message, job.unavailableTracks ? "info" : "success");
          if (settings.trayNotifications) void api.notifyDownload(message).catch(() => undefined);
          setLibraryRevision((revision) => revision + 1);
          setHistory((current) => [
            { id: Date.now(), job, createdAt: new Date().toISOString() },
            ...current,
          ]);
        }
        if (job.status === "failed") {
          playSound("failed", settings.soundEnabled, settings.soundVolume);
          const message = job.error || `${job.title} failed.`;
          toast(message, "error");
          if (settings.trayNotifications) void api.notifyDownload(message).catch(() => undefined);
        }
      })
      .then((stop) => {
        if (disposed) stop();
        else unsubscribe = stop;
      }).catch(() => undefined);
    return () => { disposed = true; unsubscribe?.(); };
  }, [settings, toast]);

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;
    let disposed = false;
    void api
      .onJobLog((jobId, lines) => {
        setLogs((current) => ({
          ...current,
          [jobId]: [...(current[jobId] ?? []), ...lines].slice(-MAX_LOG_LINES),
        }));
      })
      .then((stop) => {
        if (disposed) stop();
        else unsubscribe = stop;
      }).catch(() => undefined);
    return () => { disposed = true; unsubscribe?.(); };
  }, []);

  const scan = useCallback(async () => {
    const sequence = ++scanSequence.current;
    setScanning(true);
    setScanError("");
    try {
      const result = await api.scanLibrary();
      if (sequence === scanSequence.current) setTracks(result);
    } catch (error) {
      if (sequence === scanSequence.current) setScanError(errorMessage(error));
    } finally {
      if (sequence === scanSequence.current) setScanning(false);
    }
  }, []);

  useEffect(() => {
    if (settings) void scan();
  }, [scan, libraryRevision, settings?.outputDir, settings?.scanSystemMusic, settings?.scanEverything,
    settings?.musicFolders]);

  const activeCount = useMemo(
    () => jobs.filter((job) => ACTIVE_STATUSES.includes(job.status)).length,
    [jobs],
  );
  const queuedCount = useMemo(
    () => jobs.filter((job) => job.status === "queued").length,
    [jobs],
  );

  if (bootError) {
    return (
      <div className="splash">
        <AsciiArt frameSet={catSleepFrames} playing={false} label="Neku" />
        <h1>NekuLoader could not start</h1>
        <p className="splash__error">{bootError}</p>
        <button type="button" className="button button--primary" onClick={() => location.reload()}>
          Try again
        </button>
      </div>
    );
  }

  if (!boot || !settings) {
    return (
      <div className="splash">
        <AsciiArt frameSet={wordmarkFrames} className="ascii--reveal" label="NekuLoader" />
        <p className="muted">Starting…</p>
      </div>
    );
  }

  const tr = (key: string) => translate(settings.language, key);
  const menus: Menu[] = [
    {
      label: tr("menu.file"),
      items: [
        { label: tr("menu.downloadFolder"), onSelect: () => void openOutputFolder() },
        { separator: true, label: "sep-1" },
        { label: tr("menu.rescan"), onSelect: () => void scan() },
      ],
    },
    {
      label: tr("menu.view"),
      items: [
        { label: tr("nav.download"), shortcut: "←→", checked: slide === 0, onSelect: () => setSlide(0) },
        { label: tr("nav.player"), checked: slide === 1, onSelect: () => setSlide(1) },
        { label: tr("nav.library"), checked: slide === 2, onSelect: () => setSlide(2) },
        { separator: true, label: "sep-2" },
        {
          label: queuedCount ? `${tr("menu.queue").replace("…", "")} (${queuedCount})…` : tr("menu.queue"),
          onSelect: () => setOverlay("queue"),
        },
        { label: tr("menu.history"), onSelect: () => setOverlay("history") },
      ],
    },
    {
      label: tr("menu.devices"),
      items: [{ label: tr("menu.transfer"), onSelect: () => setOverlay("devices") }],
    },
    {
      label: tr("menu.settings"),
      items: [{ label: tr("menu.preferences"), onSelect: () => setOverlay("settings") }],
    },
  ];

  async function openOutputFolder() {
    if (!settings) return;
    try {
      await api.reveal(settings.outputDir);
    } catch (error) {
      toast(errorMessage(error), "error");
    }
  }

  const slides: Slide[] = [
    {
      id: "download",
      label: tr("nav.download"),
      content: (
        <DownloadView
          settings={settings}
          onSettings={setSettings}
          toast={toast}
          onQueued={(job, start) => {
            setJobs((current) => [job, ...current]);
            previousStatuses.current.set(job.id, job.status);
            playSound("queued", settings.soundEnabled, settings.soundVolume);
            toast(start ? tr("toast.started") : tr("toast.queued"), "success");
            setOverlay("queue");
            if (start) {
              void api.startJob(job.id).catch((error) => toast(errorMessage(error), "error"));
            }
          }}
        />
      ),
    },
    { id: "player", label: tr("nav.player"), content: <PlayerScreen library={tracks} scanError={scanError} /> },
    {
      id: "library",
      label: tr("nav.library"),
      content: (
        <LibraryView
          tracks={tracks}
          scanning={scanning}
          error={scanError}
          onRescan={() => void scan()}
          onOpenPlayer={() => setSlide(1)}
        />
      ),
    },
  ];

  return (
    <LanguageProvider language={settings.language}>
    <ThemeProvider settings={settings} customThemes={customThemes}>
      <PlayerProvider initial={boot.playback} library={tracks}>
        <Shell
          menus={menus}
          slides={slides}
          slideIndex={slide}
          onSlideIndex={(index) => {
            if (index !== slide) playSound("select", settings.soundEnabled, settings.soundVolume);
            setSlide(index);
          }}
          settings={settings}
          version={boot.version}
          downloading={activeCount > 0}
          backgroundUrl={api.backgroundUrl(settings.backgroundImagePath)}
          player={slide === 1 ? undefined : <MiniPlayerBar onExpand={() => setSlide(1)} />}
          overlay={
            overlay && (
              <OverlayWindow
                title={tr(overlay === "queue" ? "queue.title" : overlay === "history" ? "ui.168" : overlay === "devices" ? "menu.devices" : "menu.settings")}
                wide={overlay === "settings" || overlay === "devices"}
                onClose={() => setOverlay(null)}
              >
                {overlay === "queue" && <QueueView jobs={jobs} logs={logs} toast={toast} />}
                {overlay === "history" && <HistoryView history={history} />}
                {overlay === "devices" && <DevicesView tracks={tracks} toast={toast} />}
                {overlay === "settings" && (
                  <SettingsView
                    settings={settings}
                    tools={tools}
                    customThemes={customThemes}
                    version={boot.version}
                    isWindows={boot.isWindows}
                    activeDownloads={activeCount}
                    onSettings={setSettings}
                    onTools={setTools}
                    onCustomThemes={setCustomThemes}
                    onRescan={() => void scan()}
                    toast={toast}
                  />
                )}
              </OverlayWindow>
            )
          }
        />

        <div className="toast-stack" aria-live="polite">
          {toasts.map((item) => (
            <div key={item.id} className={`toast toast--${item.tone}`}>
              <span aria-hidden="true">
                {item.tone === "success" ? "✓" : item.tone === "error" ? "✕" : "›"}
              </span>
              <span>{catMode(item.message, settings.catMode)}</span>
            </div>
          ))}
        </div>
      </PlayerProvider>
    </ThemeProvider>
    </LanguageProvider>
  );
}

/**
 * Legacy 3.1's `:3` easter egg — a settings toggle that softened status copy.
 * Applied to transient status text only, never to errors that must stay plain.
 */
function catMode(message: string, enabled: boolean): string {
  return enabled ? `${message} :3` : message;
}

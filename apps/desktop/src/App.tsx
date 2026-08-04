import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  AlertCircle,
  ArrowDownToLine,
  Check,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Download,
  ExternalLink,
  FolderOpen,
  HardDriveDownload,
  Hash,
  History as HistoryIcon,
  Image,
  Library,
  Link2,
  ListMusic,
  LoaderCircle,
  Music2,
  Pause,
  Play,
  RefreshCw,
  Search,
  Settings as SettingsIcon,
  ShieldCheck,
  Sparkles,
  Tags,
  Volume2,
  VolumeX,
  X,
  Zap,
} from "lucide-react";
import "./App.css";
import { PixelCat } from "./components/PixelCat";
import * as api from "./lib/api";
import { playSound } from "./lib/sound";
import type {
  AppSettings,
  BootstrapResponse,
  DownloadJob,
  DownloadRequest,
  HistoryEntry,
  JobStatus,
  MediaPreview,
  SourceDetection,
  ToolInfo,
} from "./types";

type View = "download" | "queue" | "library" | "history" | "settings";
type Toast = { id: number; tone: "success" | "error" | "info"; message: string };
type ComponentActionState = {
  phase: "idle" | "installing" | "success" | "error";
  message: string;
};
const navItems: Array<{ id: View; label: string; icon: typeof Download }> = [
  { id: "download", label: "Download", icon: Download },
  { id: "queue", label: "Queue", icon: ListMusic },
  { id: "library", label: "Library", icon: Library },
  { id: "history", label: "History", icon: HistoryIcon },
  { id: "settings", label: "Settings", icon: SettingsIcon },
];

const formatOptions = [
  { value: "mp3", label: "MP3", detail: "320 kbps", quality: "320" },
  { value: "flac", label: "FLAC", detail: "Lossless output", quality: "0" },
  { value: "opus", label: "Opus", detail: "256 kbps", quality: "256" },
  { value: "m4a", label: "M4A", detail: "256 kbps", quality: "256" },
  { value: "wav", label: "WAV", detail: "Uncompressed", quality: "0" },
  { value: "alac", label: "ALAC", detail: "Apple lossless", quality: "0" },
  { value: "vorbis", label: "Vorbis", detail: "320 kbps", quality: "320" },
];

function App() {
  const [boot, setBoot] = useState<BootstrapResponse>();
  const [bootError, setBootError] = useState("");
  const [view, setView] = useState<View>("download");
  const [jobs, setJobs] = useState<DownloadJob[]>([]);
  const [history, setHistory] = useState<HistoryEntry[]>([]);
  const [settings, setSettings] = useState<AppSettings>();
  const [tools, setTools] = useState<ToolInfo[]>([]);
  const [toasts, setToasts] = useState<Toast[]>([]);
  const previousStatuses = useRef(new Map<string, JobStatus>());

  const toast = useCallback((message: string, tone: Toast["tone"] = "info") => {
    const id = Date.now() + Math.random();
    setToasts((current) => [...current, { id, tone, message }]);
    window.setTimeout(() => setToasts((current) => current.filter((item) => item.id !== id)), 3800);
  }, []);

  useEffect(() => {
    api.bootstrap()
      .then((result) => {
        setBoot(result);
        setJobs(result.jobs);
        setHistory(result.history);
        setSettings(result.settings);
        setTools(result.tools);
        result.jobs.forEach((job) => previousStatuses.current.set(job.id, job.status));
        if (result.legacyImported) toast("Your legacy settings and history were imported.", "success");
        if (api.isTauri() && result.tools.some((tool) => tool.required && !tool.installed)) {
          setView("settings");
          toast("Downloader components are missing. Opened Settings → Components; choose Install required components.", "info");
        }
        if (result.settings.checkToolUpdates && api.isTauri()) {
          void api.refreshTools().then(setTools).catch(() => undefined);
        }
      })
      .catch((error) => setBootError(errorMessage(error)));
  }, [toast]);

  useEffect(() => {
    let unsubscribe: (() => void) | undefined;
    api.onJobUpdate((job) => {
      const previous = previousStatuses.current.get(job.id);
      previousStatuses.current.set(job.id, job.status);
      setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
      if (previous !== job.status && settings) {
        if (job.status === "downloading") playSound("started", settings.soundEnabled, settings.soundVolume);
        if (job.status === "completed") {
          playSound("complete", settings.soundEnabled, settings.soundVolume);
          toast(`${job.title} is ready.`, "success");
          setHistory((current) => [{ id: Date.now(), job, createdAt: new Date().toISOString() }, ...current]);
        }
        if (job.status === "failed") {
          playSound("failed", settings.soundEnabled, settings.soundVolume);
          toast(job.error || `${job.title} failed.`, "error");
        }
      }
    }).then((stop) => { unsubscribe = stop; });
    return () => unsubscribe?.();
  }, [settings, toast]);

  const activeCount = jobs.filter((job) => ["preparing", "downloading", "converting"].includes(job.status)).length;
  const queuedCount = jobs.filter((job) => job.status === "queued").length;

  if (bootError) {
    return (
      <div className="fatal-state">
        <img src="/nekuloader-icon.png" alt="NekuLoader" />
        <h1>NekuLoader could not start</h1>
        <p>{bootError}</p>
        <button className="button button--primary" onClick={() => location.reload()}><RefreshCw size={17} /> Try again</button>
      </div>
    );
  }

  if (!boot || !settings) {
    return (
      <div className="splash" aria-label="Starting NekuLoader">
        <div className="splash__halo" />
        <img src="/nekuloader-icon.png" alt="" />
        <h1>NekuLoader</h1>
        <p>Preparing your music desk…</p>
        <PixelCat state="detecting" />
      </div>
    );
  }

  return (
    <div className={`app ${settings.reducedMotion ? "reduced-motion" : ""}`}>
      {settings.animateBackground && <Particles />}
      <aside className="sidebar">
        <button className="brand" onClick={() => setView("download")} aria-label="NekuLoader home">
          <img src="/nekuloader-icon.png" alt="" />
          <span><strong>NekuLoader</strong><small>3.2 alpha</small></span>
        </button>
        <nav aria-label="Main navigation">
          {navItems.map(({ id, label, icon: Icon }) => (
            <button key={id} className={view === id ? "nav-item nav-item--active" : "nav-item"} onClick={() => setView(id)}>
              <Icon size={19} strokeWidth={1.8} />
              <span>{label}</span>
              {id === "queue" && queuedCount > 0 && <b>{queuedCount}</b>}
            </button>
          ))}
        </nav>
        <div className="sidebar__status">
          <div className={activeCount ? "status-orb status-orb--active" : "status-orb"} />
          <span>{activeCount ? `${activeCount} active` : "Ready for links"}</span>
        </div>
        <div className="sidebar__cat-track">
          <PixelCat state={activeCount ? "downloading" : "idle"} progress={activeCount ? 58 : 8} reducedMotion={settings.reducedMotion} />
        </div>
      </aside>

      <main className="main-shell">
        <header className="app-header">
          <div>
            <p className="eyebrow">{view === "download" ? "Smart link resolver" : view}</p>
            <h1>{navItems.find((item) => item.id === view)?.label}</h1>
          </div>
          <div className="header-actions">
            <span className="version-chip">v{boot.version}</span>
            <button className="icon-button" onClick={() => setView("settings")} aria-label="Open settings"><SettingsIcon size={18} /></button>
          </div>
        </header>

        <section className="content">
          {view === "download" && (
            <DownloadView
              settings={settings}
              onSettings={setSettings}
              onQueued={(job, start) => {
                setJobs((current) => [job, ...current]);
                previousStatuses.current.set(job.id, job.status);
                playSound("queued", settings.soundEnabled, settings.soundVolume);
                toast(start ? "Download started." : "Added to queue.", "success");
                setView("queue");
                if (start) void api.startJob(job.id).catch((error) => toast(errorMessage(error), "error"));
              }}
              toast={toast}
            />
          )}
          {view === "queue" && <QueueView jobs={jobs} toast={toast} />}
          {view === "library" && <LibraryView />}
          {view === "history" && <HistoryView history={history} />}
          {view === "settings" && (
            <SettingsView
              settings={settings}
              tools={tools}
              version={boot.version}
              activeDownloads={activeCount}
              onSettings={setSettings}
              onTools={setTools}
              toast={toast}
            />
          )}
        </section>
      </main>

      <div className="toast-stack" aria-live="polite">
        {toasts.map((item) => (
          <div key={item.id} className={`toast toast--${item.tone}`}>
            {item.tone === "success" ? <CheckCircle2 size={18} /> : item.tone === "error" ? <AlertCircle size={18} /> : <Sparkles size={18} />}
            <span>{item.message}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

function DownloadView({ settings, onSettings, onQueued, toast }: {
  settings: AppSettings;
  onSettings: (value: AppSettings) => void;
  onQueued: (job: DownloadJob, start: boolean) => void;
  toast: (message: string, tone?: Toast["tone"]) => void;
}) {
  const [url, setUrl] = useState("");
  const [detection, setDetection] = useState<SourceDetection>();
  const [preview, setPreview] = useState<MediaPreview>();
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState("");
  const [format, setFormat] = useState(settings.defaultFormat);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!url.trim()) {
      setDetection(undefined);
      setPreview(undefined);
      setError("");
      return;
    }
    let stale = false;
    const timer = window.setTimeout(async () => {
      try {
        setResolving(true);
        setError("");
        const detected = await api.detectSource(url);
        if (stale) return;
        setDetection(detected);
        if (!detected.supported) throw new Error(detected.message);
        const resolved = await api.inspectLink(url);
        if (stale) return;
        setPreview(resolved);
        playSound("detected", settings.soundEnabled, settings.soundVolume);
      } catch (reason) {
        if (!stale) {
          setPreview(undefined);
          setError(errorMessage(reason));
        }
      } finally {
        if (!stale) setResolving(false);
      }
    }, 480);
    return () => {
      stale = true;
      clearTimeout(timer);
    };
  }, [url, settings.soundEnabled, settings.soundVolume]);

  const selectedFormat = formatOptions.find((item) => item.value === format) || formatOptions[0];
  const providerCapability = api.providerCapability(preview?.provider || detection?.provider || "unknown");
  const spotifyAlacUnsupported = preview?.provider === "spotify" && format === "alac";
  const canSubmit = providerCapability.canDownload && !spotifyAlacUnsupported;
  const request = (): DownloadRequest => ({
    url: preview?.url || detection?.normalizedUrl || url,
    title: preview?.title,
    artist: preview?.artist,
    provider: preview?.provider || detection?.provider || "unknown",
    mediaKind: preview?.mediaKind || detection?.mediaKind || "unknown",
    format,
    quality: selectedFormat.quality,
    outputDir: settings.outputDir,
    trackNumbers: settings.trackNumbers,
    embedMetadata: settings.embedMetadata,
    embedArtwork: settings.embedArtwork,
  });

  async function queue(start: boolean) {
    if (!preview || submitting || !canSubmit) return;
    setSubmitting(true);
    try {
      const job = await api.queueDownload(request());
      onQueued(job, start);
      setUrl("");
      setPreview(undefined);
    } catch (reason) {
      toast(errorMessage(reason), "error");
    } finally {
      setSubmitting(false);
    }
  }

  async function paste() {
    try {
      setUrl(await navigator.clipboard.readText());
    } catch {
      toast("Clipboard access was blocked. Paste the link with Ctrl/Cmd+V.", "error");
    }
  }

  async function persistQuickSettings(next: AppSettings) {
    const previous = settings;
    onSettings(next);
    try {
      onSettings(await api.saveSettings(next));
    } catch (reason) {
      onSettings(previous);
      toast(`Could not save download settings: ${errorMessage(reason)}`, "error");
    }
  }

  return (
    <div className="download-layout">
      <section className="hero-card panel">
        <div className="hero-card__copy">
          <span className="kicker"><Zap size={14} /> Automatic link recognition</span>
          <h2>Drop a music link.<br /><em>Neku handles the rest.</em></h2>
          <p>YouTube, YouTube Music, and SoundCloud can download directly. Spotify uses SpotDL metadata matching. Deezer and Apple Music are recognized but not downloadable yet.</p>
        </div>
        <div className={`link-composer ${resolving ? "link-composer--loading" : ""} ${error ? "link-composer--error" : ""}`}>
          <Link2 size={20} />
          <input
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            placeholder="Paste a track, album, or playlist link"
            aria-label="Music link"
            spellCheck={false}
          />
          {url && <button className="clear-button" onClick={() => setUrl("")} aria-label="Clear link"><X size={16} /></button>}
          <button className="paste-button" onClick={paste}>Paste</button>
        </div>
        <div className="resolver-status" aria-live="polite">
          {resolving && <><LoaderCircle className="spin" size={15} /> Reading link and artwork…</>}
          {!resolving && detection?.supported && !error && <span className={providerCapability.canDownload ? "resolver-result" : "resolver-result resolver-result--limited"}>{providerCapability.canDownload ? <CheckCircle2 size={15} /> : <AlertCircle size={15} />} {detection.message} · {providerCapability.label}</span>}
          {!resolving && error && <span className="error-text"><AlertCircle size={15} /> {error}</span>}
          {!url && <><ShieldCheck size={15} /> Links are validated locally before a downloader starts.</>}
        </div>
      </section>

      {preview ? (
        <div className="download-workspace">
          <section className="preview-card panel">
            <div className="artwork">
              {preview.thumbnail ? <img src={preview.thumbnail} alt="" /> : <div className="artwork__fallback"><Music2 size={42} /></div>}
              <span className={`provider provider--${preview.provider}`}>{api.providerLabel(preview.provider)}</span>
            </div>
            <div className="preview-card__info">
              <p className="eyebrow">{preview.mediaKind}{preview.trackCount ? ` · ${preview.trackCount} tracks` : ""}</p>
              <h3>{preview.title}</h3>
              <p className="artist">{preview.artist}</p>
              {preview.album && <p className="album">{preview.album}</p>}
              {preview.provider === "spotify" && <div className="inline-warning"><AlertCircle size={15} /> {providerCapability.description}</div>}
              {preview.provider === "spotify" && preview.warning && <div className="inline-warning"><AlertCircle size={15} /> {preview.warning}</div>}
              {preview.provider !== "spotify" && (preview.warning || !providerCapability.canDownload) && <div className="inline-warning"><AlertCircle size={15} /> {!providerCapability.canDownload ? providerCapability.description : preview.warning}</div>}
            </div>
          </section>

          <section className="options-card panel">
            <div className="section-heading"><div><p className="eyebrow">Output</p><h3>Download setup</h3></div><SlidersIcon /></div>
            <label className="field-label" htmlFor="format">Audio format</label>
            <div className="format-grid" id="format">
              {formatOptions.map((option) => (
                <button key={option.value} disabled={!providerCapability.canDownload || (preview.provider === "spotify" && option.value === "alac")} title={preview.provider === "spotify" && option.value === "alac" ? "ALAC is not supported by SpotDL; choose FLAC or M4A" : undefined} className={format === option.value ? "format-option format-option--active" : "format-option"} onClick={() => setFormat(option.value)}>
                  <strong>{option.label}</strong><small>{option.detail}</small>{format === option.value && <Check size={14} />}
                </button>
              ))}
            </div>
            {spotifyAlacUnsupported && <div className="inline-warning"><AlertCircle size={15} /> ALAC is not supported by SpotDL. Choose FLAC or M4A for this Spotify link.</div>}
            <p className="quality-note">The selected format controls the output encoding. It cannot restore detail missing from the source{format === "flac" ? "; FLAC avoids another lossy encode but does not upgrade the source" : ""}.{preview.provider === "spotify" ? " Spotify audio is matched from another accessible source, so its source and quality can differ from Spotify's catalog stream." : ""}</p>
            <button className="output-path" onClick={async () => {
              const chosen = await api.chooseFolder();
              if (chosen) await persistQuickSettings({ ...settings, outputDir: chosen });
            }}><FolderOpen size={17} /><span><small>Save to</small><strong>{settings.outputDir}</strong></span><ChevronRight size={16} /></button>
            <div className="quick-options">
              <Toggle icon={Tags} label="Metadata" value={settings.embedMetadata} onChange={(value) => { void persistQuickSettings({ ...settings, embedMetadata: value }); }} />
              <Toggle icon={Image} label="Artwork" value={settings.embedArtwork} onChange={(value) => { void persistQuickSettings({ ...settings, embedArtwork: value }); }} />
              <Toggle icon={Hash} label="Track numbers" value={settings.trackNumbers} onChange={(value) => { void persistQuickSettings({ ...settings, trackNumbers: value }); }} />
            </div>
            <div className="primary-actions">
              <button className="button button--secondary" disabled={submitting || !canSubmit} onClick={() => queue(false)}><ListMusic size={17} /> Add to queue</button>
              <button className="button button--primary" disabled={submitting || !canSubmit} onClick={() => queue(true)}>{submitting ? <LoaderCircle className="spin" size={17} /> : <ArrowDownToLine size={17} />} {!providerCapability.canDownload ? "Unavailable for this source" : spotifyAlacUnsupported ? "Choose another format" : "Download now"}</button>
            </div>
          </section>
        </div>
      ) : (
        <section className="empty-preview panel">
          <div className="empty-preview__visual"><span /><span /><span /><Music2 size={34} /></div>
          <div><h3>Your preview will appear here</h3><p>Artwork, artist, collection size, format, and destination—before anything downloads.</p></div>
          <div className="source-availability"><span><strong>Direct downloads</strong>YouTube · YouTube Music · SoundCloud</span><span><strong>Metadata matching</strong>Spotify via SpotDL</span><span><strong>Recognition only</strong>Deezer · Apple Music</span></div>
        </section>
      )}
    </div>
  );
}

function QueueView({ jobs, toast }: { jobs: DownloadJob[]; toast: (message: string, tone?: Toast["tone"]) => void }) {
  const active = jobs.filter((job) => !["completed", "cancelled"].includes(job.status));
  const completed = jobs.filter((job) => job.status === "completed");
  return (
    <div className="view-stack">
      <div className="summary-strip">
        <Summary icon={HardDriveDownload} label="Active" value={String(active.filter((job) => ["downloading", "preparing", "converting"].includes(job.status)).length)} />
        <Summary icon={Clock3} label="Waiting" value={String(active.filter((job) => ["queued", "paused", "interrupted"].includes(job.status)).length)} />
        <Summary icon={CheckCircle2} label="Completed" value={String(completed.length)} />
      </div>
      <section className="panel list-panel">
        <div className="section-heading"><div><p className="eyebrow">Persistent queue</p><h2>Downloads</h2></div><span className="muted">Jobs survive app restarts</span></div>
        {jobs.length === 0 ? <Empty icon={ListMusic} title="The queue is quiet" text="Add a link from Download and it will appear here." /> : (
          <div className="job-list">
            {jobs.map((job) => <JobRow key={job.id} job={job} toast={toast} />)}
          </div>
        )}
      </section>
    </div>
  );
}

function JobRow({ job, toast }: { job: DownloadJob; toast: (message: string, tone?: Toast["tone"]) => void }) {
  const running = ["preparing", "downloading", "converting"].includes(job.status);
  const canStart = ["queued", "paused", "interrupted", "failed", "cancelled"].includes(job.status);
  return (
    <article className={`job-row job-row--${job.status}`}>
      <div className="job-row__icon"><Music2 size={20} /></div>
      <div className="job-row__main">
        <div className="job-row__top"><div><h3>{job.title}</h3><p>{job.artist || api.providerLabel(job.request.provider)} · {job.request.format.toUpperCase()}</p></div><StatusChip status={job.status} /></div>
        <div className="progress-line"><span style={{ width: `${job.progress}%` }} /></div>
        <div className="job-row__meta"><span>{job.stage}</span><span>{job.speed}{job.speed && job.eta ? " · " : ""}{job.eta ? `${job.eta} left` : ""}</span><strong>{Math.round(job.progress)}%</strong></div>
        {job.error && <p className="job-error"><AlertCircle size={14} /> {job.error}</p>}
      </div>
      <div className="job-row__actions">
        {running && <button className="icon-button" aria-label="Pause" onClick={() => api.pauseJob(job.id).catch((error) => toast(errorMessage(error), "error"))}><Pause size={17} /></button>}
        {canStart && <button className="icon-button icon-button--accent" aria-label="Start or resume" onClick={() => api.startJob(job.id).catch((error) => toast(errorMessage(error), "error"))}><Play size={17} /></button>}
        {job.status !== "completed" && <button className="icon-button" aria-label="Cancel" onClick={() => api.cancelJob(job.id).catch((error) => toast(errorMessage(error), "error"))}><X size={17} /></button>}
      </div>
    </article>
  );
}

function LibraryView() {
  const [tracks, setTracks] = useState<api.LibraryTrack[]>([]);
  const [query, setQuery] = useState("");
  const [scanning, setScanning] = useState(false);
  const [scanError, setScanError] = useState("");
  const [hasScanned, setHasScanned] = useState(false);

  const scan = useCallback(async () => {
    setScanning(true);
    setScanError("");
    try {
      setTracks(await api.scanLibrary());
      setHasScanned(true);
    } catch (error) {
      setScanError(errorMessage(error));
      setHasScanned(true);
    } finally {
      setScanning(false);
    }
  }, []);

  useEffect(() => { void scan(); }, [scan]);

  const normalizedQuery = query.trim().toLocaleLowerCase();
  const visibleTracks = normalizedQuery
    ? tracks.filter((track) => [track.title, track.artist, track.album, track.format, track.path].some((value) => value.toLocaleLowerCase().includes(normalizedQuery)))
    : tracks;

  return (
    <div className="view-stack">
      <div className="toolbar panel"><div className="search-box"><Search size={17} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search title, album, format, or path" aria-label="Search library" /></div><button className="button button--secondary" onClick={scan} disabled={scanning}>{scanning ? <LoaderCircle className="spin" size={16} /> : <RefreshCw size={16} />} {scanning ? "Scanning…" : "Scan folder"}</button></div>
      <section className="panel list-panel">
        <div className="section-heading"><div><p className="eyebrow">Local collection</p><h2>Music folder</h2></div><span className="muted">{normalizedQuery ? `${visibleTracks.length} of ${tracks.length}` : tracks.length} item{tracks.length === 1 ? "" : "s"}</span></div>
        {scanError && <div className="operation-state operation-state--error" role="alert"><AlertCircle size={17} /><div><strong>Library scan failed</strong><p>{scanError}</p></div></div>}
        {!scanError && scanning && tracks.length === 0 ? <Empty icon={RefreshCw} title="Scanning your music folder" text="NekuLoader is looking for supported local audio files." /> : visibleTracks.length === 0 ? <Empty icon={Library} title={normalizedQuery ? "No tracks match that search" : hasScanned ? "No audio files found" : "Your library starts here"} text={normalizedQuery ? "Try a title, album, format, or folder name." : "Download music or choose a different output folder in Settings, then scan again."} /> : (
          <div className="library-grid">{visibleTracks.map((track) => <article className="library-card" key={track.id}><div><Music2 size={32} /><span>{track.format}</span></div><h3 title={track.title}>{track.title}</h3><p title={[track.artist, track.album].filter(Boolean).join(" · ") || track.path}>{[track.artist, track.album].filter(Boolean).join(" · ") || "Local audio"}</p><small>{formatBytes(track.sizeBytes)} · {new Date(track.modifiedAt).toLocaleDateString()}</small><button onClick={() => api.reveal(track.path)}><FolderOpen size={14} /> Reveal</button></article>)}</div>
        )}
      </section>
    </div>
  );
}

function HistoryView({ history }: { history: HistoryEntry[] }) {
  const [query, setQuery] = useState("");
  const normalizedQuery = query.trim().toLocaleLowerCase();
  const visibleHistory = normalizedQuery
    ? history.filter((entry) => {
        const values = entry.job
          ? [entry.job.title, entry.job.artist, api.providerLabel(entry.job.request.provider), entry.job.request.format]
          : [entry.legacyText || "", "Imported from NekuLoader 3.1"];
        return values.some((value) => value.toLocaleLowerCase().includes(normalizedQuery));
      })
    : history;
  return (
    <section className="panel list-panel">
      <div className="section-heading"><div><p className="eyebrow">Activity archive</p><h2>History</h2></div><div className="search-box search-box--small"><Search size={16} /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Filter history" aria-label="Filter history" /></div></div>
      {visibleHistory.length === 0 ? <Empty icon={HistoryIcon} title={history.length ? "No history matches that search" : "No downloads yet"} text={history.length ? "Try a title, artist, provider, or format." : "Successful jobs and imported legacy records appear here."} /> : (
        <div className="history-list">{visibleHistory.map((entry) => <article key={entry.id}><div className="history-list__icon"><CheckCircle2 size={17} /></div><div><h3>{entry.job?.title || entry.legacyText || "Legacy download"}</h3><p>{entry.job ? `${api.providerLabel(entry.job.request.provider)} · ${entry.job.request.format.toUpperCase()}` : "Imported from NekuLoader 3.1"}</p></div><time>{new Date(entry.createdAt).toLocaleDateString()}</time></article>)}</div>
      )}
    </section>
  );
}

function SettingsView({ settings, tools, version, activeDownloads, onSettings, onTools, toast }: {
  settings: AppSettings;
  tools: ToolInfo[];
  version: string;
  activeDownloads: number;
  onSettings: (value: AppSettings) => void;
  onTools: (value: ToolInfo[]) => void;
  toast: (message: string, tone?: Toast["tone"]) => void;
}) {
  const [draft, setDraft] = useState(settings);
  const [checking, setChecking] = useState(false);
  const [componentAction, setComponentAction] = useState<ComponentActionState>({ phase: "idle", message: "" });
  useEffect(() => setDraft(settings), [settings]);

  const missingRequired = tools.filter((tool) => tool.required && !tool.installed);
  const installingComponents = componentAction.phase === "installing";

  async function save() {
    try {
      const saved = await api.saveSettings(draft);
      onSettings(saved);
      playSound("saved", saved.soundEnabled, saved.soundVolume);
      toast("Settings saved.", "success");
    } catch (error) { toast(errorMessage(error), "error"); }
  }

  async function refresh() {
    setChecking(true);
    try {
      onTools(await api.refreshTools());
      toast("Component health checks complete.", "success");
    } catch (error) { toast(errorMessage(error), "error"); }
    finally { setChecking(false); }
  }

  async function installOrUpdateComponents() {
    setComponentAction({
      phase: "installing",
      message: "Downloading, verifying, and health-checking managed components. Keep NekuLoader open; this can take a few minutes.",
    });
    try {
      const installed = await api.installComponents();
      onTools(installed);
      setComponentAction({
        phase: "success",
        message: "Required components passed their health checks. Optional SpotDL status is shown above.",
      });
      toast("Required components are ready.", "success");
    } catch (error) {
      const message = errorMessage(error);
      setComponentAction({ phase: "error", message });
      toast(message, "error");
    }
  }

  return (
    <div className="settings-layout">
      <section className="panel settings-card">
        <div className="section-heading"><div><p className="eyebrow">General</p><h2>Downloads</h2></div><FolderOpen size={20} /></div>
        <label className="setting-field"><span>Default output folder<small>New jobs save here unless changed.</small></span><button className="path-control" onClick={async () => { const folder = await api.chooseFolder(); if (folder) setDraft({ ...draft, outputDir: folder }); }}>{draft.outputDir}<ChevronRight size={15} /></button></label>
        <label className="setting-field"><span>Concurrent downloads<small>One is safest for rate-limited providers.</small></span><select value={draft.concurrentDownloads} onChange={(event) => setDraft({ ...draft, concurrentDownloads: Number(event.target.value) })}><option value={1}>1 job</option><option value={2}>2 jobs</option><option value={3}>3 jobs</option><option value={4}>4 jobs</option></select></label>
        <Toggle icon={Tags} label="Embed metadata" value={draft.embedMetadata} onChange={(value) => setDraft({ ...draft, embedMetadata: value })} />
        <Toggle icon={Image} label="Embed artwork" value={draft.embedArtwork} onChange={(value) => setDraft({ ...draft, embedArtwork: value })} />
        <Toggle icon={Hash} label="Number collection tracks" value={draft.trackNumbers} onChange={(value) => setDraft({ ...draft, trackNumbers: value })} />
      </section>

      <section className="panel settings-card">
        <div className="section-heading"><div><p className="eyebrow">Classic personality</p><h2>Appearance & sound</h2></div><Sparkles size={20} /></div>
        <Toggle icon={Sparkles} label="Pixel particles" value={draft.animateBackground} onChange={(value) => setDraft({ ...draft, animateBackground: value })} />
        <Toggle icon={Zap} label="Reduced motion" value={draft.reducedMotion} onChange={(value) => setDraft({ ...draft, reducedMotion: value })} />
        <Toggle icon={draft.soundEnabled ? Volume2 : VolumeX} label="Interface sounds" value={draft.soundEnabled} onChange={(value) => { setDraft({ ...draft, soundEnabled: value }); if (value) playSound("detected", true, draft.soundVolume); }} />
        <label className="volume-setting"><span>Sound volume</span><input type="range" min="0" max="1" step="0.05" value={draft.soundVolume} onChange={(event) => setDraft({ ...draft, soundVolume: Number(event.target.value) })} /></label>
      </section>

      <section className="panel settings-card settings-card--wide">
        <div className="section-heading"><div><p className="eyebrow">Managed toolchain</p><h2>Components & updates</h2></div><button className="button button--secondary" onClick={refresh} disabled={checking || installingComponents}>{checking ? <LoaderCircle className="spin" size={16} /> : <RefreshCw size={16} />} Check tools</button></div>
        <div className="tool-grid">{tools.map((tool) => <article key={tool.name} className={tool.installed ? "tool-card tool-card--ready" : "tool-card"}><div className="tool-card__status">{tool.installed ? <CheckCircle2 size={17} /> : <AlertCircle size={17} />}</div><div><h3>{tool.name}</h3><p>{tool.version || tool.status}</p><small>{tool.path || (tool.required ? "Required" : "Optional")}</small></div></article>)}</div>
        <div className="update-preferences">
          <Toggle icon={RefreshCw} label="Check component health on startup" value={draft.checkToolUpdates} onChange={(value) => setDraft({ ...draft, checkToolUpdates: value })} />
          <p>Startup checks only report local component health. They never download or install the toolchain; use the explicit action below.</p>
        </div>
        <div className="component-manager">
          <div className="component-manager__copy"><ShieldCheck size={19} /><span><strong>{missingRequired.length ? `${missingRequired.length} required component${missingRequired.length === 1 ? "" : "s"} missing` : "Required components detected"}</strong><small>Installs or updates yt-dlp, Deno, FFmpeg/FFprobe and, when a managed build exists for this platform, optional SpotDL. Release assets are SHA-256 verified from published metadata and executables are health-checked before promotion. Optional SpotDL failure does not block the required toolchain.</small></span></div>
          <button className="button button--primary" onClick={installOrUpdateComponents} disabled={installingComponents || checking || activeDownloads > 0 || !api.isTauri()} title={activeDownloads > 0 ? "Pause active downloads before updating components" : !api.isTauri() ? "Open the installed desktop app to manage components" : undefined}>{installingComponents ? <LoaderCircle className="spin" size={16} /> : <HardDriveDownload size={16} />} {installingComponents ? "Installing components…" : missingRequired.length ? "Install required components" : "Update required components"}</button>
        </div>
        {activeDownloads > 0 && <div className="operation-state operation-state--warning" role="status"><AlertCircle size={17} /><div><strong>Component updates are paused</strong><p>Pause or finish {activeDownloads} active download{activeDownloads === 1 ? "" : "s"}, then try again.</p></div></div>}
        {componentAction.phase !== "idle" && <div className={`operation-state operation-state--${componentAction.phase}`} role={componentAction.phase === "error" ? "alert" : "status"} aria-live="polite">{componentAction.phase === "installing" ? <LoaderCircle className="spin" size={17} /> : componentAction.phase === "success" ? <CheckCircle2 size={17} /> : <AlertCircle size={17} />}<div><strong>{componentAction.phase === "installing" ? "Installing components" : componentAction.phase === "success" ? "Components ready" : "Component update failed"}</strong><p>{componentAction.message}</p>{componentAction.phase === "installing" && <div className="operation-progress operation-progress--indeterminate" role="progressbar" aria-label="Installing required components"><span /></div>}</div></div>}
        <div className="update-row"><div><img src="/nekuloader-icon-original.png" alt="" /><span><strong>NekuLoader {version}</strong><small>Application updates are manual for this alpha. No updater or platform-signing credential is embedded in the app or release workflow.</small></span></div><a className="button button--secondary" href={api.releasesUrl} target="_blank" rel="noreferrer">GitHub Releases <ExternalLink size={14} /></a></div>
      </section>

      <section className="panel settings-card settings-card--wide about-strip"><div><p className="eyebrow">NekuLoader 3.2</p><h2>Legacy soul. Native speed.</h2><p>The Java edition remains preserved as 3.1 while this Rust-powered edition reaches full cross-platform parity.</p></div><a href="https://github.com/deviverr/NekuLoader" target="_blank" rel="noreferrer">GitHub <ExternalLink size={15} /></a></section>
      <div className="settings-save"><button className="button button--primary" onClick={save}><Check size={17} /> Save settings</button></div>
    </div>
  );
}

function Toggle({ icon: Icon, label, value, onChange }: { icon: typeof Tags; label: string; value: boolean; onChange: (value: boolean) => void }) {
  return <button type="button" className="toggle-row" role="switch" aria-checked={value} onClick={() => onChange(!value)}><Icon size={16} /><span>{label}</span><i className={value ? "switch switch--on" : "switch"}><b /></i></button>;
}

function Summary({ icon: Icon, label, value }: { icon: typeof Download; label: string; value: string }) {
  return <div><Icon size={18} /><span><small>{label}</small><strong>{value}</strong></span></div>;
}

function StatusChip({ status }: { status: JobStatus }) {
  return <span className={`status-chip status-chip--${status}`}>{status}</span>;
}

function Empty({ icon: Icon, title, text }: { icon: typeof Download; title: string; text: string }) {
  return <div className="empty-state"><Icon size={30} /><h3>{title}</h3><p>{text}</p></div>;
}

function SlidersIcon() {
  return <span className="sliders-icon"><i /><i /><i /></span>;
}

function Particles() {
  const particles = useMemo(() => Array.from({ length: 24 }, (_, index) => ({
    left: `${(index * 37) % 100}%`, delay: `${-((index * 0.73) % 9)}s`, duration: `${7 + (index % 6)}s`, size: `${2 + (index % 3)}px`,
  })), []);
  return <div className="particles" aria-hidden="true">{particles.map((particle, index) => <i key={index} style={{ left: particle.left, animationDelay: particle.delay, animationDuration: particle.duration, width: particle.size, height: particle.size }} />)}</div>;
}

function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === "string") return error;
  return "Something went wrong.";
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KB", "MB", "GB"];
  let value = bytes / 1024;
  let unit = units[0];
  for (let index = 1; value >= 1024 && index < units.length; index += 1) {
    value /= 1024;
    unit = units[index];
  }
  return `${value.toFixed(value >= 10 ? 0 : 1)} ${unit}`;
}

export default App;

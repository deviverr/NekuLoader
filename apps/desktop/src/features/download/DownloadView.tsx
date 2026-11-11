import { useEffect, useRef, useState } from "react";
import { useT } from "../../lib/i18n";
import { AsciiArt } from "../../ascii/AsciiArt";
import { wordmarkFrames } from "../../ascii/frames/wordmark";
import { Notice, Panel, SectionHeading, Toggle } from "../../components";
import * as api from "../../lib/api";
import { errorMessage, formatFor, formatOptions } from "../../lib/formats";
import { playSound } from "../../lib/sound";
import type {
  AppSettings,
  DownloadJob,
  DownloadRequest,
  MediaPreview,
  SourceDetection,
} from "../../types";
import "./download.css";

/**
 * Pulls the link out of pasted share text ("Listen on Deezer: https://…") and
 * keeps only the last one when two links were pasted back to back.
 */
/** "open.spotify.com/album/4aw…" — enough to recognise, short enough to fit. */
function shortLink(link: string): string {
  try {
    const parsed = new URL(link);
    const text = `${parsed.hostname.replace(/^www\./, "")}${parsed.pathname}`;
    return text.length > 42 ? `${text.slice(0, 41)}…` : text;
  } catch {
    return link;
  }
}

export function extractLink(raw: string): string {
  const text = raw.trim();
  const starts = [...text.matchAll(/https?:\/\//gi)].map((match) => match.index ?? 0);
  if (starts.length === 0) return text;
  const tail = text.slice(starts[starts.length - 1]);
  return tail.split(/\s/)[0].replace(/[)\].,;!?'"»]+$/, "");
}

export function DownloadView({
  settings,
  onSettings,
  onQueued,
  toast,
}: {
  settings: AppSettings;
  onSettings: (value: AppSettings) => void;
  onQueued: (job: DownloadJob, start: boolean) => void;
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const [url, setUrl] = useState("");
  const [detection, setDetection] = useState<SourceDetection>();
  const [preview, setPreview] = useState<MediaPreview>();
  const [resolving, setResolving] = useState(false);
  const [error, setError] = useState("");
  const [format, setFormat] = useState(settings.defaultFormat);
  const [submitting, setSubmitting] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [clipboardLink, setClipboardLink] = useState<string>();
  const inputRef = useRef<HTMLInputElement>(null);
  const lastClipboard = useRef("");
  const t = useT();

  // Offer a link the user just copied elsewhere, once, when the window regains
  // focus — no paste button, no permission bubble.
  useEffect(() => {
    const sniff = async () => {
      try {
        const link = extractLink(await api.readClipboard());
        if (link === lastClipboard.current || !/^https?:\/\//i.test(link)) return;
        lastClipboard.current = link;
        const detected = await api.detectSource(link);
        if (detected.supported) setClipboardLink(detected.normalizedUrl);
      } catch {
        // Clipboard access is a convenience; typing and pasting still work.
      }
    };
    void sniff();
    window.addEventListener("focus", sniff);
    return () => window.removeEventListener("focus", sniff);
  }, []);

  // ⌘V / Ctrl+V anywhere outside another text field lands in the composer.
  useEffect(() => {
    const onKey = async (event: KeyboardEvent) => {
      if (!(event.metaKey || event.ctrlKey) || event.key.toLowerCase() !== "v") return;
      const target = event.target as HTMLElement | null;
      if (target && (target.tagName === "INPUT" || target.tagName === "TEXTAREA" || target.isContentEditable)) return;
      event.preventDefault();
      try {
        setUrl(extractLink(await api.readClipboard()));
        setClipboardLink(undefined);
        inputRef.current?.focus();
      } catch {
        inputRef.current?.focus();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // Debounced resolve: typing a long link should not fire a request per key.
  useEffect(() => {
    setDetection(undefined);
    setPreview(undefined);
    setError("");
    setResolving(Boolean(url.trim()));
    if (!url.trim()) return;
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
      window.clearTimeout(timer);
    };
  }, [url, settings.soundEnabled, settings.soundVolume]);

  const selected = formatFor(format);
  const provider = preview?.provider ?? detection?.provider ?? "unknown";
  const capability = api.providerCapability(provider);
  // SpotDL cannot emit ALAC, so that pairing is blocked rather than failed late.
  const spotifyAlac = provider === "spotify" && format === "alac";
  const canSubmit = !resolving && capability.canDownload && !spotifyAlac;

  async function persist(next: AppSettings) {
    const previous = settings;
    onSettings(next);
    try {
      onSettings(await api.saveSettings(next));
    } catch (reason) {
      onSettings(previous);
      toast(`Could not save: ${errorMessage(reason)}`, "error");
    }
  }

  async function queue(start: boolean) {
    if (!preview || submitting || !canSubmit) return;
    setSubmitting(true);
    try {
      const request: DownloadRequest = {
        url: preview.url || detection?.normalizedUrl || url,
        title: preview.title,
        artist: preview.artist,
        provider,
        mediaKind: preview.mediaKind ?? detection?.mediaKind ?? "unknown",
        format,
        quality: selected.quality,
        outputDir: settings.outputDir,
        trackNumbers: settings.trackNumbers,
        embedMetadata: settings.embedMetadata,
        embedArtwork: settings.embedArtwork,
      };
      onQueued(await api.queueDownload(request), start);
      setUrl("");
      setPreview(undefined);
    } catch (reason) {
      toast(errorMessage(reason), "error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="view-stack">
      <div className="composer">
        <AsciiArt
          frameSet={wordmarkFrames}
          density="full"
          className={`ascii--wordmark composer__art${resolving ? " ascii--busy" : ""}`}
          label="NekuLoader"
        />

        <div
          className={`composer__drop${dragging ? " composer__drop--over" : ""}${url ? " composer__drop--filled" : ""}`}
          onDragOver={(event) => {
            event.preventDefault();
            setDragging(true);
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={(event) => {
            event.preventDefault();
            setDragging(false);
            const dropped =
              event.dataTransfer.getData("text/uri-list") || event.dataTransfer.getData("text/plain");
            if (dropped) setUrl(extractLink(dropped));
          }}
        >
          <div className="field composer__field">
            <span className="field__marker" aria-hidden="true">
              {resolving ? "◌" : "›"}
            </span>
            <input
              ref={inputRef}
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              onPaste={(event) => {
                const pasted = event.clipboardData.getData("text");
                const link = extractLink(pasted);
                if (link !== pasted) {
                  event.preventDefault();
                  setUrl(link);
                }
              }}
              onKeyDown={(event) => {
                if (event.key === "Enter" && preview && canSubmit) void queue(true);
                if (event.key === "Escape") setUrl("");
              }}
              placeholder={t("composer.placeholder")}
              aria-label="Music link"
              spellCheck={false}
            />
            {url && (
              <button
                type="button"
                className="icon-button"
                aria-label="Clear link"
                onClick={() => setUrl("")}
              >
                ✕
              </button>
            )}
          </div>
          {!url && clipboardLink && (
            <button
              type="button"
              className="clipboard-chip"
              onClick={() => {
                setUrl(clipboardLink);
                setClipboardLink(undefined);
              }}
            >
              <span className="clipboard-chip__key">⌘V</span>
              <span className="clipboard-chip__text">
                {t("composer.clipboard")} <b>{shortLink(clipboardLink)}</b>
              </span>
            </button>
          )}
          {!url && !clipboardLink && (
            <p className="composer__hint">{t("composer.hint")}</p>
          )}
        </div>

        <p className="composer__status" aria-live="polite">
          {resolving && t("composer.reading")}
          {!resolving && error && <span className="composer__error">{error}</span>}
          {!resolving && !error && detection?.supported && (
            <>
              {api.providerLabel(detection.provider)} · {t(`kind.${detection.mediaKind}`)} ·{" "}
              {capability.label === "Direct download" ? t("cap.direct") : t("cap.matching")}
            </>
          )}
        </p>
      </div>

      {preview ? (
        <div className="download-grid">
          <Panel>
            <SectionHeading eyebrow={preview.mediaKind} title={preview.title} />
            <p className="preview__artist">{preview.artist}</p>
            {preview.album && <p className="muted">{preview.album}</p>}
            {preview.trackCount ? (
              <p className="muted">
                {preview.trackCount} {preview.trackCount === 1 ? t("download.track") : t("download.tracks")}
              </p>
            ) : null}
            {!capability.canDownload && <Notice tone="warning">{capability.description}</Notice>}
            {capability.canDownload && provider === "spotify" && (
              <Notice tone="info">{capability.description}</Notice>
            )}
            {preview.warning && <Notice tone="warning">{preview.warning}</Notice>}
          </Panel>

          <Panel>
            <SectionHeading eyebrow={t("download.output")} title={t("download.setup")} />
            <div className="format-grid" role="group" aria-label="Audio format">
              {formatOptions.map((option) => {
                const blocked = provider === "spotify" && option.value === "alac";
                return (
                  <button
                    key={option.value}
                    type="button"
                    disabled={!capability.canDownload || blocked}
                    title={blocked ? "SpotDL cannot produce ALAC; choose FLAC or M4A" : undefined}
                    className={
                      format === option.value ? "format format--active" : "format"
                    }
                    onClick={() => {
                      setFormat(option.value);
                      playSound("format", settings.soundEnabled, settings.soundVolume, option.value);
                    }}
                  >
                    <strong>{option.label}</strong>
                    <small>{option.detail}</small>
                  </button>
                );
              })}
            </div>

            {spotifyAlac && (
              <Notice tone="warning">
                ALAC is not supported by SpotDL. Choose FLAC or M4A for this Spotify link.
              </Notice>
            )}

            <p className="muted quality-note">
              {t("download.formatNote").replace(/\.$/, "")}
              {format === "flac"
                ? "; FLAC avoids another lossy encode but does not upgrade the source"
                : ""}
              .
            </p>

            <button
              type="button"
              className="output-path"
              onClick={async () => {
                const chosen = await api.chooseFolder();
                if (chosen) await persist({ ...settings, outputDir: chosen });
              }}
            >
              <small>{t("download.saveTo")}</small>
              <strong>{settings.outputDir}</strong>
            </button>

            <div className="quick-options">
              <Toggle
                label={t("download.metadata")}
                value={settings.embedMetadata}
                onChange={(value) => void persist({ ...settings, embedMetadata: value })}
              />
              <Toggle
                label={t("download.artwork")}
                value={settings.embedArtwork}
                onChange={(value) => void persist({ ...settings, embedArtwork: value })}
              />
              <Toggle
                label={t("download.trackNumbers")}
                value={settings.trackNumbers}
                onChange={(value) => void persist({ ...settings, trackNumbers: value })}
              />
            </div>

            <div className="primary-actions">
              <button
                type="button"
                className="button"
                disabled={submitting || !canSubmit}
                onClick={() => void queue(false)}
              >
                {t("download.addToQueue")}
              </button>
              <button
                type="button"
                className="button button--primary"
                disabled={submitting || !canSubmit}
                onClick={() => void queue(true)}
              >
                {!capability.canDownload
                  ? "Unavailable"
                  : spotifyAlac
                    ? "Choose another format"
                    : t("download.now")}
              </button>
            </div>
          </Panel>
        </div>
      ) : null}
    </div>
  );
}

import { useT } from "../../lib/i18n";
import { useCallback, useEffect, useState } from "react";
import {
  Empty,
  Notice,
  Panel,
  Progress,
  SectionHeading,
} from "../../components";
import * as api from "../../lib/api";
import { errorMessage } from "../../lib/formats";
import type { AndroidDevice, LibraryTrack } from "../../types";
import "./devices.css";

const DEFAULT_DESTINATION = "/sdcard/Music/NekuLoader";

export function DevicesView({
  tracks,
  toast,
}: {
  tracks: LibraryTrack[];
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const [tab, setTab] = useState<"wifi" | "android" | "ios">("wifi");

  return (
    <div className="view-stack">
      <div className="tabs" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={tab === "wifi"}
          className={tab === "wifi" ? "tab tab--active" : "tab"}
          onClick={() => setTab("wifi")}
        >
          {t("ui.123")}{" "}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "android"}
          className={tab === "android" ? "tab tab--active" : "tab"}
          onClick={() => setTab("android")}
        >
          {t("ui.124")}{" "}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={tab === "ios"}
          className={tab === "ios" ? "tab tab--active" : "tab"}
          onClick={() => setTab("ios")}
        >
          {t("ui.125")}{" "}
        </button>
      </div>

      {tab === "wifi" && <WifiPanel tracks={tracks} toast={toast} />}
      {tab === "android" && <AndroidPanel tracks={tracks} toast={toast} />}
      {tab === "ios" && <IOSPanel />}
    </div>
  );
}

function AndroidPanel({
  tracks,
  toast,
}: {
  tracks: LibraryTrack[];
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const [devices, setDevices] = useState<AndroidDevice[]>([]);
  const [selected, setSelected] = useState<string>();
  const [destination, setDestination] = useState(DEFAULT_DESTINATION);
  const [scanning, setScanning] = useState(false);
  const [error, setError] = useState("");
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [transfer, setTransfer] = useState<{
    completed: number;
    total: number;
  } | null>(null);

  const scan = useCallback(async () => {
    setScanning(true);
    setError("");
    try {
      const found = await api.listAndroidDevices();
      setDevices(found);
      setSelected((current) =>
        found.some((d) => d.id === current && d.state === "device")
          ? current
          : found.find((d) => d.state === "device")?.id,
      );
    } catch (reason) {
      setError(errorMessage(reason));
    } finally {
      setScanning(false);
    }
  }, []);

  useEffect(() => {
    void scan();
  }, [scan]);

  useEffect(() => {
    let unlisten: (() => void) | undefined;
    void api
      .onTransferProgress((event) => {
        setTransfer({ completed: event.completed, total: event.total });
        if (event.error) toast(event.error, "error");
      })
      .then((stop) => {
        unlisten = stop;
      });
    return () => unlisten?.();
  }, [toast]);

  async function push() {
    if (!selected || chosen.size === 0) return;
    try {
      setTransfer({ completed: 0, total: chosen.size });
      await api.pushToAndroid(selected, [...chosen], destination);
      setTransfer(null);
      toast(t("ui.126"), "success");
    } catch (reason) {
      setTransfer(null);
      toast(errorMessage(reason), "error");
    }
  }

  const ready = devices.filter((device) => device.state === "device");

  return (
    <>
      <Panel>
        <SectionHeading
          eyebrow={t("ui.127")}
          title={t("ui.128")}
          aside={
            <button
              type="button"
              className="button"
              onClick={() => void scan()}
              disabled={scanning}
            >
              {scanning ? t("ui.129") : t("ui.130")}
            </button>
          }
        />

        {error && <Notice tone="error">{error}</Notice>}

        {devices.length === 0 ? (
          <Empty title={t("ui.131")} text={t("ui.132")} />
        ) : (
          <div className="device-list">
            {devices.map((device) => (
              <label
                key={device.id}
                className={
                  device.id === selected ? "device device--active" : "device"
                }
              >
                <input
                  type="radio"
                  name="android-device"
                  checked={device.id === selected}
                  disabled={device.state !== "device"}
                  onChange={() => setSelected(device.id)}
                />
                <div>
                  <strong>{device.model || device.id}</strong>
                  <small>{device.id}</small>
                </div>
                <span
                  className={
                    device.state === "device" ? "chip chip--completed" : "chip"
                  }
                >
                  {device.state}
                </span>
              </label>
            ))}
          </div>
        )}
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.133")} title={t("ui.134")} />

        <label className="setting">
          <span>
            {t("ui.135")} <small>{t("ui.136")} </small>
          </span>
          <input
            className="text-input"
            value={destination}
            onChange={(event) => setDestination(event.target.value)}
          />
        </label>

        {tracks.length === 0 ? (
          <Empty title={t("ui.137")} text={t("ui.138")} />
        ) : (
          <>
            <div className="transfer-actions">
              <button
                type="button"
                className="button"
                onClick={() =>
                  setChosen(new Set(tracks.map((track) => track.path)))
                }
              >
                {t("ui.139")}{" "}
              </button>
              <button
                type="button"
                className="button"
                onClick={() => setChosen(new Set())}
              >
                {t("ui.122")}{" "}
              </button>
              <span className="muted">
                {chosen.size} {t("ui.140")}{" "}
              </span>
              <button
                type="button"
                className="button button--primary"
                disabled={
                  !selected ||
                  chosen.size === 0 ||
                  transfer !== null ||
                  ready.length === 0
                }
                onClick={() => void push()}
              >
                {transfer ? t("ui.141") : t("ui.142")}
              </button>
            </div>

            {transfer && (
              <div className="transfer-progress">
                <Progress
                  value={
                    (transfer.completed / Math.max(transfer.total, 1)) * 100
                  }
                />
                <span className="muted">
                  {transfer.completed} / {transfer.total}
                </span>
              </div>
            )}

            <TrackPicker tracks={tracks} chosen={chosen} onChange={setChosen} />
          </>
        )}
      </Panel>
    </>
  );
}

/**
 * Apple offers no third-party route into the Music app's library, so getting
 * tracks there still goes through Finder or Apple Devices. Wi-Fi transfer
 * covers the Files app and every other player.
 */
function IOSPanel() {
  const t = useT();
  return (
    <Panel>
      <SectionHeading eyebrow={t("ui.143")} title={t("ui.144")} />
      <Notice tone="info">{t("ui.145")} </Notice>
      <ol className="ios-steps">
        <li>
          <strong>{t("ui.146")} </strong> {t("ui.147")}{" "}
        </li>
        <li>
          <strong>{t("ui.148")} </strong> {t("ui.149")}{" "}
        </li>
        <li>
          <strong>{t("ui.150")} </strong> {t("ui.151")}{" "}
        </li>
      </ol>
      <p className="muted setting__note">{t("ui.152")} </p>
    </Panel>
  );
}

function TrackPicker({
  tracks,
  chosen,
  onChange,
}: {
  tracks: LibraryTrack[];
  chosen: Set<string>;
  onChange: (next: Set<string>) => void;
}) {
  return (
    <div className="transfer-list">
      {tracks.map((track) => (
        <label key={track.id} className="transfer-item">
          <input
            type="checkbox"
            checked={chosen.has(track.path)}
            onChange={(event) => {
              const next = new Set(chosen);
              if (event.target.checked) next.add(track.path);
              else next.delete(track.path);
              onChange(next);
            }}
          />
          <span title={track.path}>{track.title}</span>
          <span className="muted">{track.format}</span>
        </label>
      ))}
    </div>
  );
}

function formatBytes(bytes: number): string {
  const mb = bytes / (1024 * 1024);
  return mb >= 1024 ? `${(mb / 1024).toFixed(1)} GB` : `${mb.toFixed(1)} MB`;
}

/**
 * Serves the chosen tracks on the local network behind a one-time link. The
 * phone scans the QR code and saves files from its browser, so this works on
 * iPhone and Android alike without cables or companion apps.
 */
function WifiPanel({
  tracks,
  toast,
}: {
  tracks: LibraryTrack[];
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const [chosen, setChosen] = useState<Set<string>>(new Set());
  const [share, setShare] = useState<api.WifiShare | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void api
      .currentWifiShare()
      .then(setShare)
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!share) return;
    const timer = window.setTimeout(
      () => setShare(null),
      Math.max(0, Date.parse(share.expiresAt) - Date.now()),
    );
    return () => window.clearTimeout(timer);
  }, [share]);

  async function start() {
    setBusy(true);
    try {
      setShare(await api.startWifiShare([...chosen]));
    } catch (reason) {
      toast(errorMessage(reason), "error");
    } finally {
      setBusy(false);
    }
  }

  async function stop() {
    setBusy(true);
    try {
      await api.stopWifiShare();
      setShare(null);
    } catch (reason) {
      toast(errorMessage(reason), "error");
    } finally {
      setBusy(false);
    }
  }

  if (share) {
    return (
      <Panel>
        <SectionHeading
          eyebrow={t("ui.153")}
          title={t("ui.154")}
          aside={
            <button
              type="button"
              className="button"
              disabled={busy}
              onClick={() => void stop()}
            >
              {t("ui.155")}{" "}
            </button>
          }
        />
        <div className="wifi-share">
          <img
            className="wifi-share__qr"
            alt={t("ui.156")}
            src={`data:image/svg+xml;charset=utf-8,${encodeURIComponent(share.qrSvg)}`}
          />
          <div>
            <p>
              {share.fileCount} {t("ui.157")} {formatBytes(share.totalBytes)}
            </p>
            <p className="muted">{t("ui.158")} </p>
            <code className="wifi-share__url">{share.url}</code>
            <ol className="ios-steps">
              <li>{t("ui.159")} </li>
              <li>{t("ui.160")} </li>
              <li>{t("ui.161")} </li>
            </ol>
            <p className="muted setting__note">
              {t("ui.162")} {new Date(share.expiresAt).toLocaleTimeString()}
              {t("ui.163")}{" "}
            </p>
          </div>
        </div>
      </Panel>
    );
  }

  return (
    <Panel>
      <SectionHeading eyebrow={t("ui.153")} title={t("ui.164")} />
      {tracks.length === 0 ? (
        <Empty title={t("ui.137")} text={t("ui.138")} />
      ) : (
        <>
          <div className="transfer-actions">
            <button
              type="button"
              className="button"
              onClick={() =>
                setChosen(new Set(tracks.map((track) => track.path)))
              }
            >
              {t("ui.139")}{" "}
            </button>
            <button
              type="button"
              className="button"
              onClick={() => setChosen(new Set())}
            >
              {t("ui.122")}{" "}
            </button>
            <span className="muted">
              {chosen.size} {t("ui.140")}{" "}
            </span>
            <button
              type="button"
              className="button button--primary"
              disabled={chosen.size === 0 || busy}
              onClick={() => void start()}
            >
              {busy ? t("ui.165") : t("ui.166")}
            </button>
          </div>
          <TrackPicker tracks={tracks} chosen={chosen} onChange={setChosen} />
        </>
      )}
    </Panel>
  );
}

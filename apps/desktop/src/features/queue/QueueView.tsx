import { useState } from "react";
import {
  Empty,
  IconButton,
  Panel,
  Progress,
  SectionHeading,
  StatusChip,
} from "../../components";
import * as api from "../../lib/api";
import { errorMessage } from "../../lib/formats";
import { useT } from "../../lib/i18n";
import type { DownloadJob } from "../../types";
import "./queue.css";

const RUNNING = ["preparing", "downloading", "converting"];
const RESTARTABLE = ["queued", "paused", "interrupted", "failed", "cancelled"];

export function QueueView({
  jobs,
  logs,
  toast,
}: {
  jobs: DownloadJob[];
  logs: Record<string, string[]>;
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const active = jobs.filter((job) => RUNNING.includes(job.status));
  const waiting = jobs.filter((job) =>
    ["queued", "paused", "interrupted"].includes(job.status),
  );
  const completed = jobs.filter((job) => job.status === "completed");

  return (
    <div className="view-stack">
      <div className="summary-strip">
        <Summary label={t("queue.active")} value={active.length} />
        <Summary label={t("queue.waiting")} value={waiting.length} />
        <Summary label={t("queue.completed")} value={completed.length} />
      </div>

      <Panel>
        <SectionHeading
          eyebrow={t("ui.176")}
          title={t("queue.title")}
          aside={<span className="muted">{t("ui.177")} </span>}
        />
        {jobs.length === 0 ? (
          <Empty title={t("ui.178")} text={t("ui.179")} />
        ) : (
          <div className="job-list">
            {jobs.map((job) => (
              <JobRow key={job.id} job={job} log={logs[job.id]} toast={toast} />
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}

function Summary({ label, value }: { label: string; value: number }) {
  return (
    <div className="summary">
      <small>{label}</small>
      <strong>{String(value).padStart(2, "0")}</strong>
    </div>
  );
}

function JobRow({
  job,
  log,
  toast,
}: {
  job: DownloadJob;
  log?: string[];
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const [showLog, setShowLog] = useState(false);
  const running = RUNNING.includes(job.status);
  const partial =
    job.status === "completed" && (job.unavailableTracks ?? 0) > 0;
  const restartable = RESTARTABLE.includes(job.status) || partial;
  // Live lines win; the snapshot on the job hydrates a cold load.
  const lines = log ?? job.recentLog ?? [];

  const act = (action: () => Promise<void>) => () => {
    action().catch((error) => toast(errorMessage(error), "error"));
  };

  return (
    <article className={`job job--${job.status}`}>
      <div className="job__main">
        <div className="job__top">
          <div className="job__title">
            <h3>{job.title}</h3>
            <p className="muted">
              {job.artist || api.providerLabel(job.request.provider)} ·{" "}
              {job.request.format.toUpperCase()}
            </p>
          </div>
          <StatusChip status={job.status} />
        </div>

        <Progress value={job.progress} />

        <div className="job__meta">
          <span>{job.stage}</span>
          <span>
            {job.speed}
            {job.speed && job.eta ? " · " : ""}
            {job.eta ? `${job.eta} left` : ""}
          </span>
          <strong>{Math.round(job.progress)}%</strong>
        </div>

        {job.error && <p className="job__error">{job.error}</p>}

        {lines.length > 0 && (
          <div className="job__log">
            <button
              type="button"
              className="job__log-toggle"
              aria-expanded={showLog}
              onClick={() => setShowLog((value) => !value)}
            >
              {showLog ? "▾" : "▸"} {t("ui.180")} {lines.length}{" "}
              {t("ui.181")}{" "}
            </button>
            {showLog && (
              <>
                <pre className="terminal">{lines.join("\n")}</pre>
                <button
                  type="button"
                  className="button"
                  onClick={act(() => api.copyText(lines.join("\n")))}
                >
                  {t("ui.182")}{" "}
                </button>
              </>
            )}
          </div>
        )}
      </div>

      <div className="job__actions">
        {running && (
          <IconButton
            glyph="❚❚"
            label={t("ui.183")}
            onClick={act(() => api.pauseJob(job.id))}
          />
        )}
        {restartable && (
          <IconButton
            glyph="▶"
            label={partial ? t("ui.184") : t("ui.185")}
            accent
            onClick={act(() => api.startJob(job.id))}
          />
        )}
        {job.status !== "completed" && (
          <IconButton
            glyph="✕"
            label={t("ui.186")}
            onClick={act(() => api.cancelJob(job.id))}
          />
        )}
      </div>
    </article>
  );
}

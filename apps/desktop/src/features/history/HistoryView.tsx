import { useT } from "../../lib/i18n";
import { useMemo, useState } from "react";
import { Empty, Panel, SectionHeading } from "../../components";
import * as api from "../../lib/api";
import type { HistoryEntry } from "../../types";

export function HistoryView({ history }: { history: HistoryEntry[] }) {
  const t = useT();
  const [query, setQuery] = useState("");

  const visible = useMemo(() => {
    const needle = query.trim().toLocaleLowerCase();
    if (!needle) return history;
    return history.filter((entry) => {
      const values = entry.job
        ? [
            entry.job.title,
            entry.job.artist,
            api.providerLabel(entry.job.request.provider),
            entry.job.request.format,
          ]
        : [entry.legacyText ?? "", t("ui.175")];
      return values.some((value) => value.toLocaleLowerCase().includes(needle));
    });
  }, [history, query, t]);

  return (
    <div className="view-stack">
      <Panel>
        <SectionHeading
          eyebrow={t("ui.167")}
          title={t("ui.168")}
          aside={
            <div className="field" style={{ minWidth: 220 }}>
              <span className="field__marker" aria-hidden="true">
                ⌕
              </span>
              <input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder={t("ui.169")}
                aria-label={t("ui.169")}
              />
            </div>
          }
        />

        {visible.length === 0 ? (
          <Empty
            title={history.length ? t("ui.170") : t("ui.171")}
            text={history.length ? t("ui.172") : t("ui.173")}
          />
        ) : (
          <div className="track-list">
            {visible.map((entry) => (
              <div key={entry.id} className="track">
                <span
                  className="track__play"
                  aria-hidden="true"
                  style={{ color: "var(--success)" }}
                >
                  ✓
                </span>
                <div className="track__meta">
                  <strong>
                    {entry.job?.title || entry.legacyText || t("ui.174")}
                  </strong>
                  <small>
                    {entry.job
                      ? `${api.providerLabel(entry.job.request.provider)} · ${entry.job.request.format.toUpperCase()}`
                      : t("ui.175")}
                  </small>
                </div>
                <span />
                <span />
                <time className="track__size">
                  {new Date(entry.createdAt).toLocaleDateString()}
                </time>
                <span />
              </div>
            ))}
          </div>
        )}
      </Panel>
    </div>
  );
}

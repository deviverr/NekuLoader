import { useT } from "../../../lib/i18n";
import { useEffect, useState } from "react";
import { Notice, Panel, SectionHeading } from "../../../components";
import * as api from "../../../lib/api";
import { errorMessage } from "../../../lib/formats";
import type { AppSettings, ToolInfo } from "../../../types";

type Phase = "idle" | "installing" | "success" | "error";

export function ComponentsTab({
  tools,
  onTools,
  activeDownloads,
  toast,
}: {
  draft: AppSettings;
  onEdit: (patch: Partial<AppSettings>) => void;
  tools: ToolInfo[];
  onTools: (value: ToolInfo[]) => void;
  activeDownloads: number;
  toast: (message: string, tone?: "success" | "error" | "info") => void;
}) {
  const t = useT();
  const [checking, setChecking] = useState(false);
  const [phase, setPhase] = useState<Phase>("idle");
  const [message, setMessage] = useState("");

  // Startup only checks which tools exist; run the real health checks
  // (versions included) once this tab is opened.
  useEffect(() => {
    let cancelled = false;
    void api
      .refreshTools()
      .then((fresh) => {
        if (!cancelled) onTools(fresh);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const missing = tools.filter((tool) => tool.required && !tool.installed);
  const installing = phase === "installing";

  async function refresh() {
    setChecking(true);
    try {
      onTools(await api.refreshTools());
      toast(t("ui.42"), "success");
    } catch (error) {
      toast(errorMessage(error), "error");
    } finally {
      setChecking(false);
    }
  }

  async function install() {
    setPhase("installing");
    setMessage(t("ui.43"));
    try {
      onTools(await api.installComponents());
      setPhase("success");
      setMessage(t("ui.44"));
      toast(t("ui.45"), "success");
    } catch (error) {
      const text = errorMessage(error);
      setPhase("error");
      setMessage(text);
      toast(text, "error");
    }
  }

  return (
    <div className="view-stack">
      <Panel>
        <SectionHeading
          eyebrow={t("ui.46")}
          title={t("ui.47")}
          aside={
            <button
              type="button"
              className="button"
              onClick={() => void refresh()}
              disabled={checking || installing}
            >
              {checking ? t("ui.48") : t("ui.49")}
            </button>
          }
        />

        <div className="tool-grid">
          {tools.map((tool) => (
            <div
              key={tool.name}
              className={tool.installed ? "tool tool--ready" : "tool"}
            >
              <span className="tool__state" aria-hidden="true">
                {tool.installed ? "✓" : "✕"}
              </span>
              <div>
                <strong>{tool.name}</strong>
                <small>{tool.version || tool.status}</small>
                <small className="tool__path">
                  {tool.path || (tool.required ? t("Required") : t("Optional"))}
                </small>
              </div>
            </div>
          ))}
        </div>

        <div className="component-action">
          <div>
            <strong>
              {missing.length
                ? `${t("Required components missing")}: ${missing.length}`
                : t("ui.50")}
            </strong>
            <p className="muted">{t("ui.51")} </p>
          </div>
          <button
            type="button"
            className="button button--primary"
            disabled={
              installing || checking || activeDownloads > 0 || !api.isTauri()
            }
            title={
              activeDownloads > 0
                ? t("ui.52")
                : !api.isTauri()
                  ? t("ui.53")
                  : undefined
            }
            onClick={() => void install()}
          >
            {installing ? t("ui.54") : missing.length ? t("ui.55") : t("ui.56")}
          </button>
        </div>

        {activeDownloads > 0 && (
          <Notice tone="warning">
            {t("ui.57")} {activeDownloads} {t("ui.58")} {t("ui.59")}{" "}
          </Notice>
        )}

        {phase !== "idle" && (
          <Notice
            tone={
              phase === "error"
                ? "error"
                : phase === "success"
                  ? "success"
                  : "info"
            }
          >
            {message}
          </Notice>
        )}
      </Panel>
    </div>
  );
}

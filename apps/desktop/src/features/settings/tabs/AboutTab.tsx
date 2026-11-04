import { useT } from "../../../lib/i18n";
import { AsciiArt } from "../../../ascii/AsciiArt";
import { wordmarkFrames } from "../../../ascii/frames/wordmark";
import { Panel, SectionHeading } from "../../../components";
import * as api from "../../../lib/api";

export function AboutTab({ version }: { version: string }) {
  const t = useT();
  return (
    <div className="view-stack">
      <Panel>
        <AsciiArt
          frameSet={wordmarkFrames}
          className="ascii--hero"
          label={t("ui.3")}
        />
        <p className="muted about__version">
          {t("ui.4")} {version}
        </p>
        <p className="muted">{t("ui.5")} </p>
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.6")} title={t("ui.7")} />
        <p className="muted setting__note">{t("ui.8")} </p>
        <a
          className="button"
          href={api.releasesUrl}
          target="_blank"
          rel="noreferrer"
        >
          {t("ui.9")}{" "}
        </a>
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.10")} title={t("ui.11")} />
        <ul className="credits">
          <li>{t("ui.12")} </li>
          <li>{t("ui.13")} </li>
          <li>{t("ui.14")} </li>
          <li>{t("ui.15")} </li>
          <li>{t("ui.16")} </li>
        </ul>
      </Panel>
    </div>
  );
}

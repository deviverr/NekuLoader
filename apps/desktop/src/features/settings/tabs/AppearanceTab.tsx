import { useT } from "../../../lib/i18n";
import { Panel, SectionHeading, Toggle } from "../../../components";
import { playSound } from "../../../lib/sound";
import type {
  AppSettings,
  CustomThemeColors,
  LogTextColor,
  ThemeMode,
} from "../../../types";
import { PRESET_THEMES } from "../../../theme/presets";

const LOG_COLORS: LogTextColor[] = [
  "green",
  "blue",
  "cyan",
  "yellow",
  "orange",
  "pink",
  "red",
  "white",
];

export function AppearanceTab({
  draft,
  onEdit,
  onApply,
  customThemes,
}: {
  draft: AppSettings;
  onEdit: (patch: Partial<AppSettings>) => void;
  onApply: (patch: Partial<AppSettings>) => Promise<boolean>;
  customThemes: CustomThemeColors[];
}) {
  const t = useT();
  return (
    <div className="settings-grid">
      <Panel>
        <SectionHeading eyebrow={t("ui.17")} title={t("ui.18")} />
        <p className="muted setting__note">{t("ui.19")} </p>

        <div className="skin-picker">
          {(
            [
              { id: "mono", label: "Mono", hint: "Black and white, glow only" },
              {
                id: "cosmic",
                label: "Cosmic",
                hint: "Violet nebula, full glow",
              },
              {
                id: "legacy",
                label: "Legacy",
                hint: "Muted, compact art, slower",
              },
            ] as Array<{ id: ThemeMode; label: string; hint: string }>
          ).map((option) => (
            <button
              key={option.id}
              type="button"
              className={
                draft.themeMode === option.id ? "skin skin--active" : "skin"
              }
              aria-pressed={draft.themeMode === option.id}
              onClick={() => void onApply({ themeMode: option.id })}
            >
              <strong>{t(option.label)}</strong>
              <small>{t(option.hint)}</small>
            </button>
          ))}
        </div>

        <p className="eyebrow skin-picker__label">{t("ui.20")} </p>
        <div className="skin-picker skin-picker--presets">
          {PRESET_THEMES.map((theme) => {
            const id = `preset:${theme.name}` as ThemeMode;
            return (
              <button
                key={theme.name}
                type="button"
                className={
                  draft.themeMode === id ? "skin skin--active" : "skin"
                }
                aria-pressed={draft.themeMode === id}
                onClick={() => void onApply({ themeMode: id })}
              >
                <span
                  className="skin__swatch"
                  aria-hidden="true"
                  style={{
                    background: `linear-gradient(135deg, ${theme.bgStart} 0 50%, ${theme.bgEnd} 50%)`,
                    color: theme.titleColor,
                    borderColor: theme.buttonColor,
                  }}
                >
                  ★
                </span>
                <strong>{t(theme.name)}</strong>
              </button>
            );
          })}
        </div>

        {customThemes.length > 0 && (
          <label className="setting">
            <span>
              {t("ui.21")} <small>{t("ui.22")} </small>
            </span>
            <select
              value={
                draft.themeMode.startsWith("custom:") ? draft.themeMode : ""
              }
              onChange={(event) => {
                if (event.target.value)
                  void onApply({ themeMode: event.target.value as ThemeMode });
              }}
            >
              <option value="">{t("ui.23")} </option>
              {customThemes.map((theme) => (
                <option key={theme.name} value={`custom:${theme.name}`}>
                  {theme.name}
                </option>
              ))}
            </select>
          </label>
        )}
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.24")} title={t("ui.25")} />
        <div className="setting-stack">
          <Toggle
            label={t("ui.26")}
            value={draft.animateBackground}
            onChange={(value) => void onApply({ animateBackground: value })}
          />
          <Toggle
            label={t("ui.27")}
            value={draft.reducedMotion}
            onChange={(value) => void onApply({ reducedMotion: value })}
            hint={t("ui.28")}
          />
          <Toggle
            label={t("ui.29")}
            value={draft.catMode}
            onChange={(value) => void onApply({ catMode: value })}
          />
        </div>

        <Slider
          label={t("ui.30")}
          value={draft.snowSpeed}
          min={0.1}
          max={3}
          onChange={(value) => onEdit({ snowSpeed: value })}
        />
        <Slider
          label={t("ui.31")}
          value={draft.snowBoldness}
          min={0.5}
          max={3}
          onChange={(value) => onEdit({ snowBoldness: value })}
        />
        <Slider
          label={t("ui.32")}
          value={draft.snowTransparency}
          min={0}
          max={1}
          onChange={(value) => onEdit({ snowTransparency: value })}
        />
        <DirectionDial
          value={draft.starAngle}
          onChange={(value) => onEdit({ starAngle: value })}
        />
        <Slider
          label={t("ui.33")}
          value={draft.frameTransparency}
          min={0.3}
          max={1}
          onChange={(value) => onEdit({ frameTransparency: value })}
        />
      </Panel>

      <Panel>
        <SectionHeading eyebrow={t("ui.34")} title={t("ui.35")} />
        <label className="setting">
          <span>
            {t("ui.36")} <small>{t("ui.37")} </small>
          </span>
          <select
            value={draft.logTextColor}
            onChange={(event) =>
              void onApply({ logTextColor: event.target.value as LogTextColor })
            }
          >
            {LOG_COLORS.map((color) => (
              <option key={color} value={color}>
                {t(color)}
              </option>
            ))}
          </select>
        </label>
        <p className="terminal log-preview">{t("ui.38")} </p>

        <div className="setting-stack">
          <Toggle
            label={t("ui.39")}
            value={draft.soundEnabled}
            onChange={(value) => {
              onEdit({ soundEnabled: value });
              if (value) playSound("detected", true, draft.soundVolume);
            }}
          />
        </div>
        <Slider
          label={t("ui.40")}
          value={draft.soundVolume}
          min={0}
          max={1}
          onChange={(value) => onEdit({ soundVolume: value })}
        />
      </Panel>
    </div>
  );
}

function Slider({
  label,
  value,
  min,
  max,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
}) {
  return (
    <label className="slider">
      <span>{label}</span>
      <input
        type="range"
        min={min}
        max={max}
        step={0.05}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
      />
      <span className="slider__value">{value.toFixed(2)}</span>
    </label>
  );
}

/**
 * Direction of travel for the starfield, as a dial rather than a slider — the
 * value wraps at 360°, which a linear control cannot express without a seam.
 * Dragging anywhere on the disc points the marker at the cursor.
 */
function DirectionDial({
  value,
  onChange,
}: {
  value: number;
  onChange: (value: number) => void;
}) {
  const t = useT();
  function aim(event: React.PointerEvent<HTMLDivElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const dx = event.clientX - (box.left + box.width / 2);
    const dy = event.clientY - (box.top + box.height / 2);
    // atan2 measures from +x anticlockwise; the dial reads clockwise from north.
    const degrees = (Math.atan2(dx, -dy) * 180) / Math.PI;
    onChange(Math.round((degrees + 360) % 360));
  }

  return (
    <div className="dial-row">
      <span>{t("ui.41")} </span>
      <div
        className="dial"
        role="slider"
        tabIndex={0}
        aria-label={t("ui.41")}
        aria-valuenow={Math.round(value)}
        aria-valuemin={0}
        aria-valuemax={359}
        aria-valuetext={`${Math.round(value)} degrees`}
        onPointerDown={(event) => {
          event.currentTarget.setPointerCapture(event.pointerId);
          aim(event);
        }}
        onPointerMove={(event) => {
          if (event.buttons === 1) aim(event);
        }}
        onKeyDown={(event) => {
          if (event.key === "ArrowLeft") onChange((value + 355) % 360);
          if (event.key === "ArrowRight") onChange((value + 5) % 360);
        }}
      >
        <span
          className="dial__marker"
          style={{ transform: `rotate(${value}deg)` }}
        />
      </div>
      <span className="slider__value">{Math.round(value)}°</span>
    </div>
  );
}

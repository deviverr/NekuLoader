import { useMemo } from "react";
import type { AppSettings } from "../types";

/**
 * The drifting starfield and its planets.
 *
 * Legacy 3.1 had this as falling "snow" — straight down, always. Here the drift
 * direction is a user setting, so the field can read as travelling through space
 * rather than as weather.
 *
 * Positions are derived from the index rather than `Math.random`, so a
 * re-render never reshuffles the sky.
 */
const STAR_COUNT = 64;

/** Distinct sizes and orbits, so the planets do not read as a repeated stamp. */
const PLANETS = [
  { size: 190, top: "12%", left: "8%", ring: false, drift: 90 },
  { size: 96, top: "62%", left: "78%", ring: true, drift: 130 },
  { size: 54, top: "26%", left: "88%", ring: false, drift: 70 },
  { size: 130, top: "78%", left: "22%", ring: false, drift: 160 },
];

export function Starfield({ settings }: { settings: AppSettings }) {
  const angle = settings.starAngle ?? 180;

  const stars = useMemo(
    () =>
      Array.from({ length: STAR_COUNT }, (_, index) => {
        const size = (1 + (index % 3)) * settings.snowBoldness;
        return {
          // Two coprime strides keep the scatter from banding into columns.
          left: `${(index * 37) % 100}%`,
          top: `${(index * 61) % 100}%`,
          delay: `${-((index * 0.83) % 11)}s`,
          duration: `${(9 + (index % 7)) / Math.max(settings.snowSpeed, 0.05)}s`,
          size: `${Math.max(1, size)}px`,
        };
      }),
    [settings.snowBoldness, settings.snowSpeed],
  );

  return (
    <>
      <div className="planets" aria-hidden="true">
        {PLANETS.map((planet, index) => (
          <div
            key={index}
            className={planet.ring ? "planet planet--ringed" : "planet"}
            style={{
              width: planet.size,
              height: planet.size,
              top: planet.top,
              left: planet.left,
              animationDuration: `${planet.drift / Math.max(settings.snowSpeed, 0.05)}s`,
            }}
          />
        ))}
      </div>

      {settings.animateBackground && !settings.reducedMotion && (
        <div
          className="starfield"
          aria-hidden="true"
          style={{
            opacity: settings.snowTransparency,
            // Travel is a single vector every star shares, rotated by the knob.
            ["--star-dx" as string]: `${Math.sin((angle * Math.PI) / 180) * 120}vmax`,
            ["--star-dy" as string]: `${-Math.cos((angle * Math.PI) / 180) * 120}vmax`,
          }}
        >
          {stars.map((star, index) => (
            <i
              key={index}
              style={{
                left: star.left,
                top: star.top,
                width: star.size,
                height: star.size,
                animationDelay: star.delay,
                animationDuration: star.duration,
              }}
            />
          ))}
        </div>
      )}
    </>
  );
}

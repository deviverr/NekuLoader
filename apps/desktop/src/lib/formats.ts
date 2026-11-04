/**
 * Output formats, matching legacy 3.1's Format combo. `quality` is the value
 * the Rust side validates against, so these pairs must stay in step with
 * `validate_request` in src-tauri/src/lib.rs.
 */
export interface FormatOption {
  value: string;
  label: string;
  detail: string;
  quality: string;
}

export const formatOptions: FormatOption[] = [
  { value: "mp3", label: "MP3", detail: "320 kbps", quality: "320" },
  { value: "flac", label: "FLAC", detail: "Lossless", quality: "0" },
  { value: "opus", label: "OPUS", detail: "256 kbps", quality: "256" },
  { value: "m4a", label: "M4A", detail: "256 kbps", quality: "256" },
  { value: "wav", label: "WAV", detail: "Uncompressed", quality: "0" },
  { value: "alac", label: "ALAC", detail: "Apple lossless", quality: "0" },
  { value: "vorbis", label: "VORBIS", detail: "320 kbps", quality: "320" },
];

export function formatFor(value: string): FormatOption {
  return formatOptions.find((option) => option.value === value) ?? formatOptions[0];
}

export function formatBytes(bytes: number): string {
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

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || !Number.isFinite(seconds) || seconds < 0) return "--:--";
  const total = Math.floor(seconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const secs = total % 60;
  const pad = (value: number) => String(value).padStart(2, "0");
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(secs)}` : `${minutes}:${pad(secs)}`;
}

export function errorMessage(error: unknown): string {
  if (error instanceof Error) return error.message;
  if (typeof error === "string") return error;
  return "Something went wrong.";
}

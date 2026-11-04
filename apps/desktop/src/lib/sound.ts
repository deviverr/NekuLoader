/**
 * Interface sounds, played as little instrument notes.
 *
 * Every sound is synthesised with Web Audio — no sample files to ship or
 * license. Two voices: a marimba (sine fundamental plus a fast-decaying 4th
 * harmonic, the "wooden" knock) for everyday feedback, and a bell (inharmonic
 * partials with a long tail) for finished downloads. Notes come from the C
 * major pentatonic scale, so any sequence of them sounds consonant.
 */
export type SoundEvent =
  | "detected"
  | "queued"
  | "started"
  | "complete"
  | "failed"
  | "saved"
  | "select"
  | "format";

type Voice = "marimba" | "bell" | "pluck";
type Note = [midi: number, delay: number, voice: Voice];

const melodies: Record<Exclude<SoundEvent, "format">, Note[]> = {
  // A rising two-note "found it".
  detected: [[72, 0, "marimba"], [79, 0.09, "marimba"]],
  queued: [[67, 0, "pluck"], [72, 0.07, "pluck"]],
  // Arpeggio up: the download is on its way.
  started: [[60, 0, "marimba"], [64, 0.08, "marimba"], [67, 0.16, "marimba"]],
  // Bell chord, spread out like a music box.
  complete: [[72, 0, "bell"], [76, 0.11, "bell"], [79, 0.22, "bell"], [84, 0.36, "bell"]],
  // A soft falling minor third — clearly "no", never harsh.
  failed: [[64, 0, "marimba"], [60, 0.14, "marimba"], [57, 0.28, "marimba"]],
  saved: [[76, 0, "pluck"], [84, 0.07, "pluck"]],
  select: [[79, 0, "pluck"]],
};

/** One pentatonic note per output format, low to high by "weight". */
const FORMAT_NOTES: Record<string, number> = {
  mp3: 72,
  m4a: 74,
  opus: 76,
  vorbis: 79,
  flac: 81,
  alac: 84,
  wav: 86,
};

let context: AudioContext | undefined;
let lastPlayed = 0;

function frequency(midi: number): number {
  return 440 * 2 ** ((midi - 69) / 12);
}

function partial(
  ctx: AudioContext,
  out: AudioNode,
  hz: number,
  start: number,
  peak: number,
  decay: number,
  type: OscillatorType = "sine",
) {
  const osc = ctx.createOscillator();
  const gain = ctx.createGain();
  osc.type = type;
  osc.frequency.value = hz;
  gain.gain.setValueAtTime(0.0001, start);
  gain.gain.exponentialRampToValueAtTime(Math.max(peak, 0.0002), start + 0.004);
  gain.gain.exponentialRampToValueAtTime(0.0001, start + decay);
  osc.connect(gain).connect(out);
  osc.start(start);
  osc.stop(start + decay + 0.05);
}

function playNote(ctx: AudioContext, out: AudioNode, midi: number, start: number, voice: Voice, level: number) {
  const hz = frequency(midi);
  if (voice === "marimba") {
    partial(ctx, out, hz, start, level, 0.55);
    partial(ctx, out, hz * 4, start, level * 0.35, 0.09);
    partial(ctx, out, hz * 10, start, level * 0.08, 0.03);
  } else if (voice === "bell") {
    partial(ctx, out, hz, start, level * 0.8, 1.6);
    partial(ctx, out, hz * 2.76, start, level * 0.3, 0.9);
    partial(ctx, out, hz * 5.4, start, level * 0.12, 0.45);
    partial(ctx, out, hz * 8.93, start, level * 0.05, 0.25);
  } else {
    // Pluck: a bright, short triangle with a sine body.
    partial(ctx, out, hz, start, level * 0.7, 0.28, "triangle");
    partial(ctx, out, hz * 2, start, level * 0.25, 0.12);
  }
}

export function playSound(event: SoundEvent, enabled: boolean, volume: number, format?: string): void {
  if (!enabled || volume <= 0 || Date.now() - lastPlayed < 60) return;
  try {
    context ||= new AudioContext();
    const ctx = context;
    if (ctx.state === "suspended") void ctx.resume();
    const now = ctx.currentTime + 0.01;
    // A gentle master filter keeps the upper partials from getting shrill.
    const filter = ctx.createBiquadFilter();
    filter.type = "lowpass";
    filter.frequency.value = 6500;
    const master = ctx.createGain();
    master.gain.value = Math.min(1, volume) * 0.32;
    filter.connect(master).connect(ctx.destination);

    const notes: Note[] =
      event === "format" ? [[FORMAT_NOTES[format ?? "mp3"] ?? 72, 0, "marimba"]] : melodies[event];
    for (const [midi, delay, voice] of notes) playNote(ctx, filter, midi, now + delay, voice, 0.5);
    lastPlayed = Date.now();
  } catch {
    // Audio is an enhancement; visual feedback remains authoritative.
  }
}

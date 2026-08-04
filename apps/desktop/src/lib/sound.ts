export type SoundEvent = "detected" | "queued" | "started" | "complete" | "failed" | "saved";

const patterns: Record<SoundEvent, Array<[number, number, number]>> = {
  detected: [[660, 0, 0.07], [880, 0.08, 0.08]],
  queued: [[440, 0, 0.06]],
  started: [[520, 0, 0.05], [620, 0.06, 0.05]],
  complete: [[523, 0, 0.08], [659, 0.09, 0.08], [784, 0.18, 0.14]],
  failed: [[240, 0, 0.1], [180, 0.11, 0.14]],
  saved: [[700, 0, 0.05], [930, 0.06, 0.08]],
};

let context: AudioContext | undefined;
let lastPlayed = 0;

export function playSound(event: SoundEvent, enabled: boolean, volume: number): void {
  if (!enabled || volume <= 0 || Date.now() - lastPlayed < 90) return;
  try {
    context ||= new AudioContext();
    const now = context.currentTime;
    patterns[event].forEach(([frequency, delay, duration]) => {
      const oscillator = context!.createOscillator();
      const gain = context!.createGain();
      oscillator.type = "square";
      oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0.0001, now + delay);
      gain.gain.exponentialRampToValueAtTime(Math.max(0.0001, volume * 0.085), now + delay + 0.008);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + delay + duration);
      oscillator.connect(gain).connect(context!.destination);
      oscillator.start(now + delay);
      oscillator.stop(now + delay + duration + 0.02);
    });
    lastPlayed = Date.now();
  } catch {
    // Audio is an enhancement; visual feedback remains authoritative.
  }
}

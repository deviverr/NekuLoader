import { act, cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PlayerProvider, usePlayer } from "./PlayerProvider";
import { PlayerScreen } from "./PlayerScreen";
import { LibraryView } from "../library/LibraryView";
import { bootstrapFixture, trackFixture } from "../../test/fixtures";
import * as api from "../../lib/api";

vi.mock("../../lib/api", () => ({
  playableUrl: vi.fn(async () => "http://localhost/audio"),
  trackArtworkUrl: vi.fn(async () => null),
  onArtworkRepair: vi.fn(async () => () => {}),
  savePlaybackState: vi.fn(async () => undefined),
}));
vi.mock("./useArtwork", () => ({ useArtwork: () => undefined, invalidateArtwork: () => {} }));
const track = trackFixture();
function Controls() {
  const player = usePlayer();
  return <>
    <button onClick={() => player.playQueue([track], 0)}>Start</button>
    <button onClick={player.clear}>Clear</button>
    <button onClick={() => player.removeAt(0)}>Remove</button>
    <button onClick={() => player.seek(42)}>Seek</button>
    <span data-testid="position">{player.positionSeconds}</span>
  </>;
}
function mount() {
  return render(<PlayerProvider initial={bootstrapFixture().playback} library={[track]}>
    <Controls />
    <LibraryView tracks={[track]} scanning={false} error="" onRescan={() => {}} onOpenPlayer={() => {}} />
  </PlayerProvider>);
}
async function start() {
  await act(async () => { fireEvent.click(screen.getByText("Start")); });
}
describe("playback user actions", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(function(this: HTMLMediaElement) {
      Object.defineProperty(this, "paused", { configurable: true, value: true });
      this.dispatchEvent(new Event("pause"));
    });
    vi.spyOn(HTMLMediaElement.prototype, "play").mockImplementation(async function(this: HTMLMediaElement) {
      Object.defineProperty(this, "paused", { configurable: true, value: false });
      this.dispatchEvent(new Event("play"));
    });
  });
  afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.useRealTimers(); vi.clearAllMocks(); });
  it.each(["Clear", "Remove"])("stops audio when the last track is removed with %s", async (action) => {
    const { container } = mount();
    await start();
    const audio = container.querySelector("audio")!;
    expect(audio.paused).toBe(false);
    fireEvent.click(screen.getByText(action));
    expect(audio.paused).toBe(true);
    expect(audio.hasAttribute("src")).toBe(false);
    expect(screen.getByTestId("position")).toHaveTextContent("0");
  });
  it("saves the current position on seek and pause", async () => {
    const { container } = mount();
    await start();
    fireEvent.click(screen.getByText("Seek"));
    await act(async () => { vi.advanceTimersByTime(801); });
    expect(api.savePlaybackState).toHaveBeenLastCalledWith(expect.objectContaining({ positionSeconds: 42 }));
    const audio = container.querySelector("audio")!;
    audio.currentTime = 73;
    fireEvent.pause(audio);
    await act(async () => { vi.advanceTimersByTime(801); });
    expect(api.savePlaybackState).toHaveBeenLastCalledWith(expect.objectContaining({ positionSeconds: 73 }));
  });
  it("pauses and resumes the active library row without restarting it", async () => {
    const { container } = mount();
    await start();
    const audio = container.querySelector("audio")!;
    audio.currentTime = 30;
    fireEvent.click(screen.getByRole("button", { name: `Pause ${track.title}` }));
    expect(audio.paused).toBe(true);
    expect(audio.currentTime).toBe(30);
    fireEvent.click(screen.getByRole("button", { name: `Play ${track.title}` }));
    expect(audio.paused).toBe(false);
    expect(audio.currentTime).toBe(30);
  });
  it("pauses and resumes the current album track without replacing its queue", async () => {
    const { container } = render(<PlayerProvider initial={bootstrapFixture().playback} library={[track]}>
      <Controls /><PlayerScreen library={[track]} />
    </PlayerProvider>);
    await start();
    const audio = container.querySelector("audio")!;
    audio.currentTime = 30;
    fireEvent.click(screen.getByRole("button", { name: `Pause ${track.title}` }));
    expect(audio.paused).toBe(true);
    expect(audio.currentTime).toBe(30);
    fireEvent.click(screen.getByRole("button", { name: `Play ${track.title}` }));
    expect(audio.paused).toBe(false);
    expect(audio.currentTime).toBe(30);
  });

});

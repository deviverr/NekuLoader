import { cleanup, render } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { PixelSky } from "./PixelSky";
import { settingsFixture } from "../test/fixtures";

afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

it.each([false, true])("planet motion respects reduced motion = %s", (reducedMotion) => {
  let nextFrame: FrameRequestCallback = () => {};
  vi.stubGlobal("requestAnimationFrame", (callback: FrameRequestCallback) => { nextFrame = callback; return 1; });
  vi.stubGlobal("cancelAnimationFrame", () => {});
  const drawImage = vi.fn();
  const context = { clearRect() {}, fillRect() {}, drawImage, globalAlpha: 1, fillStyle: "" };
  vi.spyOn(HTMLCanvasElement.prototype, "getContext").mockReturnValue(context as unknown as CanvasRenderingContext2D);
  render(<PixelSky settings={settingsFixture({ animateBackground: true, reducedMotion })} />);
  const initial = drawImage.mock.calls.slice(0, 4).map((call) => call.slice(1));
  const start = performance.now();
  for (let frame = 1; frame <= 20; frame++) nextFrame(start + frame * 100);
  const final = drawImage.mock.calls.slice(-4).map((call) => call.slice(1));
  expect(initial).toHaveLength(4);
  if (reducedMotion) expect(final).toEqual(initial);
  else expect(final).not.toEqual(initial);
});

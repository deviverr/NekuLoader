import "@testing-library/jest-dom/vitest";

// jsdom has no canvas; the pixel sky and cat only need a no-op 2D context.
HTMLCanvasElement.prototype.getContext = (() => null) as unknown as HTMLCanvasElement["getContext"];

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { CatBar } from "./CatBar";

beforeEach(() => {
  const saved = new Map<string, string>();
  vi.stubGlobal("localStorage", {
    getItem: (key: string) => saved.get(key) ?? null,
    setItem: (key: string, value: string) => saved.set(key, value),
  });
  vi.stubGlobal("PointerEvent", class extends MouseEvent {
    pointerId: number;
    constructor(type: string, options: PointerEventInit = {}) {
      super(type, options);
      this.pointerId = options.pointerId ?? 1;
    }
  });
});
afterEach(() => { cleanup(); vi.unstubAllGlobals(); vi.restoreAllMocks(); });

it("responds to petting and can be moved by keyboard without navigating pages", () => {
  render(<CatBar running={false} />);
  const cat = screen.getByRole("button", { name: "Pet Neku" });
  const navigate = vi.fn();
  window.addEventListener("keydown", navigate);
  fireEvent.keyDown(cat, { key: "End" });
  expect(window.localStorage.getItem("nekuloader.cat-position")).toBe("100");
  expect(navigate).not.toHaveBeenCalled();
  window.removeEventListener("keydown", navigate);
  fireEvent.click(cat);
  expect(screen.getByRole("status")).toHaveTextContent("purring");
});

it("clamps dragging to the player edge and does not mistake dragging for petting", () => {
  const { container, unmount } = render(<CatBar running={false} />);
  vi.spyOn(container.querySelector(".catbar")!, "getBoundingClientRect").mockReturnValue({ width: 1000 } as DOMRect);
  const cat = screen.getByRole("button", { name: "Pet Neku" });
  fireEvent.pointerDown(cat, { button: 0, pointerId: 1, clientX: 100 });
  fireEvent.pointerMove(cat, { pointerId: 1, clientX: 2000 });
  fireEvent.pointerUp(cat, { pointerId: 1, clientX: 2000 });
  fireEvent.click(cat, { detail: 1 });
  expect(window.localStorage.getItem("nekuloader.cat-position")).toBe("100");
  expect(screen.getByRole("status")).toBeEmptyDOMElement();
  unmount();
  render(<CatBar running={false} />);
  expect(screen.getByRole("button", { name: "Pet Neku" }).style.left).toContain("100%");
});

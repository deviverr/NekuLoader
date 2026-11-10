import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { OverlayWindow } from "./OverlayWindow";

it("contains keyboard focus, handles Escape, and restores the opener", async () => {
  const user = userEvent.setup();
  const opener = document.createElement("button");
  document.body.append(opener);
  opener.focus();
  const close = vi.fn();
  const view = render(<OverlayWindow title="Settings" onClose={close}><button>Save</button></OverlayWindow>);
  expect(screen.getByRole("button", { name: "Close" })).toHaveFocus();
  await user.tab({ shift: true });
  expect(screen.getByRole("button", { name: "Save" })).toHaveFocus();
  await user.tab();
  expect(screen.getByRole("button", { name: "Close" })).toHaveFocus();
  await user.keyboard("{Escape}");
  expect(close).toHaveBeenCalledTimes(1);
  view.unmount();
  expect(opener).toHaveFocus();
  opener.remove();
});

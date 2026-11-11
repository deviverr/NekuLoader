import { QueueView } from "./features/queue/QueueView";
import { HistoryView } from "./features/history/HistoryView";
import type { DownloadJob } from "./types";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { DownloadView, extractLink } from "./features/download/DownloadView";
import { dictionaryKeys, LANGUAGES, LanguageProvider } from "./lib/i18n";
import App from "./App";
import * as api from "./lib/api";
import {
  bootstrapFixture,
  settingsFixture,
  trackFixture,
} from "./test/fixtures";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
  document.documentElement.removeAttribute("data-theme");
});

describe("shell", () => {
  it("opens on the download slide with just the link field", async () => {
    render(<App />);
    await waitFor(() =>
      expect(screen.getByLabelText("Music link")).toBeInTheDocument(),
    );
    // The rework drops legacy 3.1's manual Source/Type combos entirely.
    expect(screen.queryByLabelText(/^source$/i)).not.toBeInTheDocument();
    expect(screen.queryByText("Direct download")).not.toBeInTheDocument();
    expect(screen.queryByText("Metadata matching")).not.toBeInTheDocument();
  });

  it("keeps the bottom player mounted while switching to the Player page", async () => {
    const track = trackFixture();
    vi.spyOn(api, "bootstrap").mockResolvedValue(bootstrapFixture({
      playback: { ...bootstrapFixture().playback, queueTrackIds: [track.id], currentIndex: 0 },
    }));
    vi.spyOn(api, "scanLibrary").mockResolvedValue([track]);
    vi.spyOn(HTMLMediaElement.prototype, "load").mockImplementation(() => {});
    vi.spyOn(HTMLMediaElement.prototype, "pause").mockImplementation(() => {});
    const user = userEvent.setup();
    const { container } = render(<App />);
    await screen.findByLabelText("Music link");
    await waitFor(() => expect(container.querySelector(".mini-player")).not.toBeNull());
    const bar = container.querySelector(".mini-player");
    await user.click(screen.getByRole("tab", { name: /^Player$/i }));
    expect(container.querySelector(".mini-player")).toBe(bar);
    expect(bar?.closest(".player-dock")).not.toBeNull();
  });

  it("shows the three slides and a classic menu bar", async () => {
    render(<App />);
    await screen.findByLabelText("Music link");

    for (const label of ["Download", "Player", "Library"]) {
      expect(
        screen.getByRole("tab", { name: new RegExp(label) }),
      ).toBeInTheDocument();
    }
    for (const label of ["File", "View", "Devices", "Settings"]) {
      expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
    }
  });

  it("moves between slides with the arrow keys", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByLabelText("Music link");

    expect(screen.getByRole("tab", { name: /Download/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: /Player/ })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });

  it("opens the queue as a window from the View menu", async () => {
    const user = userEvent.setup();
    render(<App />);
    await screen.findByLabelText("Music link");

    await user.click(screen.getByRole("button", { name: "View" }));
    await user.click(screen.getByRole("menuitem", { name: /Queue/ }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByText("The queue is quiet")).toBeInTheDocument();
  });
});

describe("components workflow", () => {
  it("opens Components on first run when required tools are missing, without installing", async () => {
    vi.spyOn(api, "isTauri").mockReturnValue(true);
    vi.spyOn(api, "onJobUpdate").mockResolvedValue(() => undefined);
    vi.spyOn(api, "onJobLog").mockResolvedValue(() => undefined);
    vi.spyOn(api, "bootstrap").mockResolvedValue(
      bootstrapFixture({
        tools: [
          {
            name: "yt-dlp",
            required: true,
            installed: false,
            status: "Missing",
          },
        ],
      }),
    );
    const installer = vi.spyOn(api, "installComponents");

    render(<App />);

    // Missing components open the Settings window unprompted.
    expect(await screen.findByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Components" })).toBeInTheDocument();
    expect(installer).not.toHaveBeenCalled();
  });

  it("keeps the installer unavailable in browser preview mode", async () => {
    await expect(api.installComponents()).rejects.toThrow(
      /installed desktop app/i,
    );
  });
});

describe("library", () => {
  it("scans, shows tag-derived duration, and filters", async () => {
    vi.spyOn(api, "scanLibrary").mockResolvedValue([
      trackFixture(),
      trackFixture({
        id: "/Music/Night Drive.opus",
        title: "Night Drive",
        artist: "",
        album: "Music",
        path: "/Music/Night Drive.opus",
        format: "OPUS",
        durationSeconds: 187,
      }),
    ]);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByLabelText("Music link");
    await user.click(screen.getByRole("tab", { name: /Library/ }));
    expect(await screen.findByText("Mycelium Eyes")).toBeInTheDocument();
    // 214s from the embedded tag, which legacy never read.
    expect(screen.getByText("3:34")).toBeInTheDocument();

    await user.type(
      screen.getByRole("textbox", { name: "Search library" }),
      "opus",
    );
    expect(screen.getByText("Night Drive")).toBeInTheDocument();
    expect(screen.queryByText("Mycelium Eyes")).not.toBeInTheDocument();
  });
});

describe("history", () => {
  it("filters imported legacy records", async () => {
    vi.spyOn(api, "bootstrap").mockResolvedValue(
      bootstrapFixture({
        history: [
          {
            id: 1,
            legacyText: "Asterism download",
            createdAt: "2026-08-04T00:00:00Z",
          },
          {
            id: 2,
            legacyText: "Night Drive",
            createdAt: "2026-08-03T00:00:00Z",
          },
        ],
      }),
    );
    const user = userEvent.setup();
    render(<App />);

    await screen.findByLabelText("Music link");
    await user.click(screen.getByRole("button", { name: "View" }));
    await user.click(screen.getByRole("menuitem", { name: /History/ }));
    await user.type(
      screen.getByRole("textbox", { name: "Filter history" }),
      "asterism",
    );
    expect(screen.getByText("Asterism download")).toBeInTheDocument();
    expect(screen.queryByText("Night Drive")).not.toBeInTheDocument();
  });
});

describe("theme", () => {
  it("defaults to the monochrome skin at full ASCII density", async () => {
    render(<App />);
    await screen.findByLabelText("Music link");
    expect(document.documentElement.dataset.theme).toBe("mono");
    expect(document.documentElement.dataset.density).toBe("full");
  });

  it("switches to the legacy skin and drops ASCII density to compact", async () => {
    // Appearance changes apply immediately, so saveSettings echoes the draft.
    vi.spyOn(api, "saveSettings").mockImplementation(async (value) => value);
    const user = userEvent.setup();
    render(<App />);

    await screen.findByLabelText("Music link");
    await user.click(screen.getByRole("button", { name: "Settings" }));
    await user.click(screen.getByRole("menuitem", { name: /Preferences/ }));
    await user.click(screen.getByRole("tab", { name: "Appearance" }));
    await user.click(screen.getByRole("button", { name: /Legacy/ }));

    await waitFor(() =>
      expect(document.documentElement.dataset.theme).toBe("legacy"),
    );
    // This is what "muted cosmic" means concretely: same layout, less art.
    expect(document.documentElement.dataset.density).toBe("compact");
  });
});

describe("provider capabilities", () => {
  it("separates direct download from metadata matching", () => {
    expect(api.providerCapability("youTube")).toMatchObject({
      canDownload: true,
      label: "Direct download",
    });
    expect(api.providerCapability("spotify")).toMatchObject({
      canDownload: true,
      label: "Metadata matching",
    });
  });
});

describe("extractLink", () => {
  it("pulls a link out of share text", () => {
    expect(
      extractLink("Listen on Deezer: https://www.deezer.com/track/1 (via app)"),
    ).toBe("https://www.deezer.com/track/1");
  });
  it("keeps the last of two concatenated links", () => {
    expect(
      extractLink("https://a.com/x?list=1https://www.youtube.com/watch?v=2"),
    ).toBe("https://www.youtube.com/watch?v=2");
  });
  it("leaves plain text alone", () => {
    expect(extractLink("  not a link ")).toBe("not a link");
  });
});

describe("translations", () => {
  it("covers every English key in every language", () => {
    const english = dictionaryKeys("English").sort();
    for (const language of LANGUAGES) {
      expect(dictionaryKeys(language).sort(), language).toEqual(english);
    }
  });
});

describe("download resolution", () => {
  it("clears the previous preview while a new link is being resolved", async () => {
    const user = userEvent.setup();
    vi.spyOn(api, "readClipboard").mockResolvedValue("");
    vi.spyOn(api, "detectSource").mockResolvedValue({
      provider: "youTube",
      mediaKind: "track",
      supported: true,
      normalizedUrl: "https://www.youtube.com/watch?v=first",
      host: "www.youtube.com",
      message: "",
    });
    vi.spyOn(api, "inspectLink")
      .mockResolvedValueOnce({
        url: "https://www.youtube.com/watch?v=first",
        title: "First song",
        artist: "Artist",
        album: "",
        provider: "youTube",
        mediaKind: "track",
      })
      .mockImplementation(() => new Promise(() => {}));
    const queue = vi.spyOn(api, "queueDownload");
    render(
      <DownloadView
        settings={settingsFixture()}
        onSettings={vi.fn()}
        onQueued={vi.fn()}
        toast={vi.fn()}
      />,
    );
    const input = screen.getByLabelText("Music link");
    await user.type(input, "https://www.youtube.com/watch?v=first");
    await screen.findByText("First song");
    await user.clear(input);
    await user.type(input, "https://www.youtube.com/watch?v=second");
    expect(screen.queryByText("First song")).not.toBeInTheDocument();
    await user.keyboard("{Enter}");
    expect(queue).not.toHaveBeenCalled();
    await user.clear(input);
    expect(screen.queryByText("Reading link…")).not.toBeInTheDocument();
  });
});

describe("collection recovery", () => {
  it("offers a retry for partial collections without allowing complete collections to restart", async () => {
    const user = userEvent.setup();
    const start = vi.spyOn(api, "startJob").mockResolvedValue(undefined);
    const job: DownloadJob = {
      id: "partial",
      title: "Album",
      artist: "Artist",
      status: "completed",
      progress: 100,
      stage: "Complete · 1 unavailable",
      speed: "",
      eta: "",
      unavailableTracks: 1,
      createdAt: "2026-11-04",
      updatedAt: "2026-11-04",
      request: {
        url: "https://www.youtube.com/playlist?list=test",
        provider: "youTube",
        mediaKind: "playlist",
        format: "mp3",
        quality: "320",
        outputDir: "/Music",
        trackNumbers: true,
        embedMetadata: true,
        embedArtwork: true,
      },
    };
    const { rerender } = render(
      <QueueView jobs={[job]} logs={{}} toast={vi.fn()} />,
    );
    await user.click(
      screen.getByRole("button", { name: "Retry missing tracks" }),
    );
    expect(start).toHaveBeenCalledWith("partial");
    rerender(
      <QueueView
        jobs={[{ ...job, unavailableTracks: 0 }]}
        logs={{}}
        toast={vi.fn()}
      />,
    );
    expect(
      screen.queryByRole("button", { name: "Retry missing tracks" }),
    ).not.toBeInTheDocument();
  });
});

describe("localized secondary screens", () => {
  it("translates history controls in every supported language", () => {
    const labels = [
      "Filter history",
      "Поиск в истории",
      "Пошук в історії",
      "Filtrar historial",
      "Filtrer l’historique",
      "Verlauf filtern",
    ];
    for (const [index, language] of LANGUAGES.entries()) {
      const view = render(
        <LanguageProvider language={language}>
          <HistoryView history={[]} />
        </LanguageProvider>,
      );
      expect(
        screen.getByRole("textbox", { name: labels[index] }),
      ).toBeInTheDocument();
      view.unmount();
    }
  });
});

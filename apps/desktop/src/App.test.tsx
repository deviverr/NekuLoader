import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import * as api from "./lib/api";

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe("NekuLoader app shell", () => {
  it("loads the automatic link workflow without a source selector", async () => {
    render(<App />);
    await waitFor(() => expect(screen.getByRole("heading", { name: "Download" })).toBeInTheDocument());
    expect(screen.getByLabelText("Music link")).toBeInTheDocument();
    expect(screen.getByText("Automatic link recognition")).toBeInTheDocument();
    expect(screen.getByText(/YouTube, YouTube Music, and SoundCloud can download directly/i)).toBeInTheDocument();
    expect(screen.queryByLabelText(/source/i)).not.toBeInTheDocument();
  });

  it("opens Components on first run when required tools are missing without installing them", async () => {
    vi.spyOn(api, "isTauri").mockReturnValue(true);
    vi.spyOn(api, "onJobUpdate").mockResolvedValue(() => undefined);
    vi.spyOn(api, "bootstrap").mockResolvedValue({
      settings: {
        outputDir: "/Music",
        defaultFormat: "mp3",
        defaultQuality: "320",
        trackNumbers: true,
        embedMetadata: true,
        embedArtwork: true,
        soundEnabled: false,
        soundVolume: 0.5,
        animateBackground: false,
        reducedMotion: true,
        language: "English",
        theme: "Classic Legacy",
        concurrentDownloads: 1,
        checkToolUpdates: false,
      },
      jobs: [],
      history: [],
      tools: [{ name: "yt-dlp", required: true, installed: false, status: "Missing" }],
      legacyImported: false,
      version: "3.2.0-alpha.1",
    });
    const installer = vi.spyOn(api, "installComponents");

    render(<App />);

    expect(await screen.findByRole("heading", { name: "Settings" })).toBeInTheDocument();
    expect(screen.getByText(/Downloader components are missing/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Install required components" })).toBeInTheDocument();
    expect(installer).not.toHaveBeenCalled();
  });

  it("shows explicit component updates and a manual application release link", async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: "Settings" }));
    expect(screen.getByRole("button", { name: "Install required components" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "GitHub Releases" })).toBeInTheDocument();
    expect(screen.getByText(/No updater or platform-signing credential is embedded/i)).toBeInTheDocument();
  });

  it("scans and filters the local library", async () => {
    vi.spyOn(api, "scanLibrary").mockResolvedValue([
      {
        id: "/Music/Asterism/Mycelium Eyes.flac",
        title: "Mycelium Eyes",
        artist: "Sub Urban",
        album: "Asterism",
        path: "/Music/Asterism/Mycelium Eyes.flac",
        format: "FLAC",
        sizeBytes: 42_000_000,
        modifiedAt: "2026-08-04T00:00:00Z",
      },
      {
        id: "/Music/Night Drive.opus",
        title: "Night Drive",
        artist: "",
        album: "Music",
        path: "/Music/Night Drive.opus",
        format: "OPUS",
        sizeBytes: 8_000_000,
        modifiedAt: "2026-08-03T00:00:00Z",
      },
    ]);
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: "Library" }));
    expect(await screen.findByText("Mycelium Eyes")).toBeInTheDocument();
    const search = screen.getByRole("textbox", { name: "Search library" });
    await user.type(search, "opus");
    expect(screen.getByText("Night Drive")).toBeInTheDocument();
    expect(screen.queryByText("Mycelium Eyes")).not.toBeInTheDocument();
  });

  it("filters download history", async () => {
    vi.spyOn(api, "bootstrap").mockResolvedValue({
      settings: {
        outputDir: "/Music",
        defaultFormat: "mp3",
        defaultQuality: "320",
        trackNumbers: true,
        embedMetadata: true,
        embedArtwork: true,
        soundEnabled: false,
        soundVolume: 0.5,
        animateBackground: false,
        reducedMotion: true,
        language: "English",
        theme: "Classic Legacy",
        concurrentDownloads: 1,
        checkToolUpdates: false,
      },
      jobs: [],
      history: [
        { id: 1, legacyText: "Asterism download", createdAt: "2026-08-04T00:00:00Z" },
        { id: 2, legacyText: "Night Drive", createdAt: "2026-08-03T00:00:00Z" },
      ],
      tools: [],
      legacyImported: false,
      version: "3.2.0-alpha.1",
    });
    const user = userEvent.setup();
    render(<App />);
    await user.click(await screen.findByRole("button", { name: "History" }));
    await user.type(screen.getByRole("textbox", { name: "Filter history" }), "asterism");
    expect(screen.getByText("Asterism download")).toBeInTheDocument();
    expect(screen.queryByText("Night Drive")).not.toBeInTheDocument();
  });

  it("keeps installers safe in browser preview mode", async () => {
    await expect(api.installComponents()).rejects.toThrow(/installed desktop app/i);
  });

  it("distinguishes Spotify matching from recognition-only providers", () => {
    expect(api.providerCapability("spotify")).toMatchObject({ canDownload: true, label: "Metadata matching" });
    expect(api.providerCapability("deezer")).toMatchObject({ canDownload: false, label: "Recognition only" });
  });
});

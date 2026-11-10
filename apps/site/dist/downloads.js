/* Only explicitly available, same-origin installers can become download buttons. */
(() => {
  const panels = [
    {
      platform: 'macos-arm64', prefix: 'download', buttonId: 'mac-download', extension: '.dmg', name: 'Mac',
      detail: 'For Apple silicon Macs. This is a testing prerelease; read the release notes before installing.',
      warning: build => build.unsigned === true
        ? 'Not signed with Apple Developer ID or notarized. macOS may block opening this experimental build.'
        : 'Check the release notes for this build’s exact signing and notarization status.',
    },
    {
      platform: 'linux-x64', prefix: 'linux-download', buttonId: 'linux-download', extension: '.deb', name: 'Linux',
      detail: 'Debian 12 x64 .deb · glibc 2.36 or newer. Experimental: headless startup tested; desktop and phone validation ongoing.',
      warning: () => 'Experimental Linux build. Headless startup does not verify desktop integration, audio playback or physical phone transfer.',
      requiresExperimental: true,
    },
  ];
  function elements(panel) {
    return {
      status: document.getElementById(`${panel.prefix}-status`),
      detail: document.getElementById(`${panel.prefix}-detail`),
      warning: document.getElementById(`${panel.prefix}-warning`),
      button: document.getElementById(panel.buttonId),
      checksum: document.getElementById(`${panel.prefix}-checksum`),
    };
  }
  function unavailable(panel, ui) {
    ui.status.textContent = `No ${panel.name} installer is listed right now.`;
    ui.detail.textContent = 'Check GitHub Releases for current availability and known issues.';
    ui.warning.textContent = '';
    ui.button.hidden = true;
    ui.button.removeAttribute('href');
    ui.checksum.hidden = true;
    ui.checksum.textContent = '';
  }
  async function updatePanel(panel, downloads) {
    const ui = elements(panel);
    try {
      const build = downloads.find(item => item && item.platform === panel.platform && item.available === true);
      if (!build || typeof build.url !== 'string' || typeof build.version !== 'string' || !build.version.trim()
        || !/^[a-f0-9]{64}$/i.test(build.sha256 || '') || (panel.requiresExperimental && build.experimental !== true)) {
        return unavailable(panel, ui);
      }
      const url = new URL(build.url, location.origin);
      if (url.origin !== location.origin || !url.pathname.startsWith('/nekuloader/downloads/')
        || !url.pathname.toLowerCase().endsWith(panel.extension) || url.search || url.hash) return unavailable(panel, ui);
      const probe = await fetch(url.href, {method: 'HEAD', cache: 'no-store', signal: AbortSignal.timeout(8000)});
      if (!probe.ok || /(?:text\/html|application\/xhtml\+xml)/i.test(probe.headers.get('content-type') || '')) {
        return unavailable(panel, ui);
      }
      ui.status.textContent = `NekuLoader ${build.version}`;
      ui.detail.textContent = panel.detail;
      ui.warning.textContent = panel.warning(build);
      ui.button.href = url.href;
      ui.button.setAttribute('download', '');
      ui.button.hidden = false;
      ui.checksum.textContent = `SHA-256: ${build.sha256}`;
      ui.checksum.hidden = false;
    } catch { unavailable(panel, ui); }
  }
  async function load() {
    try {
      const response = await fetch('/nekuloader/downloads/manifest.json', {cache: 'no-store', signal: AbortSignal.timeout(8000)});
      if (!response.ok) throw new Error('Manifest unavailable');
      const manifest = await response.json();
      const downloads = Array.isArray(manifest?.downloads) ? manifest.downloads : [];
      await Promise.all(panels.map(panel => updatePanel(panel, downloads)));
    } catch {
      panels.forEach(panel => unavailable(panel, elements(panel)));
    }
  }
  load();
})();

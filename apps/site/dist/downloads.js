/* Only explicitly available, same-origin installers can become download buttons. */
(() => {
  const panels = [
    {
      platform: 'macos-arm64', prefix: 'download', buttonId: 'mac-download', extension: '.dmg', name: 'Mac',
      detail: 'macOS 12+ · Apple silicon.',
      warning: build => build.unsigned === true
        ? 'Unsigned; not notarized.'
        : build.notarized === true ? 'Signed and notarized by Apple.' : 'See release notes for signing status.',
    },
    {
      platform: 'linux-x64', prefix: 'linux-download', buttonId: 'linux-download', extension: '.deb', name: 'Linux',
      detail: 'Debian 12 · x64 · glibc 2.36+.',
      warning: () => 'Experimental; headless tests only.',
      requiresExperimental: true,
    },
    {
      platform: 'windows-x64', prefix: 'windows-download', buttonId: 'windows-download', extension: '.exe', name: 'Windows',
      detail: 'Windows 10/11 · x64.',
      warning: () => 'Unsigned testing build; native Windows feedback pending.',
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
    ui.status.textContent = panel.platform === "windows-x64" ? "Build in progress" : "Unavailable";
    ui.detail.textContent = '';
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

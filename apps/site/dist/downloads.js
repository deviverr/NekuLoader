/* A button is shown only for an explicitly available, same-origin installer. */
(() => {
  const status = document.getElementById('download-status');
  const detail = document.getElementById('download-detail');
  const warning = document.getElementById('download-warning');
  const button = document.getElementById('mac-download');
  const checksum = document.getElementById('download-checksum');
  const unavailable = () => {
    status.textContent = 'No Mac installer is listed right now.';
    detail.textContent = 'Check GitHub Releases for current availability and known issues.';
    button.hidden = true;
  };
  async function load() {
    try {
      const response = await fetch('/nekuloader/downloads/manifest.json', {cache:'no-store',signal:AbortSignal.timeout(8000)});
      if (!response.ok) throw new Error('Manifest unavailable');
      const manifest = await response.json();
      const build = Array.isArray(manifest.downloads) && manifest.downloads.find(item => item.platform === 'macos-arm64' && item.available === true);
      if (!build || typeof build.url !== 'string' || typeof build.version !== 'string' || !/^[a-f0-9]{64}$/i.test(build.sha256 || '')) return unavailable();
      const url = new URL(build.url, location.origin);
      if (url.origin !== location.origin || !url.pathname.startsWith('/nekuloader/downloads/') || !url.pathname.toLowerCase().endsWith('.dmg') || url.search || url.hash) return unavailable();
      const probe = await fetch(url.href, {method:'HEAD',cache:'no-store',signal:AbortSignal.timeout(8000)});
      if (!probe.ok || /text\/html/i.test(probe.headers.get('content-type') || '')) return unavailable();
      status.textContent = `NekuLoader ${build.version}`;
      detail.textContent = 'For Apple silicon Macs. This is a testing prerelease; read the release notes before installing.';
      warning.textContent = build.unsigned === true ? 'Unsigned and not notarized. macOS may block opening this alpha. Do not treat this build as signed or verified for every platform.' : 'Check the release notes for this build’s exact signing and notarization status.';
      button.href = url.href;
      button.setAttribute('download', '');
      button.hidden = false;
      checksum.textContent = `SHA-256: ${build.sha256}`;
      checksum.hidden = false;
    } catch { unavailable(); }
  }
  load();
})();

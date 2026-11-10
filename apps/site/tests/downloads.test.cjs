const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const script = fs.readFileSync(path.join(__dirname, '../dist/downloads.js'), 'utf8');
function build(platform, overrides = {}) {
  return {platform, version: '3.2.0-alpha.3', url: `/nekuloader/downloads/test${platform === 'linux-x64' ? '.deb' : '.dmg'}`,
    sha256: 'a'.repeat(64), available: true, experimental: true, unsigned: true, ...overrides};
}
async function render(downloads, {manifestError, manifestOk = true, head = () => ({ok: true, type: 'application/octet-stream'})} = {}) {
  const elements = {};
  const requests = [];
  for (const id of ['download-status','download-detail','download-warning','mac-download','download-checksum',
    'linux-download-status','linux-download-detail','linux-download-warning','linux-download','linux-download-checksum']) {
    elements[id] = {hidden: true, textContent: '', setAttribute(name, value) {this[name] = value;}, removeAttribute(name) {delete this[name];}};
  }
  vm.runInNewContext(script, {
    document: {getElementById: id => {assert.ok(elements[id], id); return elements[id];}},
    location: {origin: 'https://nekudev.com'}, URL, AbortSignal,
    fetch: async (url, options) => {
      requests.push({url, options});
      if (url.endsWith('manifest.json')) {
        if (manifestError) throw new Error('network');
        return {ok: manifestOk, json: async () => ({downloads})};
      }
      const response = head(url);
      if (response instanceof Error) throw response;
      return {ok: response.ok, headers: {get: () => response.type}};
    },
  });
  // Settle the manifest and independent HEAD request promise chains.
  await new Promise(resolve => setImmediate(resolve));
  return {elements, requests};
}
function hidden(ui, id) {assert.equal(ui[id].hidden, true); assert.equal(ui[id].href, undefined);}
test('both verified entries show correct platform links, checksums and experimental/signing notices', async () => {
  const {elements: ui} = await render([build('macos-arm64'), build('linux-x64')]);
  for (const id of ['mac-download','linux-download']) assert.equal(ui[id].hidden, false);
  assert.match(ui['mac-download'].href, /\.dmg$/);
  assert.match(ui['linux-download'].href, /\.deb$/);
  assert.match(ui['download-warning'].textContent, /notarized/);
  assert.match(ui['linux-download-detail'].textContent, /glibc 2\.36/);
  assert.match(ui['linux-download-warning'].textContent, /Headless startup does not verify/);
  for (const id of ['download-checksum','linux-download-checksum']) assert.match(ui[id].textContent, /a{64}/);
});
test('missing Linux entry leaves Mac available and no Linux button', async () => {
  const {elements: ui} = await render([build('macos-arm64')]);
  assert.equal(ui['mac-download'].hidden, false); hidden(ui, 'linux-download');
});
for (const platform of ['macos-arm64','linux-x64']) {
  const id = platform === 'linux-x64' ? 'linux-download' : 'mac-download';
  const checksum = platform === 'linux-x64' ? 'linux-download-checksum' : 'download-checksum';
  for (const [name, overrides] of Object.entries({unavailable: {available: false}, external: {url: 'https://elsewhere.example/nekuloader/downloads/test.deb'},
    traversal: {url: '/nekuloader/downloads/../test.deb'}, htmlExtension: {url: '/nekuloader/downloads/test.html'},
    invalidChecksum: {sha256: 'placeholder'}, emptyVersion: {version: ''}})) {
    test(`${platform}: ${name} never exposes installer`, async () => {
      const {elements: ui, requests} = await render([build(platform, overrides)]);
      hidden(ui, id); assert.equal(ui[checksum].hidden, true); assert.equal(requests.length, 1);
    });
  }
  for (const type of ['text/html; charset=utf-8','application/xhtml+xml']) {
    test(`${platform}: successful HTML fallback is rejected (${type})`, async () => {
      const {elements: ui} = await render([build(platform)], {head: () => ({ok: true, type})}); hidden(ui, id);
    });
  }
  for (const [name, head] of [['404', () => ({ok: false})], ['network error', () => new Error('network')]]) {
    test(`${platform}: installer ${name} keeps button hidden`, async () => {
      const {elements: ui} = await render([build(platform)], {head}); hidden(ui, id);
    });
  }
}
test('Linux requires explicit experimental flag', async () => {
  const {elements: ui} = await render([build('linux-x64', {experimental: false})]); hidden(ui, 'linux-download');
});
for (const options of [{manifestError: true},{manifestOk: false}]) {
  test(`manifest failure hides both platform buttons: ${JSON.stringify(options)}`, async () => {
    const {elements: ui} = await render([build('macos-arm64'),build('linux-x64')], options);
    hidden(ui, 'mac-download'); hidden(ui, 'linux-download');
  });
}
test('failed Linux probe does not suppress working Mac installer', async () => {
  const {elements: ui} = await render([build('macos-arm64'),build('linux-x64')], {head: url => ({ok: url.endsWith('.dmg'),type: 'application/octet-stream'})});
  assert.equal(ui['mac-download'].hidden, false); hidden(ui, 'linux-download');
});

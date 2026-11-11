# NekuLoader download page

Black cosmic page using the desktop app's original block wordmark, pixel sky,
planet drawing and sleeping cat. The cat is fixed to the browser viewport's
bottom edge, outside the app screenshot. Includes a current screenshot, concise
feature/setup details, and platform download cards with explicit signing/test
status. No analytics or remote fonts.

The NekuDev root homepage remains separate and image-only (`apps/home/dist`).

Regenerate the original app drawing code for the site with:

```sh
node apps/site/scripts/build-effects.mjs
```

This uses the desktop checkout's installed esbuild to transpile the original
canvas drawing helpers. No React runtime is shipped on the site. If effects
change, update the `site-effects.js` cache key in index.html. Background motion
pauses in hidden tabs and respects the system reduced-motion preference.

Production paths on the existing VPS:
- `/var/www/nekudev/nekuloader/`
- `/etc/nginx/sites-available/nekudev.com`

Download binaries are release artifacts; tracked source contains the manifest
and page assets. The 38 availability/error/signing-notice checks are run with
`node --test apps/site/tests/downloads.test.cjs`.

The Nov 11, 2026 dated Mac DMG contains the updated UI and notarized app.
Windows/Linux alpha.4 now carry the same interface, with their experimental
platform testing limits retained.

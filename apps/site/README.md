# NekuLoader download page

Minimal total-black/white page with the Pixelify typeface, actual app screenshot,
three platform download cards, and essential release/license links. No marketing
sections, FAQ, feature grid, analytics, or remote fonts. `downloads.js` uses the
same-origin availability manifest; unavailable binaries never become buttons.

The NekuDev root homepage is separate: `apps/home/dist` contains only the user’s
snow-leopard image. Its visible page contains no text or navigation.

Production paths on 169.58.10.252:
- `/var/www/nekudev/index.html` and `/var/www/nekudev/snow-leopard.png`
- `/var/www/nekudev/nekuloader/`
- `/etc/nginx/sites-available/nekudev.com`

Download files are external release artifacts; source includes only the manifest.
The 37 availability/error-path checks include the Windows testing-build gate.

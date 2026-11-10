# NekuLoader website

Buildless HTML/CSS/JavaScript source in `dist/`, using the real desktop Cosmic
colors, Pixelify Sans (OFL notice included), stars, pixel controls, and an actual
alpha.3 application screenshot. No analytics or external font requests.

Host: `nekudev.com/nekuloader/`, files `/var/www/nekudev/nekuloader/` on
169.58.10.252. Nginx configuration: `/etc/nginx/sites-available/nekudev.com`.
HTTPS and renewal use Certbot. Other site routes/virtual hosts are separate.

`downloads.js` reads `/nekuloader/downloads/manifest.json`. It shows a download
only for an explicitly available same-origin DMG with a SHA-256 digest and a
successful non-HTML HEAD response. Installer binaries are release artifacts,
not Git-tracked website source. Copy verified artifacts alongside the manifest
when deploying. Windows/Linux remain unavailable until their validation permits
publication. The free proprietary license preserves third-party license rights.

Responsive browser checks passed at mobile and desktop widths, with loaded
images and no horizontal overflow. Download availability logic has separate
success/error-state checks. A production deployment must additionally verify
actual file bytes, checksums and HTTP range support.

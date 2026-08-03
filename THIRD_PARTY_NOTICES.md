# Third-Party Notices

This recovered source tree currently depends on these Java libraries at build/runtime:

- FlatLaf 3.7.2, Apache License 2.0
- org.json 20260522, JSON License
- JLayer 1.0.1, LGPL

The application also shells out to user-installed tools for download and conversion workflows, including yt-dlp, spotdl, Zotify/deemix-compatible tooling, FFmpeg, adb, and libimobiledevice utilities. If you bundle any of those tools in a release, include their exact licenses and notices for the specific binaries you distribute.

Do not publish the original packaged Windows runtime folders (`jre/`, `python/`, `ffmpeg/`) or installer/runtime binaries without auditing their redistribution terms.

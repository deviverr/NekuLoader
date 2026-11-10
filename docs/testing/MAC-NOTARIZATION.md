# Mac signing and notarization through Xcode

Use the already tested release app; preserve the published installer until the
replacement has passed Apple notarization and local verification. Never publish
an Apple Development signature as a Developer ID release.

For alpha.3, the owner selected Khabib Bairamov’s team (`VSUKBA84LT`). Creating
a local Developer ID certificate in Xcode failed because Apple restricts that
operation to the Account Holder. The supported Xcode automatic distribution
route successfully prepared and uploaded an archive using the signed-in account
and managed distribution signing instead. No account passwords or private keys
are copied into the repository.

The temporary archive contains `Products/Applications/NekuLoader.app` and
`Info.plist` with ArchiveVersion 2, CreationDate, Name, SchemeName and
ApplicationProperties (ApplicationPath, bundle identifier, version, architecture,
SigningIdentity and Team). The archive app is signed with the existing Apple
Development certificate and `codesign --options runtime --timestamp` before
Xcode re-signs for Developer ID distribution.

Export options: `method=developer-id`, `destination=upload`,
`signingStyle=automatic`, `teamID=VSUKBA84LT` and
`manageAppVersionAndBuildNumber=false`. Upload using:

```sh
xcodebuild -exportArchive -archivePath NekuLoader.xcarchive \
  -exportOptionsPlist ExportOptions.plist -allowProvisioningUpdates
```

Open the archive in Xcode Organizer to monitor Apple’s processing. After the
notarization succeeds:

```sh
xcodebuild -exportNotarizedApp -archivePath NekuLoader.xcarchive \
  -exportPath notarized
```

Verify the exported app’s Developer ID identity/team, hardened runtime, strict
code signature, stapled ticket and Gatekeeper acceptance before repackaging.
Then check the mounted final DMG’s app as well, update its SHA-256, and only
then change the public manifest and signing notice.

References: [Apple’s Xcode distribution workflow](https://help.apple.com/xcode/mac/current/en.lproj/dev033e997ca.html),
[Tauri signing guide](https://v2.tauri.app/distribute/sign/macos/).

## Alpha.3 verification

Apple accepted submission `73F0A2CB-C0F2-4AA8-8E7B-AC96092F5246` on
2026-11-10. Xcode exported the app signed with Developer ID Application:
Khabib Bairamov (`VSUKBA84LT`), with hardened runtime and a stapled ticket.
`codesign --verify --deep --strict`, `xcrun stapler validate` and
`spctl --assess --type execute` passed for both the exported app and the app
inside the final mounted DMG. The latter launched and played/paused the
authorized Blender test media, with playback position advancing.

The DMG contains the notarized app and Applications shortcut; its outer disk
image is not separately signed or ticketed. The shipped app carries the
Developer ID signature and notarization ticket.

Installer SHA-256:
`a3a894a9e6e18d7bd577e4145f9cede0a7fe4e5603ab3d56c2ce82a9554d5fc5`.

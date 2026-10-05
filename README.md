# Vibeuroba

**An Android imageboard browser maintained by [TheRWX](https://github.com/TheRWX).**

Vibeuroba brings browsing, posting, bookmarks, and media downloads into one app.
Read boards and threads, follow conversations, view images and videos, and save
threads for later. It is a continuation of
[Kuroba Experimental](https://github.com/K1rakishou/Kuroba-Experimental), which is
itself a fork of [Kuroba](https://github.com/Adamantcheese/Kuroba).

This fork starts its own public version series at **1.0**, under the
**Vibeuroba** project name. Its Git history and upstream attribution are preserved.

## What the app does

- **Browse and search:** open boards and threads in tabs, search supported sites,
  and combine multiple boards into a single catalog.
- **Follow conversations:** bookmark threads, organize bookmarks into groups,
  and receive thread update notifications.
- **Post replies:** attach media from your device, shared files, or a URL;
  queue replies with the background posting service.
- **View media:** open images, GIFs, and videos in a dedicated viewer. Optional
  mpv playback requires a separate download.
- **Save content:** download media in the background and export downloaded
  threads as HTML with their media files.
- **Customize your experience:** use dynamic light and dark themes, content
  filters, and per-site proxy settings.
- **Read archives:** access supported third-party thread archives.

The inherited site integrations include 4chan, Dvach, 8kun, Lainchan, Endchan,
and others. Available features depend on each site's API, permissions, and
availability; inclusion in the source does not guarantee that a service is
currently accessible.

## Base and status

Vibeuroba 1.1 (`v1.3.47`) is built on the current KurobaEx stable release,
**KurobaEx v1.3.47**, with Vibeuroba's changes on top. Vibeuroba 1.0 (`v1.3.36`)
was built on the unreleased 2024 `KurobaEx_2.0.0` development branch; that base
and its unfinished Compose rewrite were dropped in 1.1.

Vibeuroba changes on top of KurobaEx:

- Independent app ID, name, update channel and release signing key.
- App lock with a user-created PIN (no default PIN), optional fingerprint/face.
- Hide from screenshots and recents, blur thumbnails until tapped, Material You
  theme (all off by default).
- Bug reports open the Android share sheet; nothing is uploaded.
- Fixes for a user-agent startup freeze and a toolbar transition crash.

Testing so far is manual, on Android emulators. No physical device run has been
recorded. Release tags follow the updater scheme `vMAJOR.MINOR.PATCH`, which maps
to the Android version code (`v1.3.47` = `10347`).

## Download and installation

Published APKs are listed on this fork's
[Releases page](https://github.com/TheRWX/Vibeuroba/releases).
Check the release title and attached assets before installing. Release APKs
are signed with the Vibeuroba release key; verify the certificate before
installing:

```sh
apksigner verify --print-certs Vibeuroba.apk
# Signer #1 certificate SHA-256 digest:
# ba568a31fb051b24447636cbc2f730a2a56c02e5328985d0ea9c129dc86161de
```

Earlier test APKs (including `v1.3.35`) were debug-signed and use a different
application ID, so they cannot be upgraded in place. The upstream project's APKs and F-Droid
listing are separate distributions.

The app targets devices running Android 6.0 (API 23) or newer. Vibeuroba uses
the independent Android application ID `com.github.therwx.vibeuroba`, allowing
it to install alongside Kuroba without replacing Kuroba or its data. Existing
settings and bookmarks are not automatically migrated. Future Vibeuroba updates
must use the same signing key and an increasing internal version code.

## Build from source

The Android project lives in [`Kuroba/`](Kuroba/). It builds with JDK 21,
Android SDK 36 and the included Gradle wrapper. Dependencies are fetched from
Google Maven, Maven Central, and JitPack.

```sh
git clone --branch vibeuroba-1.0 https://github.com/TheRWX/Vibeuroba.git
cd Vibeuroba/Kuroba
```

Set your SDK location in an untracked `local.properties` file:

```properties
sdk.dir=/absolute/path/to/your/android-sdk
```

The build type is chosen with the `buildType` property (`0` stable, `1` beta,
`2` dev; dev is the default):

```sh
./gradlew :app:assembleDebug -PbuildType=2     # development build
./gradlew :app:assembleRelease -PbuildType=0   # stable release (needs signing config)
```

Release builds read the signing key from an untracked `Kuroba/app/release.properties`.
Release builds produce per-ABI APKs plus a universal APK. Attach the universal APK
first on GitHub releases: Vibeuroba 1.0's updater downloads the first APK asset.

## Screenshots

These screenshots are inherited from Kuroba Experimental and illustrate the
existing app; they are not verified screenshots of the Vibeuroba 1.0 build.

<details>
<summary>View the inherited screenshot gallery</summary>

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/5.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/6.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/7.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/8.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/9.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/10.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/11.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/12.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/13.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/14.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/15.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/15.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/16.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/16.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/17.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/17.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/18.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/18.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/19.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/19.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/20.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/20.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/21.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/21.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/22.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/22.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/23.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/23.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/24.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/24.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/25.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/25.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/26.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/26.png)

</details>

## Feedback and contributions

Use this fork's [issue tracker](https://github.com/TheRWX/Vibeuroba/issues)
for Vibeuroba feedback. Include the app version, Android version, affected site,
and steps to reproduce a problem. Discuss larger features before implementing
them so they fit the direction of this fork.

## Credits and license

Vibeuroba builds on the work of K1rakishou, the Kuroba Experimental contributors,
and the original Kuroba contributors. Existing copyright notices and attribution
remain in the source.

Licensed under [GNU GPL v3](COPYING.txt). See also the
[third-party library licenses](Kuroba/app/src/main/assets/html/license.html).

# Stario

<img src="docs/representative.png" alt="Stario representative">

My fork of [Stario](https://github.com/albu-razvan/Stario), Răzvan Albu's minimalist Android
launcher. He archived the project in 2026, so I picked it up to keep running it on my own phone.

> [!NOTE]
> I build this for myself and don't publish it anywhere.
>
> Part of why the original was archived is that Play Store rules and OEM restrictions make an
> independent launcher hard to keep alive. None of that applies to something I sideload onto my
> own device, so this fork is free to use APIs a published launcher would have to avoid.
>
> Original work Copyright (C) 2025 Răzvan Albu, GPL-3.0. This fork stays GPL-3.0 and keeps the
> upstream copyright notices intact.

## Roadmap

Running notes on what is done, what is in flight, and what is worth doing next.
Numbers here are counted from the tree, so they go stale; re-check before trusting them.

### Done

- Toolchain brought up to current: Gradle 9.7.1, AGP 9.3.2, Kotlin 2.4.10,
  compileSdk 37.1, targetSdk 37, Java 21, JDK 25 locally and in CI
- Package renamed to `adrianogba.stario.launcher`
- Dropped the store metadata, archived APK and community files this fork has no use for
- Reviewed the [yutila-org fork](https://github.com/yutila-org/stario) for anything worth taking

### In progress

- **Java to Kotlin.** 109 of 206 files, roughly 7.6k of 41k lines. Going leaf-first so the
  build stays green: every batch compiles and gets installed on an emulator before the next
  one starts.
- `LauncherApplication`, `ProfileApplicationManager`, `CategoryManager` and `Category` are
  left for last and want converting as one group. They share package-private mutable state,
  and `LauncherApplication.FALLBACK_APP` is a null typed as non-null that around twenty call
  sites compare against instead of null-checking.

### Next

- Port the `IconPackManager` drawable `LruCache` from the yutila fork. Standalone, and it
  speeds up icon lookup on its own
- Port their icon picker, which lets you override one app's icon from any installed pack
- Port their Notes sheet. `app/build.gradle` already lists `src/main/res/notes` as a resource
  directory, but no Notes code was ever written, so the wiring is half there already
- Add Detekt as a Gradle plugin, once enough of the code is Kotlin for the first run to mean
  something
- Pin the GitHub Actions in `build.yml` to commit SHAs rather than floating tags
- Turn Actions on for this fork. GitHub disables workflow runs on forks until you click
  through once in the Actions tab

### Libraries worth replacing

| Library | Why |
| --- | --- |
| `tk.zielony:carbon:0.17.0` | Used in 39 files and has had no release since. The single biggest risk to future Android compatibility here, and the hardest to pull out |
| `androidx.localbroadcastmanager` | Deprecated. 11 files use it. A flow or a plain observer would do |
| `com.ogaclejapan.smarttablayout:2.0.0` | From 2016, 2 usages, replaceable with a TabLayout |
| `jp.wasabeef:glide-transformations` | Last released 2020 |
| `com.luckycatlabs:SunriseSunsetCalculator:1.2` | Ancient. Only used for sunrise and sunset times, which is not much code to own outright |
| `com.github.ChickenHook:RestrictionBypass` | Pinned to a commit because every tagged release fails to build on JitPack. Worth watching |

### Structure and code health

- **No tests anywhere.** Zero files under `test` or `androidTest`. Compilation is currently the
  only automated check, and it does not catch the nullability breaks that the Kotlin migration
  keeps producing
- **No static analysis.** See Detekt above
- `hidden/` has to stay Java. It drives Rikka's `refine` through a Java annotation processor,
  and converting it without adding KAPT would stop the processor running with no compile error
  and break hidden API access at runtime
- `Widget` overrides `equals` without `hashCode`
- `Feed.equals` compares only `rss` while `Feed.hashCode` also folds in `title`, so two equal
  feeds can hash apart. Both of these are carried over from upstream and left alone so far,
  since fixing them changes behaviour

### Feature ideas

Nothing here is committed to. This fork is sideloaded and never published, so the Play Store
rules that shaped the original do not apply.

- **Usage based sorting.** `UsageStatsManager` is not used anywhere yet. Most-used apps in the
  drawer is probably the biggest single quality of life win available
- **Shizuku.** The project already pulls in `refine` and `RestrictionBypass`, so half the
  plumbing exists. Opens up silent freeze, force stop and uninstall, plus real usage data
- **A useful accessibility service.** Right now it does exactly one thing, lock the screen.
  The API allows back, recents, quick settings and per-app gestures
- **`WRITE_SECURE_SETTINGS`** through a one time adb grant, for toggling gesture navigation,
  immersive mode and animation scales from the launcher
- **More search sources.** Contacts, files, settings entries, inline maths and unit conversion,
  and app shortcuts all fit the existing `Searchable` adapter pattern
- **Notification content on long press.** Per-package counts are already tracked, so most of
  the plumbing is there
- **Config export and import.** `allowBackup` is false and there is no export path, so a
  reinstall loses the whole layout
- **New glance cards.** `GlanceExtension` is a real plugin point: implement the interface and
  attach it in `Launcher`. Battery, next alarm, Home Assistant state and transit all fit

## Overview

Inspired by the minimalist phone concept, Stario aims to keep functionality and productivity at
their peak in a simple and elegant format.

This repository contains the complete codebase for Stario, a full rewrite of the previous
Stario Launcher. This version offers significant improvements in both performance and usability.

## Features

- **Material You Support**  
  Integrates seamlessly with Android’s Material You dynamic theming system, adapting colors based on
  your wallpaper and device settings.

- **Application Customization**  
  Customize your home screen with various icon packs and shapes to personalize your experience.

- **Built-In Weather Widget**  
  Check current weather conditions and forecasts right from your home screen.

- **Global Search Integration**  
  Perform fast, privacy-respecting searches using Kagi directly from the launcher.

- **Minimalistic Media Player Controls**  
  Manage your media playback easily with integrated controls.

- **Application Categories**  
  Organize your app drawer with customizable categories for better app management.

- **RSS/Atom Reader**  
  Stay up-to-date with news and blog feeds via the integrated RSS/Atom reader.

- **Page Sorting**  
  Easily reorder your home screen pages to suit your workflow.

## Availability

There are no builds to download. Build it yourself with the steps below.

The original project's releases are still up at
[albu-razvan/Stario](https://github.com/albu-razvan/Stario/releases/latest).

## Compatibility

- Requires **Android SDK 29+** (Android 10.0 or later)
- Compatible with AOSP and most major OEM devices
- Probably fine on custom ROMs, though I haven't tried any

## Development

You can quickly set up the development environment using the provided Dockerfile:

```bash
docker build --platform linux/amd64 -t stario-dev .

docker run --platform linux/amd64 --rm -it \
  -v </path/to/output>:/usr/local/stario/build \
  stario-dev
```

> Tip: Use `--rm` to automatically remove the container after use.

## Building

Should you wish to build the application yourself, run the build
script from within the development environment:

```bash
# Optionally, checkout to the tagged commit
git checkout v2.9

./build.sh
```

Alternatively, to also build a signed copy (APK and AAB), pass a keystore to the build script:

```bash
docker run --platform linux/amd64 --rm -it \
  -v </path/to/output>:/usr/local/stario/build \
  -v </path/to/keystore>:/usr/local/stario/keystore \
  stario-dev

# Optionally, checkout to the tagged commit
git checkout v2.9
  
./build.sh \
  -K /usr/local/stario/keystore/keystore.jks \
  -P keystore_password \
  -a key_alias \
  -p key_password
```

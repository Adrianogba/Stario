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

## What's different here

- App ID is `adrianogba.stario.launcher`
- Builds on Gradle 9.7.1, AGP 9, Kotlin 2.4, compileSdk 37, Java 21, JDK 25
- The Java source is being rewritten in Kotlin

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

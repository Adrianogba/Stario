# Stario

<img src="docs/representative.png" alt="Stario representative">

My fork of [Stario](https://github.com/albu-razvan/Stario), Răzvan Albu's minimalist Android
launcher. He archived the project in 2026, so I picked it up and kept it going.

## Roadmap

Running notes on what is done, what is in flight, and what is worth doing next.
Numbers here are counted from the tree, so they go stale; re-check before trusting them.

### Done

- Toolchain brought up to current: Gradle 9.7.1, AGP 9.3.2, Kotlin 2.4.10,
  compileSdk 37.1, targetSdk 37, Java 21, JDK 25 locally and in CI

### In progress

- **Java to Kotlin.** 147 of 206 files. The sheet gesture core, Measurements, UiUtils,
  ActionDialog and ThemedActivity are all across now. Going leaf-first so the
  build stays green: every batch compiles and gets installed on an emulator before the next
  one starts.
- `LauncherApplication` is converted and `FALLBACK_APP` is gone; it was a constant set
  to null that thirteen files compared against instead of comparing to null.
  `ProfileApplicationManager`, `CategoryManager`, `CategoryMappings` and `Category` are
  the remaining cluster. They share package-private mutable state on
  `LauncherApplication`, which is why its fields are `@JvmField` for now; once the
  package is all Kotlin they can become ordinary properties.
- `ClosingAnimationView` and `GlanceConstraintLayout` cannot be converted at all while
  carbon is still here. They extend `carbon.widget.ConstraintLayout`, which exposes two
  declarations with the same JVM signature for `getElevation()`, and Kotlin refuses to
  subclass that. It fails the compile and is not suppressible. Only these two files are
  affected: holding a carbon type in a Kotlin class is fine, inheriting from one is not.

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
| `tk.zielony:carbon:0.17.0` | Used in 39 files and has had no release since. The single biggest risk to future Android compatibility here, and the hardest to pull out. Also now a hard blocker: no Kotlin class can extend a carbon widget |
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

### Compose, and why not all at once

Compose is already a dependency here and already used once, in
`SliderComposeView`, embedded in the view hierarchy through a `ComposeView`.
So the question is not whether to adopt it but how far to take it.

The tree has 74 layout files. 48 of them embed one of this project's own custom
views, which is the part that matters: a launcher is close to the worst case for
a full Compose rewrite.

- Third-party home screen widgets arrive as `RemoteViews` and have to be hosted by
  a real `AppWidgetHostView`. Under Compose that becomes an `AndroidView` wrapper,
  so nothing is gained.
- The sheet system is roughly 2,900 lines of `CoordinatorLayout.Behavior` and custom
  drag maths across `SheetBehavior` and `SheetDragHelper`. There is no equivalent to
  port to; it would be a rewrite from scratch, and it is the fiddliest code here.
- Home screen drag and drop uses `startDragAndDrop` with a `DragShadowBuilder`.
- The icon rendering in `AdaptiveIconView` is `Canvas` work that is no nicer either way.

Where it genuinely pays is the settings surface: 21 of the 74 layouts are settings
and settings dialogs, mostly lists, switches and text. Those have no custom gesture
work and are the sort of thing Compose makes shorter and easier to change.

So the plan, if this gets picked up:

1. Finish the Kotlin migration first. Converting screens that then get rewritten in
   Compose is wasted work, and Compose wants Kotlin anyway.
2. Move the settings screens over incrementally, one dialog at a time, using
   `ComposeView` exactly the way `SliderComposeView` already does.
3. Leave the launcher surface on Views: the grid, the sheets, the widget host and the
   drawer. Not out of caution, but because Compose has nothing better to offer there.

Also worth saying plainly: 176 of the 314 XML files are drawables and 29 are values.
Compose does not replace those. The XML count is not as damning as it looks.

### Simplification opportunities

Measured, not guessed. Line counts and diff counts below come from the tree
and from the lint and dependency-analysis reports, so re-check them before
acting.

One thing that is already clean: `./gradlew projectHealth` reports no unused
and no misused dependencies, and no duplicate classes on the classpath. The
dependency list is honest.

**The four sheet dialogs are the same file four times.** `TopSheetDialog`,
`BottomSheetDialog`, `LeftSheetDialog` and `RightSheetDialog` are 44 lines
each, 176 in total. Neutralise the direction words and only 4 lines differ
between any two. They vary by a layout id, a view id and a `SheetType`. One
class taking those three values, plus the existing `SheetDialogFactory`,
would replace all four and drop roughly 125 lines with no behaviour change.
This is the easiest real win in the project.

**The four sheet behaviors are nearly the same twice.** 1,393 lines across
`TopSheetBehavior`, `BottomSheetBehavior`, `LeftSheetBehavior` and
`RightSheetBehavior`. Same-axis pairs are close: neutralising direction words
leaves 74 differing lines between top and bottom, and 53 between left and
right. Across axes they genuinely diverge, 184 lines between top and left.
So the realistic shape is two axis classes with a sign parameter rather than
one class for all four. Worth maybe 400 lines, but this is the drag and
gesture core and there are no tests, so it should come last and be done with
the emulator in hand.

**Three home screen widgets share one shape.** `ClockWidget`, `SearchWidget`
and `PinnedCategory` total 382 lines and each does the same five things:
build a `DraggableGridItem`, set an `ItemLayoutData`, register a preference
listener, flip visibility in `updateContainerState`, unregister in `detach`.
Only the layout, the tag and the span constraints differ. A small base class
holding the attach and detach lifecycle would remove around 120 lines and,
more usefully, means a fourth home screen widget is a short subclass.

**Two classes are both called `IconsRecyclerAdapter`**, one in `apps/popup`
and one in `activities/settings/dialogs/icons`, 177 lines together. They do
different jobs, one lists icons within a pack and the other lists packs, so
this is a naming problem rather than duplication. Renaming them to say what
they list would stop the import list being a coin flip.

**41 unused resources.** Lint lists them by name. Mostly `Theme_*_Opaque`
styles and stray drawables. Straight deletions.

**Three obsolete resource folders.** A `-v29` drawable folder and two `-v26`
mipmap folders, all redundant now that `minSdk` is 29. Merge them into their
parent folders.

**40 places where an androidx KTX extension would be shorter**, mostly
`edit().apply()` in place of `edit { }`. Upstream deliberately avoided the
KTX artifacts, which is why the old build file carried
`noinspection KtxExtensionAvailable` comments. Adopting them means adding
`core-ktx` and friends, so it is a trade: fewer lines against more
dependencies. Reasonable either way, but decide it once rather than per file.

**The adaptive launcher icon has no monochrome layer.** Not a simplification,
but it is one XML attribute and the app already supports themed icons, so it
is odd that its own icon does not.

### Liquid glass

[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
published as `io.github.kyant0:backdrop`. Apache-2.0, which is fine to use
from a GPL-3.0 project, actively maintained, and already a dependency here.
minSdk moved to 33 for it, which also made 34 of the 36 SDK checks in the
source dead.

The intended shape is a glass option: a preference that switches the launcher
surfaces over to glass, not a rewrite.

#### Can we actually get the wallpaper? Yes, tested

Glass has to sample what is behind it, and the wallpaper is composited by the
system behind a translucent window, so it is in no view or Compose tree this
app owns. The library's `CanvasBackdrop` accepts arbitrary drawing as the
backdrop source, so the question is only whether the wallpaper bitmap can be
read at all.

Probed on an API 37 emulator, three attempts:

| Setup | `WallpaperManager.getDrawable()` |
| --- | --- |
| No permission, not default home | SecurityException, READ_EXTERNAL_STORAGE denied |
| No permission, set as default home | same SecurityException |
| `READ_MEDIA_IMAGES` declared and granted | `BitmapDrawable 922x1024` |

So being the default launcher is **not** sufficient, contrary to what is often
assumed. From Android 13 the wallpaper counts as media and needs
`READ_MEDIA_IMAGES`, granted at runtime like any other media permission.

That is the real cost of this feature, and it is worth being honest about it:
a launcher asking for photo access looks alarming, even though the wallpaper
is the only thing it reads. The permission should be requested lazily, only
when someone turns the glass option on, never at first run, and the option
should degrade to the current flat surfaces if it is refused.

#### The rest, if that trade is acceptable

1. A reusable `AbstractComposeView` rendering one glass surface, taking the
   wallpaper through a `CanvasBackdrop`, with the offset and zoom the launcher
   already applies so the glass lines up with what is on screen.
2. Put it behind the glance card first. Small, self-contained, already over the
   wallpaper and outside the sheet gesture machinery, so it either looks good
   or reverts in one commit.
3. A preference to switch it on, defaulting off, gated on the permission.
4. Then the search widget and the popup menus, if it holds up.

#### What it does not replace

Sheets blur their background with `Window.setBackgroundBlurRadius`, which the
system composites and which blurs everything behind the window, wallpaper and
other apps included. Nothing in Compose can do that. Liquid glass is for the
surfaces sitting on top, not for the window behind them.

The library also ships no components by design. Buttons, toggles and cards are
yours to build, with their catalog app as reference.

### Feature ideas

Nothing here is committed to. This fork is not published through the Play Store, so the rules
that shaped the original do not apply.

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

There are no prebuilt releases yet. Build it yourself with the steps below.

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

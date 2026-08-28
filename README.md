# Stario

<img src="docs/representative.png" alt="Stario representative">

My fork of [Stario](https://github.com/albu-razvan/Stario), Răzvan Albu's minimalist Android
launcher. He archived the project in 2026, so I picked it up and kept it going.

## Roadmap

Running notes on what is done, what is in flight, what is next and what is
still only an idea. Counts are taken from the tree, so they go stale; re-check
before trusting them.

### Done

**Toolchain**

- Gradle 9.7.1, AGP 9.3.2, Kotlin 2.4.10, compileSdk 37.1, targetSdk 37, Java
  21, JDK 25 locally and in CI
- Version catalog and the plugins DSL, so versions live in one file
- minSdk 33, which the liquid glass shader needs and which made 34 of the 36
  SDK version checks in the source dead
- Daemon, build cache and parallel execution on. An incremental compile went
  from about 40 seconds to under 3
- Package renamed to `adrianogba.stario.launcher`, version set to 3.0.0-beta
- CI hardened: every action pinned to a commit SHA, concurrency group, gradle
  cache, debug APK uploaded as an artifact

**Removed**

- Store metadata, archived APK and community files this fork has no use for,
  plus 41 unused resources and three obsolete resource folders
- `FALLBACK_APP`, a constant set to null that thirteen files compared against
  instead of comparing to null. It was hiding two real nullability bugs
- The Discord link, and the four cities the location picker seeded as if they
  were suggestions

**Java to Kotlin**

- All 206 convertible files, leaf first so the build stayed green. Every file
  compiles before the next one starts, and anything on a path a user can reach
  was driven on an emulator before being committed
- Dead SDK checks went with them. minSdk 33 made every check for R, S and
  TIRAMISU always true, and one of them in `SearchFragment` was hiding an
  entire second keyboard implementation that could never run
- Three files stay as they are. `ClosingAnimationView` and
  `GlanceConstraintLayout` extend `carbon.widget.ConstraintLayout`, which
  declares two `getElevation()` methods with the same JVM signature, and Kotlin
  refuses to subclass that; it is not suppressible. `PreEventNestedScrollView`
  is an androidx shim that has to sit in the androidx package
- Repetition collapsed where it was hiding the shape of the code, not
  everywhere. The eight settings rows that each held a dialog and a showing
  flag, the three session picker loops in `Media`, the four row grouping walks
  in `WidgetsDialog`, and the 41 entry weather symbol table that was a HashMap
  of HashMaps keyed by three int constants
- Bugs found on the way were commented, not silently fixed: an aliasing bug in
  `DynamicGridLayout` where cloning a grid state shares its rectangles, a
  padding call in `WidgetsDialog` that reads the bottom inset into the top
  slot, and a delete path that only forgets an entry when it still parses

**Interface**

- Language picker, System default and English, over
  `AppCompatDelegate.setApplicationLocales`. The 19 translations and the
  `locales_config.xml` listing them were already there, so each new language is
  one enum entry
- Surface style option, Material or Liquid Glass, with both chips drawn in the
  style they select so the choice is shown rather than described
- Dark mode and theme colour apply on the spot with the dialog still open,
  instead of restarting the process
- Theme colour list wraps onto centred lines, so all twelve are visible at once
- Glance card and search widget render as glass, cut to the same outlines their
  drawables use and still turning, tinted from the wallpaper's own colours
- Weather units name both systems, and the clock toggle says what it does
- Info section points at this fork and its own site

### Doing

Nothing in flight. The Kotlin migration finished; see below.

### Next

In rough order:

1. Collapse the four sheet dialogs into one. They are the same 44 line file four
   times and only 4 lines differ between any two. Easiest real win here.
2. Add Detekt as a Gradle plugin, now that the code is mostly Kotlin.
3. Port the `IconPackManager` drawable cache from the yutila fork. Standalone,
   and it speeds up icon lookup on its own.
4. Port their icon picker, which overrides one app's icon from any pack.
5. Port their Notes sheet. `app/build.gradle` already lists `src/main/res/notes`
   as a resource directory, so the wiring is half there.
6. Give the home screen widgets a shared base class. `ClockWidget`,
   `SearchWidget` and `PinnedCategory` each repeat the same five step attach and
   detach lifecycle.

### Evaluating

Decided in principle, not started, and each has an open question worth settling
before writing code:

- **Glass with refraction on the drawer, popups and sheets.** These sit over
  content this app draws, so their pixels can be captured and genuinely
  refracted, which the home screen widgets cannot be. The switch and both
  preview chips already do it. Question worth settling first is how much of
  each surface should be glass, since Apple's own rule is that glass belongs to
  the floating control layer and never to the content under it.
- **A wallpaper Stario owns.** The only route to real refraction on the home
  screen. If the user picks an image inside the app, through the photo picker
  which needs no permission, the launcher can draw it itself and then sample
  what it drew. Costs a second copy of the wallpaper and a decision about what
  happens when the system wallpaper changes underneath it.
- **Compose for the settings screens.** 21 of the 74 layouts are settings and
  settings dialogs, with no custom gesture work. The launcher surface should
  stay on Views. See the Compose section below.
- **Inter as the type system.** Would suit the glass look, but the font should
  not change with the surface style, so it is an app-wide decision rather than
  a glass one.
- **Pluggable inline search results.** Kagi is the only option today because
  inline results need a keyed search API. Brave is the strongest addition;
  DuckDuckGo genuinely cannot do it. See the search section below.
- **Collapsing the four sheet behaviors.** Worth around 400 lines, but it is the
  drag core with no tests, so it goes last and wants the emulator in hand.
- **Tests.** Deliberately deferred until the migration is finished. There is one
  unit test today, for the fuzzy matcher, written because the conversion touched
  its arithmetic.
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

#### Can we get the wallpaper? No, and no permission fixes it

Glass has to sample what is behind it, and on the home screen that is the
wallpaper. The wallpaper is composited by the system behind a translucent
window, so it is in no view or Compose tree this app owns. The only way at it
are `WallpaperManager.getDrawable()` and `getWallpaperFile()`.

Retested on an API 37 emulator, four configurations, both entry points:

| Setup | `getDrawable()` | `getWallpaperFile()` |
| --- | --- | --- |
| No permission, not default home | SecurityException, READ_EXTERNAL_STORAGE denied | same |
| `READ_MEDIA_IMAGES` granted | same SecurityException | same |
| Default home app, no permission | same SecurityException | same |
| Default home app **and** `READ_MEDIA_IMAGES` granted | same SecurityException | same |

`dumpsys wallpaper` confirmed the wallpaper was a static `ImageWallpaper` in
every run, so this is not the live wallpaper exception.

It asks for `READ_EXTERNAL_STORAGE`, which an app targeting 33 or above cannot
hold. There is no combination that works. An earlier note here claimed
`READ_MEDIA_IMAGES` was sufficient; that was wrong, and the permission has been
taken back out of the manifest rather than left there asking for photo access
in exchange for nothing.

This is deliberate on Android's part. A wallpaper is often a personal photo,
so reading it was locked down. AOSP's own launcher tints from
`WallpaperManager.getWallpaperColors()`, which needs no permission and returns
three colours rather than pixels.

To be exact about what this does and does not rule out, because it is easy to
read it as "the library cannot refract": the library refracts fine, and does it
in this app already. Refraction needs source pixels, and the library takes them
from a canvas the app draws (`rememberCanvasBackdrop`) or from a layer captured
off the app's own composables (`layerBackdrop`). That is also how every example
in the library's catalog works, and how the same effect works in Flutter's
`oc_liquid_glass`: what refracts is the app's own content. **The one thing that
is off the table is refracting the system wallpaper**, because those pixels are
not ours to read.

So:

- **Surfaces over the launcher's own content refract for real.** Anything
  inside the drawer, the settings sheets, the popup menus or the briefing sits
  on top of views this app draws, which can be captured and sampled directly.
  The theme dialog's glass chip and `LiquidToggleView` both do this today.
- **Home screen surfaces get everything except refraction.** Translucent body
  with a light gradient, specular rim, inner shadow and drop shadow, tinted
  from `getWallpaperColors()`, which is free. The launcher window is
  translucent, so the real wallpaper shows through the pane without a single
  pixel being read. It is glass that does not bend light.
- **The one route to full refraction on the home screen** is a wallpaper the
  app owns: the user picks an image inside Stario through the photo picker,
  which needs no permission, Stario draws it, and Stario can then sample what
  it drew. On the Evaluating list above.

`WallpaperSource` is kept as the record of this: it fails cleanly, every caller
treats null as no glass, and the class comment carries the finding so nobody
retries it.
#### The rest, if that trade is acceptable

1. A reusable `AbstractComposeView` rendering one glass surface, taking the
   wallpaper through a `CanvasBackdrop`, with the offset and zoom the launcher
   already applies so the glass lines up with what is on screen.
2. Put it behind the glance card first. Small, self-contained, already over the
   wallpaper and outside the sheet gesture machinery, so it either looks good
   or reverts in one commit.
3. A preference to switch it on, defaulting off, gated on the permission.
4. Then the search widget and the popup menus, if it holds up.

#### Where the switch goes

In the theme dialog, directly under Enforce dark mode. That dialog already has
the exact shape needed: `pop_up_theme.xml` holds a `force_dark_container` with
a `MaterialSwitch` above the colour list, and `ThemeDialog` already reads the
preference, writes it on toggle, and reports on dismiss whether anything
changed so the activity gets recreated. A glass switch is that same pattern a
second time:

1. A `LIQUID_GLASS` key beside `FORCE_DARK` in `ThemedActivity`.
2. A second container and switch in `pop_up_theme.xml`.
3. The same read, write and dismiss-diff in `ThemeDialog`, so flipping it
   recreates the activity the way a colour change already does.

Present it as two option chips rather than two switches, and draw each chip in
the style it selects: the Material one as a normal Material surface, the glass
one as actual liquid glass. The control then shows you what you are choosing
instead of describing it, the way a font picker renders each option in its own
typeface. It also sidesteps the problem with two switches wired to contradict
each other, which reads wrong because a switch looks independent. The chip row
below for colours is already this shape.

That also gives the glass code its first home: the chip has to render glass
before anything else does, so it doubles as the proof that the wallpaper
backdrop works, in a place where getting it wrong costs nothing.

**Surface style and colour stay separate axes, and that is the point.** Glass
changes how a surface is drawn; the theme still decides what colour it is.
The colour list keeps working untouched, Dynamic included, because
`ThemeRecyclerAdapter` already resolves colours through
`activity.getAttributeData(theme, attr)`. Tint the glass from the same
`colorPrimaryContainer` and `colorPrimary` attributes and Material You colours
carry over for free. Nothing about the twelve themes has to change.

Two things the switch has to handle honestly:

- It depends on `READ_MEDIA_IMAGES`. Ask when the switch is turned on, never
  before, and leave the switch off if it is refused rather than showing glass
  that cannot sample anything.
- It should be off by default until it has been looked at on a real device.

#### Components, and what Apple's guidance actually asks for

The backdrop library ships the effect and no components, by design. Its catalog
app does contain reference implementations (`LiquidButton`, `LiquidToggle`,
`LiquidSlider`, `LiquidBottomTabs`) under Apache-2.0, which is one-way
compatible with GPL-3.0 in our direction, so they can be adapted rather than
guessed at.

There is no mature component library to depend on instead. Searching the
`liquid-glass` Kotlin topic turns up one project at 3.5k stars, the effect
library already in use here, and everything offering ready-made controls sits
in single digits. Not worth a dependency; porting the reference implementations
is the safer route.

Three points from Apple's guidance shape where any of this belongs:

1. **Glass is for the floating control layer, never for content.** Lists,
   tables and media stay as they are. That rules out making settings rows
   glass, and points at the switches, buttons and sheet chrome instead.
2. **Do not stack glass on glass.** One pane per surface.
3. **Two variants.** Regular adapts to what is beneath it and filters more;
   Clear lets detail through. Regular is the right default here.

So far: `LiquidToggleView`, a switch whose thumb refracts its own track
through a layer backdrop, swaps blur for refraction while held, and swells on
press. It replaces the Material switch in the theme dialog when the style is
set to Liquid Glass, and the row around it stays exactly as it was, which is
what point 1 asks for.

#### What it does not replace

Sheets blur their background with `Window.setBackgroundBlurRadius`, which the
system composites and which blurs everything behind the window, wallpaper and
other apps included. Nothing in Compose can do that. Liquid glass is for the
surfaces sitting on top, not for the window behind them.

The library also ships no components by design. Buttons, toggles and cards are
yours to build, with their catalog app as reference.

### Why Kagi is the only inline-results option

The engine list (Google, DuckDuckGo, Brave, Kagi and the rest) only decides
which site a query opens in. That is unrelated to the Kagi setting, which does
something different: it shows web results **inside** the launcher, above the
app results. `WebAdapter` calls `kagi.com/api/v0/search` with an
`Authorization: Bot <key>` header and renders each hit as a url, title and
snippet.

So it needs a search API that returns structured JSON, and that is a much
shorter list than the engine picker suggests. When the setting is on,
`SearchEngine.getEngine()` also forces the engine to Kagi so that tapping
through lands somewhere consistent with the results shown.

**DuckDuckGo cannot replace it.** Its public API is the Instant Answer API,
which returns definitions, disambiguations and zero-click answers, not general
web results. There is no official DuckDuckGo web-search API. Worth stating
plainly because it is the obvious first suggestion and it does not work.

Candidates that could actually slot in:

| Provider | Key | Notes |
| --- | --- | --- |
| Kagi | `Authorization: Bot` | What exists today. Paid, no free tier |
| Brave Search API | `X-Subscription-Token` | Free tier, returns JSON web results with title, url and description. The strongest candidate |
| Google Custom Search JSON | key plus engine id | Small free daily quota, two values to configure rather than one |
| SearXNG | none | Self-hosted, JSON output, no key at all. Appealing for a personal build if you already run an instance |

The work to generalise it is well contained, because `WebAdapter` already
reduces everything to a url, a title and a snippet:

1. Pull the fetch and parse out of `WebAdapter` into a small interface, one
   implementation per provider.
2. Store a chosen provider alongside the key instead of assuming Kagi. The
   settings dialog already has the key field and the paste button, so it needs
   a provider picker above them and a hint that changes with the choice, since
   `KAGI_API_KEY` becomes provider-specific.
3. Drop the `getEngine()` special case that forces Kagi and instead force
   whichever provider is selected, or leave the engine alone entirely for
   providers like SearXNG that have no public site to open.

The string `Kagi servers reported unauthorized access. Is your API key valid?`
would want generalising too.

### On Inter

The app currently sets DM Sans everywhere through `android:fontFamily`, plus a
custom variable font for the clock face, which should stay whatever else
happens.

Inter on its own would be enough. It is the usual open stand-in for the Apple
system font and it covers the whole range through its weights, so a second
family is not needed and would probably hurt. Inter v4 also carries an optical
size axis, which is the thing that actually makes large text feel right; that
matters more here than adding another typeface, given how much of this UI is
big headings over wallpaper.

One caution about tying it to the glass switch: **the font should not change
with the surface style.** Flipping a glass toggle and having every label in the
app reflow is jarring, and it makes the two styles feel like two different
apps rather than one app with two skins. Pick one type system and keep it on
both. If Inter is the better fit, switch to it outright rather than only under
glass.

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

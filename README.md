<div align="center">

<br/>

# homebrew&#8209;weather

**The forecast, minus the noise.**

<sub>A pixel-art weather widget for Android that speaks up only when the sky has something to say.</sub>

<br/><br/>

<img src="docs/screenshot-widget.png" width="78%" alt="Homebrew Weather widget on a home screen: Estepona, 22°, a storm from 7 to 19, four-day forecast" />

<br/><br/>

[![Download APK](https://img.shields.io/badge/download-APK-33ff66?style=for-the-badge&labelColor=0a0f0a)](https://github.com/pi0trdotsys/Homebrew-Weather/releases/latest)
&nbsp;
[![beta](https://img.shields.io/github/v/release/pi0trdotsys/Homebrew-Weather?include_prereleases&style=for-the-badge&label=&color=0a0f0a&labelColor=0a0f0a)](https://github.com/pi0trdotsys/Homebrew-Weather/releases)

</div>

<br/>

```
01  ─  rain, pinned to its day      today up top, other days under their column: ▽ 7–19
02  ─  quiet on quiet days          a dry day is a number and four days, nothing more
03  ─  tap a day, see its hours     the widget opens the app on that day
04  ─  mornings and evenings        a brief at breakfast, tomorrow at dinner
05  ─  rain heads-up, both ends     before it starts, and before a long spell stops
06  ─  fits whatever you give it    any size; a calmer day gets bigger type, not gaps
07  ─  your language, your tone     polski · english  ·  clean · sigma · rude
08  ─  how much, not just whether   "downpour 15–19" or "drizzle", with the millimetres
09  ─  warnings are not jokes       storm, hail, gale, extreme heat: plain and immediate
10  ─  one widget, several places   tap the city name to switch: home, work, anywhere
11  ─  a line for the next 24 hours temperature and rain on one curve (full density)
12  ─  the sun, when it matters     "sunset in 40 min", only in that last hour
13  ─  a tip or a fact, not a joke  well-being tips, or what happened on this day
14  ─  a map of the city behind it  terrain contours, or a skyline on flat land (per widget)
```

<br/>

<div align="center">

### Get it

</div>

1. Grab the **APK** from the [latest release](https://github.com/pi0trdotsys/Homebrew-Weather/releases/latest) and open it on your phone.
2. Long-press your home screen → **Widgets** → **Homebrew Weather**.
3. Pick a city and a density (*minimal*, *standard* or *full*). That's it. It refreshes on its own.

Tap the widget to open the full forecast for that city, or tap a day to see its hours.
To keep more than one place on a widget, open its setup, switch on *add as another place*
and pick another city; from then on tapping the city name switches between them (the
**≡** next to it opens the setup).
Can't find the widget picker? **./settings** has an *add widget* button that asks your launcher
to pin one. The widget's setup also offers a *Material You* colour theme (Android 12+), a *city map* background
(terrain contours around the city, or a pixel skyline where the land is flat), and
**./settings** a *high contrast* option for the dim text, in the app and on the widget.
Language, brief and evening times, alert thresholds, tone and what the footer says (a joke, a
well-being tip, or an event from this day in history) are under **./settings**,
along with a button that sends one of each notification so you can see them right away.

> **Updating from beta.11 or earlier?** Uninstall the old version once, then install:
> beta.12 is signed with a new key, and Android won't replace an app signed with a
> different one. From beta.12 onwards, new versions install right over the top and your
> widgets stay put.

<br/>

<div align="center">

<sub>

**private by default**: no account, no ads, no tracking, no server of ours  
weather straight from [Open-Meteo](https://open-meteo.com/) · location is optional, a city name works too  
polski or english, your pick · still in beta, so rough edges are expected

<br/>

[for developers →](docs/DEVELOPMENT.md)

</sub>

</div>

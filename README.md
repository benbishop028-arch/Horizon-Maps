# 🧭 Horizon Nav

**Free, ad-free navigation for Android head units and Android Auto.**

Horizon Nav is a 3D navigation app made for car screens. No ads, no tracking, no subscription. It's made by one person in their spare time and shaped by feedback from the community.

[⬇️ Download the latest version](https://github.com/benbishop028-arch/Horizon-Maps/releases/tag/app) · [💙 Support development](https://www.paypal.me/BBishop462) · [🐞 Report a problem](https://github.com/benbishop028-arch/Horizon-Maps/issues)

<!-- Add a few screenshots here, for example: 

![Navigating](docs/navigating.png)

 -->

---

## ✨ Features

### 🗺️ The map
- 🏙️ Smooth 3D map with buildings
- 🚗 3D car model as your locator (saloon, hatchback, estate, SUV, pickup, van or sports), or an arrow, or your own picture
- 🔄 2D / 3D view that's remembered
- 🔍 Set your own default zoom for when you're not navigating
- 🌙 Day, night and auto themes, plus satellite view
- 🚦 Traffic overview when you're stopped (can be switched off)
- 🛣️ Junction close-ups at exits and slip roads

### 🧭 Navigation
- ⏱️ Routes with live traffic
- 📴 Offline maps and offline routing for many countries, with several areas at once
- 🛣️ Lane guidance on the turn card, with the lanes to use lit on the map
- 🚨 Speed limits, speed camera and average-speed zone warnings, stop sign warnings
- 🔋 Electric car planning: battery, charge at arrival and charging stops
- ⛽ Live fuel prices in France, Austria and Germany, plus E85
- 🚫 Avoid tolls, motorways or ferries, or tolls only in chosen countries
- 🔊 Voice guidance, with or without road names; km/h or mph
- ☕ One-tap stops along the route: fuel, coffee and so on
- ⚠️ Report police, crashes and hazards

### 📱 Phone and car
- 📤 Send a destination from your phone to the head unit
- 🅿️ Remembers where you parked after Android Auto disconnects
- 🚘 Android Auto support, with the same map page as the phone app

---

## 📥 Install

1. Open the [latest release](https://github.com/benbishop028-arch/Horizon-Maps/releases/tag/app) and download the APK.
2. Install it on your head unit or phone (you may need to allow installs from unknown sources).
3. Open the app and follow the setup screens.

The app updates itself from the same release page.

### 🚘 Android Auto
Android Auto only lists unofficial apps under certain conditions. If Horizon Nav doesn't appear, install it through **AAEnabler**. The app shows a note about this. If you installed through AAEnabler, updates are saved to your Downloads folder to install with AAEnabler again.

---

## 🔑 Free keys for online routes and search

The app has no keys built in. Add your own once, in **Settings > Keys & accounts**. You need **one** for online routes and search (or use offline maps instead). Having **both** gives the best results.

### TomTom key (routes, search and traffic)
1. Sign up for a free account at [developer.tomtom.com](https://developer.tomtom.com).
2. Open your dashboard and create an API key.
3. Make sure Routing and Search are ticked for it.
4. Copy the key into the app.

### Mapbox token (map, search and lane data)
1. Sign up for a free account at [account.mapbox.com](https://account.mapbox.com).
2. Copy your **default public token** (it starts with `pk.`).
3. Paste it into the app.

> If search says the servers are busy, adding a Mapbox token usually fixes it.

---

## 🛣️ About lane guidance

Lane guidance is the hardest part to get right. The lane list comes from the routing service, and the lanes drawn on the map come from Mapbox's map data, with no direct link between them. So:

- At junctions with lane guidance, an overhead lane sign shows the lanes to use.
- Where the app can read Mapbox's painted lanes and they match, the lit lanes are painted on the road with the route line through them.
- With a Mapbox token, a TomTom route uses Mapbox's lane list where it can, to suit the painted lanes better.

Turn on **Demo mode** to see a short message saying whether real lanes were found at a junction.

---

## 🐞 Something not working?

- Use **Settings > About & backup** to send a **feedback report**. It includes your unit, the app version and what the app last saw, which helps a lot.
- If your head unit restarts while the app is open, the app notices and steps down to safer drawing on its own. A report from that unit helps me fix it properly.
- Open an [issue](https://github.com/benbishop028-arch/Horizon-Maps/issues) with a screenshot if you can.

See [ROADMAP.md](ROADMAP.md) for what's planned and what's been fixed, and [CHANGELOG.md](CHANGELOG.md) for what changed in each version.

---

## 💙 Support the project

Horizon Nav is free and always will be. If it's useful to you, a small donation helps keep it going: [paypal.me/BBishop462](https://www.paypal.me/BBishop462). There's also a donate button in **Settings > About**.

---

## 🛠️ For developers

The Android Studio project is in this repository, so changes can be reviewed as ordinary Git diffs.

- `app/` is the Android application. `build.gradle.kts` and `settings.gradle.kts` are the root Gradle configuration.
- To build locally, open the root folder in Android Studio and let Gradle sync, then build the `app` configuration. The Gradle files define the SDK and dependency requirements.
- Releases are built with the **Build Horizon Nav** GitHub Actions workflow. Ticking **publish** creates a versioned release and refreshes the fixed `app` release used by the in-app updater.
- Signing keys, API keys and local configuration are not in version control. Don't commit credentials, and keep the application ID and signing configuration unchanged unless there's a planned migration.
- See [ARCHITECTURE.md](ARCHITECTURE.md) for how the code is laid out.# Horizon-Maps

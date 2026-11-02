# Horizon Nav

Free sat nav for Android car head units, that also runs on Android Auto. No ads, no account and no tracking. Made by one person in their spare time, and a lot of it comes from your feedback here and on Reddit.

⬇️ **[Download the latest version](https://github.com/benbishop028-arch/Horizon-Maps/releases/tag/app)**: install the APK, and the app updates itself from then on.

💙 **[Support development](https://www.paypal.me/BBishop462)**: totally optional. The app stays free.

---

## Features

### 🗺️ The map
- Smooth 3D map with buildings, landmarks and trees, and optional 3D hills
- Your locator: a 3D car (saloon, hatchback, estate, SUV, pickup, van or sports) in any colour, an arrow, or your own picture
- 2D / 3D view, remembered between drives
- Day, night, auto and headlight-linked themes, plus satellite view
- Your zoom is remembered, and you can set your own default
- Traffic overview when you're stopped (can be switched off)
- Junction close-ups at exits and slip roads

### 🧭 Navigation
- Routes with live traffic. With both keys added, new routes are asked of TomTom and Mapbox, and the quicker one is offered first
- Offline maps and offline routing for every country, several areas at once
- Lane guidance on the turn card, plus a lane sign over the road at junctions
- Speed limits, speed cameras, average-speed zones and stop signs, with spoken warnings
- Faster-route suggestions while you drive
- Avoid tolls, motorways or ferries, or tolls only in chosen countries
- Electric car planning: battery level, charge at arrival and charging stops
- Live fuel prices in the UK, France, Austria and Germany, plus E85
- One-tap stops along the route: fuel, charging, coffee, food, services and toilets
- Report police, crashes, hazards, broken-down cars and mobile cameras, on the head unit, phone or Android Auto
- Voice guidance with or without road names, km/h or mph, 12 or 24-hour clock

### 📱 Phone and car
- Full Android Auto support, with the same map page as the app
- Send a destination from your phone to the head unit
- Remembers where you parked when Android Auto disconnects
- Resumes your trip if the head unit restarts mid-journey

---

## Installing

### On a head unit or phone
1. Download the APK from the [download page](https://github.com/benbishop028-arch/Horizon-Maps/releases/tag/app) and open it.
2. Allow installing from unknown sources if it asks.
3. Open Horizon Nav and follow the setup: location permission, units, and your keys (see below).
4. For no-signal driving, download your area in **Settings › Offline maps**.

### On Android Auto
Android Auto only lists apps from outside the Play Store once you allow it:

1. Open Android Auto's settings on your phone (Settings › Connected devices › Android Auto, or the Android Auto app).
2. Scroll to the bottom and tap **Version** about 10 times until developer settings are on.
3. Open the ⋮ menu › **Developer settings**, and tick **Unknown sources**.
4. Back in Android Auto's settings, open **Customise launcher** and make sure **Horizon Nav** is ticked.

**Still not showing?** On some phones Android Auto ignores apps installed straight from the APK. Install it with one of these instead:

- **[KingInstaller](https://github.com/fcaronte/KingInstaller/releases)**: open the Horizon Nav APK with it. This has worked for most people who couldn't get it to show.
- **AAEnabler**: open the APK with it. If you use AAEnabler, update through it too. Updating from inside the app removes Horizon Nav from Android Auto, so the app saves new versions to your Downloads folder for AAEnabler to install instead.

Phones running **GrapheneOS, LineageOS** or other versions of Android without Google's own apps may not be able to show it on Android Auto at all. It still works on the phone itself.

---

## 🔑 Your free keys: TomTom and Mapbox

The app has no keys built in, so nothing goes through a server of mine. You add your own once, in **Settings › Keys & accounts**.

**Mapbox** (needed for the map)
1. Sign up at [account.mapbox.com](https://account.mapbox.com).
2. Open **Tokens** and tap the **copy button** next to your **Default public token** (it starts `pk.`).
3. Paste it into the Mapbox box. Don't type it in by hand: one wrong character and Mapbox refuses it (error 401).

**TomTom** (recommended: the best address search, traffic, and a second opinion on routes)
1. Sign up at [developer.tomtom.com](https://developer.tomtom.com). On a phone, switching the browser to desktop view helps.
2. Open your dashboard and copy your API key (the default one is fine, with Routing and Search ticked).
3. Paste it into the TomTom box.

**Good to know**
- **Put both in.** Search is best with both, and new routes are compared between them.
- **Cost:** both have free allowances that one person's driving won't use up. Mapbox may ask for card details when you sign up, but you're only charged if you go over the free allowance.
- **One key on several devices** (phone and head unit) is fine.

---

## 🔒 Privacy: what leaves the car

No account, no ads, no tracking, and no Horizon Nav server. The app talks directly to these services, and only for the feature you're using:

| Service | What it gets | What for |
|---|---|---|
| Mapbox | Your position and destination | The map, routes, search, traffic, speed limits |
| TomTom (with your key) | Your position and destination | Routes, search, traffic, speed limits |
| OpenStreetMap services (Overpass, Nominatim, Photon) | The area around you | Speed cameras, speed limits, traffic lights, search |
| Open-Meteo, RainViewer | Your rough position | Weather and the rain radar (only when switched on) |
| Fuel price services (UK, France, Austria, Germany) | The area you're searching | Fuel prices |
| GitHub | Nothing about you | Checking for and downloading updates |
| ntfy.sh | The place you send, under your pairing code | "Send to car" from your phone (only if you pair a phone) |

**Stays on the device:** saved places, recent destinations, your own reports, the trip log and your settings.

**Works with no signal** (once you've downloaded an area): the map, routes, search for downloaded places, speed limits, speed cameras and traffic lights.

**Needs signal:** live traffic, online search, weather, fuel prices and updates.

---

## 🛠️ Troubleshooting

- **The APK download stops at 100% and never finishes:** this is the browser, not the app. Try another browser, or once it's installed, update from inside the app (Settings › About & backup).
- **Blank or black map:** check both keys are in Settings › Keys & accounts.
- **Mapbox "401":** the token isn't being accepted. Copy it again with the copy button. The app tells you what Mapbox says is wrong with it.
- **"No signal" all the time, even though there is signal:** check the head unit's **date and time**. When they're wrong, every secure connection fails and looks like no signal.
- **Laggy on a slower head unit:** in Settings, set Map detail to Lite or Lightest and lower the frame rate.
- **Spoken directions missing:** check your device's text-to-speech voice is installed. If you hear the beeps but not the voice, try Settings › Sound › Voice channel › Media.

---

## 💬 Feedback and bug reports

Bug reports, ideas and screenshots are very welcome, in [GitHub Issues](https://github.com/benbishop028-arch/Horizon-Maps/issues) or on Reddit.

The best way to report a problem: **Settings › About & backup › Feedback or a problem**. It sends a report with your app version and head unit details, which makes it much quicker to fix. Please include it in your issue.

⚠️ **Lane guidance** is the hardest part to get right. Some roads line up with the map's painted lanes and some don't, so tell me how it looks on yours.

See [CHANGELOG.md](CHANGELOG.md) for what's changed in each version.

Thanks for all the testing! 🙏

# Using Horizon Nav from other apps

For developers of launchers, dashboards and other apps that want to work with Horizon Nav: how to start navigation in it, and
how to show what it's doing. Everything here uses standard Android features. Horizon needs no extra setup or permission for it.

## Package names

| Edition | Package |
|---|---|
| GitHub (APK) | `com.headunit.nav.public` |
| Google Play (Horizon Maps) | `com.headunit.nav.play` |

Set the package on your intent (`intent.setPackage(...)`) to send it straight to Horizon. Without it, Android may ask the user
which navigation app to use.

## 1. Start navigation in Horizon

Horizon answers the same links as Google Maps. Coordinates open the route choice to that place; a name or address opens
Horizon's search with it filled in, so the user picks the right result.

### Go to coordinates

```kotlin
val uri = Uri.parse("google.navigation:q=51.5014,-0.1419")
startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.headunit.nav.public"))
```

Or with a name for the destination:

```kotlin
val uri = Uri.parse("geo:0,0?q=51.5014,-0.1419(Buckingham Palace)")
startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.headunit.nav.public"))
```

### Search for a place or address

```kotlin
val uri = Uri.parse("geo:0,0?q=" + Uri.encode("10 Downing Street, London"))
startActivity(Intent(Intent.ACTION_VIEW, uri).setPackage("com.headunit.nav.public"))
```

`google.navigation:q=<address>` works the same way.

### Add a stop on the way

While Horizon is already navigating, add `intent=add_a_stop` to a `geo:` link and the place becomes the next stop, keeping
the destination:

```kotlin
val uri = Uri.parse("geo:0,0?q=51.5007,-0.1246(Westminster)&intent=add_a_stop")
```

When Horizon isn't navigating, the place is treated as the destination.

### Also accepted

- `android.intent.action.NAVIGATE` with a `geo:` link (what Google Assistant and Gemini send).
- Google Maps web links: `https://maps.google.com/...`, `https://www.google.com/maps/...` and short `https://maps.app.goo.gl/...`
  links (a short link needs a data connection to open).
- `Intent.ACTION_SEND` with `text/plain`: a shared address, coordinates or a Google Maps link, such as a place shared from a
  messaging app.

### What happens next

- With coordinates, Horizon plans the route and shows the route choice. If the user has **Auto-start route countdown** on
  (Settings › Routes), navigation starts by itself when the countdown ends; otherwise the user taps Go.
- With a name or address, Horizon shows its search results for it.
- If Android Auto is connected, the same route choice is also offered on the car screen.

## 2. Show what Horizon is doing

While navigating, Horizon keeps an ongoing notification showing the next turn, as long as the user has
**Next turn in the notification** on (Settings › Turn card & lanes, on by default). A launcher with notification access (a
`NotificationListenerService`) can read it to show its own turn panel.

| Where | What it holds | Example |
|---|---|---|
| Package | Horizon's package (see above) | `com.headunit.nav.public` |
| `category` | Always `Notification.CATEGORY_NAVIGATION` | `navigation` |
| `EXTRA_TITLE` | Distance to the next turn · what to do | `300 m · Turn left` |
| `EXTRA_TEXT` | The road to turn onto (or the sign's destination) | `High Street` |
| `EXTRA_SUB_TEXT` | Arrival time, when known | `Arrive 14:32` |
| Large icon | The turn arrow as a picture | (bitmap) |
| Small icon | The turn arrow as an icon | (icon) |

Notes:

- The text is in the user's chosen language and units (miles or km, 12 or 24-hour clock), exactly as Horizon shows it, so show
  it as it comes rather than parsing it.
- It's updated when the distance shown, the turn, the road or the arrival time changes, at most about once a second.
- With no next turn to show (or the setting off), the notification says only that Horizon is navigating. When the route ends,
  it goes away, or, while Android Auto is connected, goes back to a plain notification without a turn (check `category` and
  whether the title has a distance, rather than only whether the notification exists).
- Tapping the notification opens Horizon.

## 3. What isn't possible

- **Embedding Horizon's live map inside your app.** Android only lets system apps show another app's screen inside their own
  (Android's `ActivityView` / `TaskView`). A normal launcher can't. The nearest you can get is opening Horizon in Android's own
  split-screen or a freeform window, if the device supports them.
- **A programming interface (API or bound service) for routes or the map.** Horizon doesn't offer one. Use the links in
  section 1 to start navigation, and the notification in section 2 to follow it.

If you need something that isn't covered here, open an issue on GitHub saying what your app would do with it.

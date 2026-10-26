# Shared-version setup and widget controls

This is a development snapshot for a future shared build. It was tested as a separate preview application; it does not replace the original personal TV installation. Compatibility is still verified only on the development Insignia Fire TV.

## First launch

1. Choose an optional greeting name.
2. Optionally search for a city or postal code. Select the intended city from a bounded list with region/country labels, then choose its timezone or keep the TV timezone.
3. Choose which installed apps appear. Missing streaming apps are not inserted as store shortcuts.
4. Choose greeting, clock/date, and weather widgets, then Finish.

Counter-Strike is not mentioned in these steps and is off by default. It is available later through Settings → Widgets → Counter-Strike (optional).

Back returns to the preceding setup step. Draft name, city, app, and widget choices are committed only by Finish. Later leaves setup incomplete. Settings → Run setup again opens the flow with current choices; existing app order is preserved. City and time can also be edited separately, with Cancel leaving its previous value intact.

Skipping the city lets setup finish without an internet connection and defaults weather off. Users can add a city and enable weather later. The clock uses the TV timezone when no city is selected. The idle sky uses preset transitions until valid seasonal sun times are available.

## Widget behavior

Greeting, clock/date, and weather switches affect both the home and idle screens. The optional CS switch controls its entire section, including live cards, upcoming matches, and past matches. Its feed/client/workers are created only while enabled. Disabling it closes those workers and removes its remote-focus targets. An in-flight network operation may finish within its timeout; interrupted pagination cannot start another request.

The Home/app-return helper starts off. It is configured separately through its existing setting and ADB permission grant. Neither setup nor widget selection grants system permissions.

## City, weather, and local dates

City search sends the entered query to Open-Meteo's geocoding service. It uses no GPS/device-location permission, limits responses to 64 KiB and five choices, and validates coordinates and timezone IDs. Search is asynchronous and ignores responses after navigation/dismissal. Failure/empty states allow retry or skipping the city.

Weather and solar requests use the selected city, and temperature can be changed between Fahrenheit/Celsius in Weather settings. Weather can be hidden while solar times continue refreshing for the idle sky. Profile changes clear incompatible weather/solar caches and reject obsolete in-flight responses. The clock and optional CS daily boundaries use the selected timezone. Calendar-based midnight boundaries support 23- and 25-hour daylight-saving days; daily caches record the zone used for their freshness check.

Sources: [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api), [forecast documentation](https://open-meteo.com/en/docs). City data is based on GeoNames.

## Separate preview

Add `-Preview` to the existing Windows build command. It generates `build/QuietTV-preview.apk`, package `com.quiettv.preview`, label Quiet TV Preview, with separate application storage. Only the preview manifest enables Android debugging for local inspection. The normal build keeps the original application ID and is not installed over the personal version during these checks.

```powershell
adb -s YOUR_TV_IP:5555 install -r build/QuietTV-preview.apk
adb -s YOUR_TV_IP:5555 shell am start -n com.quiettv.preview/com.quiettv.home.MainActivity
```

Keep the optional Home/app-return helper off in a side-by-side preview. Generated APKs, development signing keys, and local setup data remain excluded from Git.

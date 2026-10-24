# Data sources and behavior

## Weather and seasonal sky

[Open-Meteo forecast API](https://open-meteo.com/en/docs) supplies temperature, weather code, and daily sunrise/sunset times. The selected city supplies coordinates and an IANA timezone through optional [Open-Meteo geocoding](https://open-meteo.com/en/docs/geocoding-api). Requests are user-triggered, bounded to five city results and 64 KiB, with five-second connection/read timeouts. No weather requests occur before a city is selected. The clock can instead follow the TV timezone; Weather settings offer Celsius/Fahrenheit. No device-location permission is requested. Requests normally refresh every 30 minutes while visible, with bounded responses and retries. Solar times are cached for up to 16 days; unavailable data falls back to preset sky times.

The moon, stars, clouds, and terrain are decorative Canvas graphics, not a live astronomical model.

## Counter-Strike

This widget is off by default, is absent from setup, and only constructs its feed/workers after being enabled in Settings → Widgets. The following behavior applies while enabled.

HLTV returned HTTP 403 during development. The app does not bypass its access controls or assume an HLTV public API exists. Instead, it reads BO3's publicly reachable website interface at `https://api.bo3.gg/api/v1/`. This interface is undocumented and has no stability guarantee. Changed structures and connection failures must be treated as unavailable information.

- Live status requires the source's `current` state plus time/completion checks. Upcoming matches are labeled as scheduled.
- `app/res/raw/cs_policy.json` controls event prominence, tier/prize thresholds, exclusions, included event IDs, card count, and refresh/expiry bounds. Default maximum: two cards, 120-second refresh, 180-second live expiry.
- Small team/event images are restricted to the provider image host, downloaded with size limits, decoded to bounded dimensions, and held in a bounded memory cache. Missing images use clean fallbacks.
- An `official` provider flag alone is insufficient: co-streamers may have it. `cs_broadcasters.json` contains reviewed organizer identities. Selection intersects those identities with the selected match's fresh stream list and prefers English. It never blindly substitutes a fixed organizer channel.
- YouTube metadata may be checked through oEmbed to verify the exact channel owner. Failure leaves the stream unverified. Twitch deep links are used when the native app can resolve them; browser fallback is available.
- Official identities do not independently prove what is on screen: match-to-stream association still relies on the provider being correct.
- Discovery runs off the UI thread. Polling stops while idle or in another app. No separate backend, paid data subscription, or discovery account is used.

Today's results accepts only source-confirmed `finished` matches with a valid finish date on the current local day. It backfills eligible prominent matches and remembers recently featured IDs. It does not infer a final from a disappearing card or a winning score. Final series scores are validated before naming a winner.

The finished archive is sorted by completion time, checked locally for date boundaries, and scanned at most five pages of 100 rows. Tested server date filters were ignored, so the implementation does not rely on them. Partial scans show incomplete checking. Limits: 64 daily finals, 128 recently featured IDs, and a 128 KiB normalized results cache. Results normally refresh every ten minutes while visible, also after a featured match disappears; user refresh has a 15-second minimum interval. Confirmed cached finals remain available after retrieval failures.

Organizer research links are retained beside each profile in `cs_broadcasters.json`. Useful primary references include [PGL](https://www.pglesports.com/), [ESL watch](https://esl.com/watch/), [BLAST](https://blast.tv/cs), and [Twitch mobile deep links](https://dev.twitch.tv/docs/mobile-deeplinks/).

The Upcoming matches list uses the same prominence policy and only source-upcoming matches with future start times today in the selected timezone. It refreshes every ten minutes while visible, with a 15-second user-refresh floor and failure backoff. Its sorted scan stops at tomorrow, capped at five pages of 100; retention is capped at 64 rows and a 128 KiB normalized private cache. Cached schedules expire after 30 minutes and are always filtered against the current date/time. Partial scans and connection failures have explicit status rows. Schedule work reuses the existing results worker and does not add an animation loop or background polling.

The home card's NEXT TODAY suggestion uses that same fresh upcoming list, its prominence rules, and its local-day boundaries. It never substitutes a match from a later date. Old feed-cache next entries are ignored, and there is no separate next-match network request.

## Govee

Optional light requests use Govee's cloud device API at `https://openapi.api.govee.com/router/api/v1/device/`. The integration is restricted to the H6097 and model-specific segment/scene behavior. Requests are spaced, app-color selection is debounced, and effects execute on the light controller rather than streaming animation frames from the TV. The smooth saved DIY effect expects a user-created effect named `slow`. Custom colors replace camera matching; restoring Video mode currently requires Govee Home.

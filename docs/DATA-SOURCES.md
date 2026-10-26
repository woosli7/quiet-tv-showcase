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

Past matches shows today plus the previous three local calendar dates, grouped by when a match finished. Left/Right changes dates within the list, Up/Down scrolls matches, OK opens the final score and Highlights options, and Menu refreshes. In the bottom row, Left/Right moves between Previous day, Next day, and Back. Only source-confirmed `finished` records with valid completion times are included; a disappearing live card or winning score does not imply a final. The same prominence rules and remembered featured IDs apply.

The archive is fetched asynchronously through the existing result worker, sorted by completion time with within-page and cross-page ordering checks. Pagination stops at the oldest retained midnight or five pages of 100 rows. Tested server date filters were ignored, so filtering happens locally. Backfilling finds eligible results even when the TV was off. There are no additional polling timers or requests for each date press; normal refresh remains ten minutes while visible, with a 15-second manual-refresh floor and failure backoff.

Limits are 64 finals per date, 256 across the four dates, 128 remembered featured IDs, and a 512 KiB normalized disk cache. A limited scan/retention cap is labeled incomplete, including after restart. Versioned coverage makes older today-only caches recheck the archive. Coverage expires after thirty minutes, local midnight, or a timezone change; an unchecked day is not presented as confirmed empty. Confirmed saved finals remain available after failures. Records outside the moving four-date window are pruned. Calendar boundaries account for seasonal 23/25-hour days.
Organizer research links are retained beside each profile in `cs_broadcasters.json`. Useful primary references include [PGL](https://www.pglesports.com/), [ESL watch](https://esl.com/watch/), [BLAST](https://blast.tv/cs), and [Twitch mobile deep links](https://dev.twitch.tv/docs/mobile-deeplinks/).

Upcoming Matches browses seven local calendar dates: today and the following six days. Only source-upcoming matches with future start times are included, under the same tournament prominence policy. Left/Right in the match list changes dates; in the bottom row it moves between buttons. Up/Down scrolls the selected day, OK opens match details, and Menu refreshes. Previous/Next day buttons and a hardware Back action are also available. Returning from a detail keeps the selected date.

The full window is fetched asynchronously through the existing schedule worker, normally every ten minutes while visible. Moving between dates reuses the cached window and does not issue a request for every key press. User refresh keeps its 15-second floor and failure backoff. Pagination stops after reaching the seven-day calendar boundary or five pages of 100 rows. At most 64 records per day and 256 in the window are retained, with a bounded 512 KiB cache. A limited scan or retention limit is labeled incomplete, including after cache restoration. Checked coverage expires after thirty minutes, at local-day rollover, or after a timezone change. Versioned coverage forces old today-only caches to refresh rather than implying future days are empty. Midnight and navigation boundaries use Calendar dates, including 23- and 25-hour daylight-saving days.

The home card's NEXT TODAY suggestion uses that same fresh upcoming list, its prominence rules, and its local-day boundaries. It never substitutes a match from a later date. Old feed-cache next entries are ignored, and there is no separate next-match network request.

### Team favorites

Team favorites are optional local preferences, initially empty. Up to 32 team IDs/names are stored in the app's private settings with a 16 KiB input bound. Provider team IDs take precedence, preventing separate teams with matching display names from colliding. Older ID-less cached matches use full normalized names and a few explicit aliases, including NAVI/Natus Vincere; substring matching is not used. Named rosters such as NAVI Junior remain separate. Favorites survive restarts and disabling/re-enabling the CS widget.

The live retrieval keeps up to 100 policy-eligible normalized current records, with a 256 KiB disk-cache bound. Favorites are promoted before applying the unchanged two-card cap; ties preserve the source's existing tournament ranking. The rendered ordering is cached until match data or favorite preferences change. Normal LIVE expiry, completion checks, prominence exclusions, and verified broadcast selection remain in force. Favorites never make an upcoming, expired, or excluded match LIVE.

Upcoming and Past match lists retain chronological date order, decorate followed teams with amber stars, and offer separate persistent All matches / My teams controls. Filtering uses already-loaded records; it issues no additional match requests. Empty favorites, an empty filtered date, and retrieval failures have distinct states. Saved favorites remain manageable even when their teams are absent from the current dates. The picker uses teams from cached/current matches and saved favorites, bounded to 96 choices; it does not call a separate team-search API.

Team controls are available in scheduled/final match details and the live broadcast alternatives menu. Highlights and match-page actions remain available. Date arrows apply to the list; footer arrows stay among enabled bottom buttons, including at the row ends. Returning from details restores list focus and selection. There are no new background workers, timers, accounts, notifications, or light effects for this feature.

### Match highlights

Past matches → a match score → Highlights checks only the organizer highlights channels reviewed in `app/res/raw/cs_highlights.json`: [PGL Counter-Strike Highlights](https://www.youtube.com/@Counter_Strike_Highlights), [ESL Counter-Strike Highlights](https://www.youtube.com/@ESLCSHighlights), and [BLAST Premier CS2 Highlights](https://www.youtube.com/@BLASTPremierHighlights). Rules and team aliases are editable without changing the lookup code. Other organizers have an explicit match-page fallback.

YouTube's public Atom feeds returned HTTP 404 during testing. The resolver instead reads the public channel search page and up to two candidate watch pages, off the UI thread and only on request. These page structures are undocumented and may change; blocked requests, consent/login screens, missing fields, and changed structures fail closed to a labeled channel-search fallback. No private YouTube API, API key, account, external backend, or playback extraction is used.

Discovery checks both team names with word boundaries and reviewed aliases, event words/year, the exact reviewed channel ID in page and video metadata, playable status, an explicit non-live state, video length, and an exact publication time near the confirmed finish time. Shorts, livestream archives, other events/years/opponents, and unverified owners are excluded. Matching uses upload metadata rather than a provider match ID; unusually delayed uploads, repeated pairings, renamed events, or title changes can require manual channel search. An empty check means no matching upload was found, not proof the organizer has not uploaded one.

Requests are bounded to three pages, 2 MiB per decompressed page, four-second connection/read timeouts, and a fifteen-second lookup budget. Cancellation prevents a later launch. At most two verified videos and 24 lookup results are cached in memory: ten minutes for a hit, two minutes for no match, one minute for a failed check. There is no highlights polling or persistent video/page cache. A direct video prefers the installed YouTube app; official-channel search explicitly opens in the browser. Native playback retains normal YouTube navigation and the existing launcher return helper.

## Govee

Optional light requests use Govee's cloud device API at `https://openapi.api.govee.com/router/api/v1/device/`. The integration is restricted to the H6097 and model-specific segment/scene behavior. Requests are spaced, app-color selection is debounced, and effects execute on the light controller rather than streaming animation frames from the TV. The smooth saved DIY effect expects a user-created effect named `slow`. When App colors is enabled, entering the Counter-Strike section uses its warm amber accent; Left or Back restores the selected app color. These transitions reuse the same debounced request path and brightness setting. Custom colors replace camera matching; restoring Video mode currently requires Govee Home.

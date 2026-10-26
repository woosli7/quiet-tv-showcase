# Validation

Development testing used an Insignia NS-50F301NA24 running Android 9 / Fire OS 7 with 2 GB RAM. Compatibility with other models and firmware versions is not established.

## Feature checks

- The CS development baseline passed 130 Android-runtime checks: 71 live-status/filter/broadcast checks, 34 daily-result checks, and 25 schedule checks. Provider fixtures and raw device logs are not distributed here.
- Shared setup passed 23 profile checks on the TV runtime, covering city parsing and bounds, timezone validation, selected-city persistence, malformed/empty search results, bounded choices, solar conversion, cache timezone changes, and 23/25-hour daylight-saving days. See `tests/ProfileChecks.java`.
- The 71 live-match/broadcast checks and 34 daily-result checks were rerun against the shared classes; all 105 passed.
- The next-card correction passed 14 focused checks for each development variant: Saturday versus Tuesday, earliest eligible match today, 30-minute cache expiry, clock rollback, missing cache, start/midnight boundaries, source status, prominence, empty state, and agreement with Upcoming Matches. See `tests/CsNextChecks.java`.

On-device checks covered real PGL Twitch playback, app return, remote focus, schedule/result list and detail navigation, nested Back behavior, font switching, idle previews, and preference persistence after force-stop/reopen. The next card was verified to omit a later-date match, while Upcoming Matches correctly reported no more featured matches that day.

## Setup and demonstration captures

Setup was completed with a searched example city and without a city or name. All four setup pages omit Counter-Strike. Skipping the city leaves weather off, follows the TV timezone, and creates no CS client/cache. City disambiguation, Back from widgets to apps, saved choices, and clock/weather/CS toggles were checked on the TV. Setup does not grant log-reading, overlay, or lighting permissions.

All nine showcase images were reviewed as demonstration captures. They use DEMO and the example city Paris, with no account information, home location, network details, or device identifiers. Sunrise/sunset/night use the existing sky-preview controls; the clock retains the actual capture time. The optional results capture uses source-reported match data. Capture testing used a separate preview package and storage; that package was removed afterward.

This repository begins with a fresh history. It contains no older development screenshots, private configuration, signing keys, API keys, raw logs, APKs, or rollback backups. Normal and preview builds compile and verify using the documented build script.

## Remaining limits

- Only one TV model/firmware combination has been tested. Home/log routing and remote shortcuts vary across Fire OS versions.
- Live PGL Twitch playback was checked. ESL/BLAST selection and YouTube ownership were checked in logic; playback from every organizer/platform was not available.
- Midnight rollover, failure handling, and daylight-saving boundaries were checked in logic; the TV clock and network were not changed for those checks. An actual city-search failure and retry were observed.
- BO3's interface is undocumented and may require maintenance after source changes.
- Govee scenes are model-specific, and general lighting onboarding is unfinished.
- The optional device-specific DIRECTV shortcut companion is not distributed. Night mode remains available in Settings.


## Seven-day upcoming navigation

35 deterministic upcoming-day checks and the 14 existing NEXT TODAY checks passed on the TV runtime for both source variants. Five additional shared-zone checks cover spring/fall day lengths, seven-day horizons, and date navigation. They verify day separation, Tuesday browsing from Saturday, empty days, navigation bounds, start/midnight/month/year boundaries, cache migration and expiry, timezone invalidation, prominence filtering, retention limits, pagination, and source failure handling. The home suggestion remains today-only. Both app variants build successfully. See tests/CsScheduleDaysChecks.java and tests/CsScheduleZoneChecks.java.

On the TV, verified Tomorrow and Tuesday listings with real provider matches, scrolling to a fifth row, full-date match details, Back retaining Tuesday and the selected match, and first/last-day bounds with the appropriate day button disabled. Menu refreshed the schedule without opening launcher settings. The update was installed without clearing application data, and the previous APK/source were saved for rollback. Device test captures remain local; no additional personal screenshots were added to the showcase.

Footer-navigation correction: verified on the TV that Left/Right moves between the bottom buttons without changing the date, activating Next day retains button focus, and Back closes the panel. Left/Right still changes dates when the match list has focus. Disabled boundary buttons are skipped; activating a day button that becomes disabled moves focus to Back.

## Match highlights

35 deterministic matching/trust/parser checks passed on the Fire TV runtime, including real public PGL search and watch-page fixtures. They cover organizer routing, aliases, event/year/opponent boundaries, channel impersonation, live/premiere/short/unavailable rejection, exact publication times, unfinished matches, escaped page data, and changed-page failure. See tests/CsHighlightsChecks.java. Both personal and shared apps build successfully.

On the production TV, selecting a completed FURIA–Natus Vincere score showed Highlights beside Open match page and Back, then opened the matching official PGL highlights in the installed YouTube app. Home returned through the existing helper. Cancelling a different lookup restored match details; Back restored the results list with the same match selected. Original app data and rollback APKs were retained. ESL/BLAST routing is covered by checks, but current-match playback for those organizers and the live browser fallback were not tested on this TV. Raw pages and device screenshots remain local and are not included in the showcase.

## Rolling past matches

37 past-match checks passed on the TV runtime for each development variant, including a real provider page covering multiple dates. They cover day grouping, finish-day assignment, date bounds, midnight/month/year rollover, source status, saved-final preservation, legacy cache migration, timezone/expiry/partial coverage, sorting/pagination, filtering, deduplication, and memory/disk retention limits. Five shared-zone checks cover spring/fall calendar lengths and browsing. The 35 upcoming-day and 14 NEXT TODAY checks also passed for each variant, plus the five existing shared upcoming-zone checks. Both app variants build successfully. See tests/CsPastChecks.java and tests/CsPastZoneChecks.java.

On the production TV, checked Today, Yesterday, the oldest date, an empty date, final-score details with Highlights, and Back retaining Yesterday with the same match selected. First/last date buttons were disabled at their bounds. Footer Left/Right moved focus to Back without changing the date; selecting Next day retained footer focus. Hardware Back closed the panel normally. The update retained app data and a pre-change APK/source snapshot. Failure and rollover cases were checked deterministically; TV network/time settings were not altered.

Counter-Strike focus background: the selected app's glow is replaced by a dedicated warm amber gradient centered behind the CS panel. Inactive app labels remain muted; Left restores the remembered app, its logo, and its brand glow. The existing background-glow setting still controls intensity/off, and fixed appearance themes retain their chosen tint. This reuses the existing cached gradient draw without animations, polling, workers, or additional background services. Both variants build. On the TV, checked entry from YouTube and Spotify, identical CS screenshots across those entries, and restoration of Spotify's green highlight/background on return. Personal captures remain local; the pre-change APK/source were saved.

Past-matches button label: replaced the finals count with the four-date browsing range, derived from CsResults.DAYS and the selected local timezone. The label is cached until the local date or timezone changes. Both app variants build; the installed TV screenshot shows OCT 22 – OCT 25 fitting on the existing button. This presentation-only change does not alter result retrieval or navigation.

Counter-Strike light matching: App colors now sends the shared CS amber accent on entry to the CS section and restores the remembered app color on Left or hardware Back. Idle dimming and the App colors opt-in guard run first; repeated Right presses do not resubmit the same focus transition. Uses the existing 500 ms debounce, request spacing, brightness preference, and controller. Both app variants build. On the TV, Govee state confirmed amber RGB 15314290, YouTube red 16725044 after Left, Spotify green 2021216, amber again from Spotify, and green after Back, all at the existing 45% brightness. No credentials or device identifiers are included in this source package.

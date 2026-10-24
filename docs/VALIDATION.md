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

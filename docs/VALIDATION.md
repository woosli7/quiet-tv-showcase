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

## Favorite CS teams

69 favorite-team checks passed on the Fire TV runtime: default-empty preferences, follow/unfollow/revision changes, persistence, team-ID renames and collisions, legacy name aliases, academy/substrings, invalid and oversized input, a 32-team bound, cached team IDs, manager deduplication, stable two-card priority, local filtering, and unchanged prominence/LIVE expiry. The 36 deterministic past-match, 35 upcoming-day, 14 NEXT TODAY, and 35 highlights checks also passed with the extended match model. See tests/CsFavoriteChecks.java. Both personal and shared builds compile successfully.

On the production TV, checked follow/unfollow from final-score details, amber stars, My teams filtering in Past matches and the upcoming Tomorrow list, no-favorites/empty-date states, persistence after force-stop/reopen, detail-title updates, and Back retaining list focus/selection. Footer Right at Back stays in the footer rather than jumping to the filter. Lists were kept inside TV margins. The temporary test favorite was removed and both lists restored to All matches. Actual simultaneous live events were unavailable during UI testing; live priority was verified with deterministic match data, including promotion from beyond the original two cards. This update preserved application data and the pre-change APK/source backup.


## Match-list opening defaults

Both builds compile. On the production TV, verified Past and Upcoming each open on All matches, can switch to My teams, retain that choice through match details and Back, and return to All matches after closing and reopening. Upcoming also retained My teams while moving to Tomorrow. Existing followed teams and stars remained intact. Filters now use in-memory session fields, avoiding preference writes; saved team favorites remain unchanged. The update preserved application data and a pre-change APK/source snapshot.

## Full labels and readable rows

76 deterministic label-fitting checks passed on the Fire TV runtime. They cover complete character preservation, long app/team/event/city/error labels, one- and two-line width/height bounds, unchanged short labels, and Unicode code-point boundaries. See tests/ReadableTextChecks.java. Both personal and shared builds compile.

On the production TV, the previously shortened upcoming empty-state message now wraps in full. Checked populated Upcoming and Past lists, tournament and date/status metadata on separate lines, scrolling to later/recycled rows, match details and Back, and native settings/app-choice menus with their action buttons inside the screen margins. Existing followed-team stars and app selections remained intact. Native rows grow to fit text; list viewports remain bounded and scrollable. Dialog headings use a compact readable size and can wrap. No continuous text animation, network request, timer, or service was added. Home app labels and compact CS/weather fields retain all words using a bounded 128-entry layout cache. Live-card long-label fitting was checked deterministically; no live event was available for a fresh broadcast-card visual check. Shared first-time setup uses the same row wrapping but was not rerun on the personalized production installation. The pre-change APK/source and older rollback versions remain saved.
## Quiet TV menu styling

Menus now use solid dark panels, a subtle border, compact monospace headings and rows, amber selection outlines, and smaller outlined action buttons. Match-list titles stay short; the selected date and day position are shown separately above the list. Descriptive messages are left-aligned, and Team favorites is a compact secondary action. Full-label wrapping and bounded scrolling are retained. Native dialog/list selection, checkbox state, text entry, and existing remote handlers are reused. The styling adds no dependency, worker, polling, or animation loop; menu window animations are disabled.

Both personal and shared builds compile. The 76 readable-label checks passed again on the Fire TV runtime. On the production TV, verified settings navigation, opening/cancelling greeting entry, preserved app checkbox selections with Save/Back visible, Upcoming All matches/My teams, Tomorrow browsing, match details and Back, fresh-open reset to All matches, Past scores and the Highlights/Open match page/Back actions, and full text inside the panel. Device captures include the user's existing night-screen overlay and remain local. First-time setup renders its dynamic controls with the shared style; the full setup flow was not rerun on the personalized production installation. App data and the pre-redesign APK/source snapshot were retained.
Menu focus refinement: list selectors and row backgrounds are transparent, and action buttons retain the same neutral fill/border across focus states. Only the selected/focused text changes to amber; checked indicators keep their existing saved-state color. Both variants build. On the TV with the night filter off, checked the first and second settings rows: the amber text moves with the remote selection, without a filled or outlined selection bar. The earlier menu version and app data were preserved.
## Showcase capture refresh — October 26, 2026

Replaced all nine earlier showcase captures and added settings, upcoming matches, and match details, for 12 fresh 1920×1080 images from the current shared preview build. The separate preview used the generic name Demo and Paris for weather/time; its Home helper, light matching, and night filter were off. The main home/appearance/setup/widget images leave optional CS off; it was enabled only in preview for the match screenshots. Skies use the built-in Sunrise, Sunset, and Night previews while the real clock continues running. Match screenshots show real source data captured that day, not a fabricated live broadcast.

Visually reviewed all 12 images for the current text-only focus style, full readable labels, correct scene/screen, and absence of personal details. Validated every README image link and PNG dimensions. The capture-only preview app was removed afterward and the personal launcher was brought back to the foreground. No production preferences, app order, source behavior, credentials, or signing material were changed or published by this refresh. Earlier showcase images/README are backed up locally.
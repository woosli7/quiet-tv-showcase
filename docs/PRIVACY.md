# Permissions and privacy

This repository contains source, documentation, and selected launcher screenshots. It does not contain the original signing key, Govee API key, device configuration, personal network addresses, backups, or device logs.

The app does not include analytics. Optional city search sends the entered city/postal-code query to Open-Meteo and never requests GPS/device location. Setup can finish offline. Counter-Strike and the Home/app-return helper start off; no CS client/polling or log reader is started by first-time setup. Preferences, cached weather/solar data, and normalized CS caches are stored on the TV. Network requests go directly to the services listed in DATA-SOURCES.md. Those services receive ordinary request metadata such as the public IP address.

## Optional permissions

- **Internet:** weather, solar times, match discovery, team/event images, stream resolution, and optional Govee control.
- **Read logs:** the Home helper reads filtered foreground/Home events locally to detect routing events. It does not save or transmit log lines. This is a broad Android permission, granted separately through ADB; skip it if you do not want the helper.
- **Boot completed / foreground service:** support the optional Home/app-return helper and night overlay. A persistent notification identifies the service.
- **Display over other apps:** required only for the warm/dim filter. It can be revoked in Android settings. An overlay may not affect every protected video surface.

## Lights

No Govee account key is compiled into the app. The existing integration imports an encrypted setup payload and stores it in private no-backup storage, using a key held in Android Keystore. The repository intentionally does not include anyone's encrypted payload or private provisioning files. A general consumer setup screen has not been built, so lighting is a source-level integration rather than ready-to-use onboarding.

## Screenshots

All included screenshots are captures from a separate demonstration profile using the name DEMO and the example city Paris. They show no personal names, home location, account details, network settings, device identifiers, or credentials. Sky-preview captures keep the actual clock time; match information reflects the capture time. This showcase repository begins with a clean history and does not include the original development repository or its older screenshots.

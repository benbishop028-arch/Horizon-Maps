# Horizon Nav

Horizon Nav is an Android navigation/launcher project designed with in-car head-unit use in mind. The repository contains the Android Studio project source so changes can be reviewed as ordinary Git diffs rather than being distributed only as opaque ZIP files.

## Project layout

- `app/` — Android application source, resources, and app build configuration.
- `build.gradle.kts`, `settings.gradle.kts` — root Gradle configuration.
- `.gitignore` — excludes generated build files, local machine configuration, and signing credentials.

## Build

1. Install a compatible Android Studio release and its required Android SDK components.
2. Open this repository's root directory in Android Studio.
3. Allow Gradle sync to complete, then build/run the `app` configuration.

The exact SDK and dependency requirements are defined in the Gradle files. A local `local.properties` file may be created by Android Studio and should not be committed.

## Signing and secrets

Signing keys, keystore files, local properties, and secret configuration are intentionally excluded from version control. Configure release signing locally or through a secure CI secret mechanism. Do not commit credentials or API keys. The application ID and signing configuration should be kept unchanged unless a deliberate migration is planned.

## Development workflow

Make changes to the source files, review them with `git diff`, and commit focused changes with descriptive messages. Keep generated APKs/ZIPs as release artifacts rather than using them as substitutes for source commits. When running the GitHub Actions workflow, tick **Publish** to create an immutable versioned release (for history and notes) and refresh the fixed `app` release used by the in-app updater.

## Status and testing

This repository snapshot was prepared from the supplied project archive. It has not been built on an Android SDK toolchain as part of this packaging step, and no claim of device validation is made.

# Release Checklist

Gusic releases are shipped from `main` after the release candidate passes local checks and a basic device smoke test.

## Project Origin and Upstream Intent

Gusic is the renamed continuation of the former PixelPlayerOLX project. The original source repository is https://github.com/MiNoMinoaa/PixelPlayerOLX; this URL is retained as an origin reference. Gusic is independently maintained and is not an official PixelPlayerOSS, lx-music-mobile, or platform release; PixelPlayerOSS references are historical attribution only, not a Git upstream. Upstream licenses, notices, and attribution remain in force.

## Versioning

Version values live in `gradle.properties`:

```properties
APP_VERSION_NAME=26.10.05.001
APP_VERSION_CODE=522010001
```

The version name has four dot-separated segments: `YY.MM.DD.MOD`. The first three segments identify the formal release date, and `MOD` is a zero-padded modification number for changes made on that formal date. The first formal Gusic date is `26.10.05`; its first modification build is `26.10.05.001`. For a same-date modification, increment only the fourth segment (`.002`, `.003`, and so on); when the formal date changes, advance the date segments and reset the modification segment to `.001`.

`APP_VERSION_CODE` is derived as `YYMMDD * 2000 + MOD`. Stable and formal builds reserve modification slots `0` through `999`; therefore `26.10.05.001` becomes `261005 * 2000 + 1 = 522010001`, while the formal date's slot zero is `522010000`. Alpha CI builds reserve slots `1001` through `1999`: the workflow validates a run number from `1` through `999` and computes `YYMMDD * 2000 + 1000 + run number`. This keeps alpha artifacts separate from stable modifications and below Android's signed 32-bit version-code limit. Every published or CI code must be strictly greater than the last code distributed through the same release channel, must never be reused, and must be checked before publishing.

For every public release:

1. Update `APP_VERSION_NAME` using the four-segment rule above.
2. Set `APP_VERSION_CODE` to the matching monotonic numeric value and verify it is greater than the last published code.
3. Move the relevant `CHANGELOG.md` entries from `Unreleased` to the release version.
4. Tag the release as `v<APP_VERSION_NAME>`, for example `v26.10.05.001`.

## Required Local Checks

Run these before tagging:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false
```

The release-candidate command above is an official release check and requires the external Gusic release keystore described below. Do not add the signing-disable property to a release intended for distribution.

For a diagnostic build only (for example, to inspect packaging before signing is provisioned), use:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false -Ppixelplayer.disableReleaseSigning=true
```

A diagnostic build with signing disabled is not a publishable release and must not be uploaded or tagged as the official artifact.

For split APK artifacts, use:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=true
```

## Signing

Release signing is driven by an untracked `keystore.properties` file at the repository root and an external, dedicated Gusic release keystore. Keep both the keystore and credentials outside Git.

Supported keys:

```properties
storeFile=/absolute/or/repo-relative/path/to/gusic-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` must point to the dedicated Gusic release keystore; `storePassword`, `keyAlias`, and `keyPassword` are required. Official Release tasks run `validateReleaseSigningConfig` and fail when the keystore, properties file, or any required property is missing or invalid. A missing key is never treated as an unsigned publishable release.

CI workflows create temporary `gusic-ci.jks` keys with the `gusic-ci-key` alias for CI artifacts. Those keys are disposable CI credentials, not the official Gusic release key, and CI artifacts must not be used as official releases.

For a diagnostic build only, pass `-Ppixelplayer.disableReleaseSigning=true` even when local signing files exist. This opt-out is for packaging/debug diagnosis and does not make the resulting APK a releasable artifact.

## Device Smoke Test

Install the release candidate and verify:

1. First launch and setup complete.
2. Local library scan finds music.
3. Playback starts, pauses, resumes, skips, and survives backgrounding.
4. Full player opens and closes smoothly.
5. Widget controls still reach the playback service.
6. Navidrome and Jellyfin login screens open.
7. Backup export flow creates a file.

## Publishing

1. Ensure `main` is clean and pushed.
2. Create a tag: `git tag v<APP_VERSION_NAME>`.
3. Push the tag: `git push origin v<APP_VERSION_NAME>`.
4. Create a GitHub release from the tag.
5. Attach APK artifacts and paste the changelog section.

## Store Metadata

If you submit to an app store:

1. Update `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Check `PRIVACY.md` still matches the optional network services present in the app.
3. Create source archives from git, not from the working tree, so ignored local artifacts are excluded.

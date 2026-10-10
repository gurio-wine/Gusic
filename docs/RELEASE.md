# Release Checklist

Gusic releases are shipped from `main` after the release candidate passes local checks and a basic device smoke test.

## Project Origin and Upstream Intent

Gurio Wine maintains Gusic’s current code, features, and releases in the Gusic repository (https://github.com/gurio-wine/Gusic). Gusic continues the PixelPlayerOLX codebase; https://github.com/MiNoMinoaa/PixelPlayerOLX is retained only as an origin and historical source reference, not as current Gusic repository navigation or a release entry point. This project-origin statement does not assert that copyright or other rights were transferred. Gusic is not an official PixelPlayerOSS, lx-music-mobile, or platform release; PixelPlayerOSS references are historical attribution only, not a Git upstream. Upstream licenses, notices, and attribution remain in force.

## Versioning

Version values live in `gradle.properties`:

```properties
APP_VERSION_NAME=26.10.08.001
APP_VERSION_CODE=522016001
```

The stable version name is `YY.MM.DD`; its three segments identify the formal release date, and the omitted modification number is treated as `000`. Thus the formal date `26.10.08` has base code `261008 * 2000 + 000 = 522016000`; the current same-date follow-up `26.10.08.001` uses MOD `001` and code `522016001`. Optional fourth, zero-padded segments support further same-date follow-up builds (`26.10.08.002`, and so on); their code is `YYMMDD * 2000 + MOD`. When the formal date changes, advance the date segments and normally omit the modification segment for the initial stable build.

Alpha CI builds reserve slots `1001` through `1999`: the workflow calls `scripts/alpha_version.py`, which validates the stable name/code pair (including a real calendar date), validates a run number from `1` through `999`, and computes `YYMMDD * 2000 + 1000 + run number`. For example, alpha run 1 for `26.10.08` is `26.10.08-alpha.1` with code `522017001`. This keeps alpha artifacts separate from stable modifications and below Android's signed 32-bit version-code limit. Every published or CI code must be strictly greater than the last code distributed through the same release channel, must never be reused, and must be checked before publishing.

For every public release:

1. Set `APP_VERSION_NAME` to the release date (`YY.MM.DD`); use the optional `.MOD` suffix only for same-date follow-ups.
2. Set `APP_VERSION_CODE` to `YYMMDD * 2000 + MOD` (`MOD=000` when omitted) and verify it is greater than the last published code.
3. Move the relevant `CHANGELOG.md` entries from `Unreleased` to the release version.
4. Tag the release as `v<APP_VERSION_NAME>`, for example `v26.10.08.001`.

## APK Naming

Gradle output APKs are named `gusic-<abi>-[v<APP_VERSION_NAME>].apk` — for example `gusic-v8a-[v26.10.08.001].apk` (`v8a` = `arm64-v8a`, `v7a` = `armeabi-v7a`). A build without ABI splits omits the ABI segment: `gusic-[v26.10.08.001].apk`. The rule is implemented in `app/build.gradle.kts` (`androidComponents` output naming), so every variant, including CI alpha and PR builds, follows it; CI workflows upload and publish APKs under these names unchanged.

## Required Local Checks

Run these before tagging:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:compileDebugKotlin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:lintDebug
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:testDebugUnitTest
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleRelease -Ppixelplayer.enableAbiSplits=false
```

The release-candidate command above is the only supported Release packaging check and requires the external Gusic release keystore described below. Do not add the signing-disable property to a release intended for distribution.

An unsigned diagnostic `assembleRelease` command is not currently supported. Although `-Ppixelplayer.disableReleaseSigning=true` disables creation of the release signing configuration, Release tasks still depend on `validateReleaseSigningConfig` and therefore fail rather than producing a diagnostic APK. Do not use that property as a Release verification or packaging workaround. If signing is not provisioned, use a debug variant for general packaging diagnostics instead, or provision the dedicated keystore and run the supported Release check above.

For a debug packaging check only, use:

```sh
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :app:assembleDebug -Ppixelplayer.enableAbiSplits=false
```

A debug APK is not a Release candidate, is not a publishable release, and must not be uploaded or tagged as the official artifact.

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

The property `-Ppixelplayer.disableReleaseSigning=true` is intentionally not a Release verification path: it suppresses the signing configuration, but Release tasks still depend on `validateReleaseSigningConfig` and fail. Use the debug packaging check above for general diagnostics, or provision the dedicated release keystore before invoking any Release task.

## Device Smoke Test

Install the release candidate and verify:

1. First launch and setup complete.
2. Explicit SAF library import adds selected music files and they persist across restart.
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
External publication is a separate approval step: do not upload, publish, or distribute an artifact outside the repository until the authorized release owner has given explicit approval for that publication.

## Store Metadata

If you submit to an app store:

1. Update `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
2. Check `PRIVACY.md` still matches the optional network services present in the app.
3. Create source archives from git, not from the working tree, so ignored local artifacts are excluded.

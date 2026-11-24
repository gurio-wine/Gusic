# Privacy Policy

Gusic is local-first and does not include analytics, advertising SDKs, Firebase, Crashlytics, or Google Play Services runtime dependencies.

## Data Stored On Device

The app stores music library metadata, playlists, favorites, lyrics, preferences, playback state, artwork cache, and optional backup/restore data on the device.

## Network Access

Network features are optional and user-controlled:

- MusicBrainz lookup runs only when the user requests enrichment for a track. The app sends that track's title and, when available, artist, album, and duration to the public MusicBrainz search API; applying a result stores the selected MusicBrainz identifiers locally.
- LRCLIB lyric lookup is used only when online lyrics are enabled.
- Deezer artist artwork lookup is used only when online artist images are enabled.
- LX Music online sources: the app provides a built-in source slot (not shown in the source list); builders may place their own default source into it, and it serves online search/playback only while no user source is enabled. Users can additionally import and enable lx-music-compatible source scripts or sign in to supported platforms. Search and playback requests are sent to the third-party platform's public APIs through the active source. Platform cookies are stored locally and may be injected into user-provided source scripts; only use scripts you trust.
- Update checks query the GitHub Releases API (`api.github.com`, or via the `gh-proxy.com` mirror) for the latest version number, release notes, and package information, and in-app updates download the package from GitHub Releases (or via the `ghfast.top` or `gh-proxy.com` mirrors). This check is enabled by default and the automatic check can be turned off in Settings; the requests contain no user data (only a plain version query). The "install unknown apps" permission (`REQUEST_INSTALL_PACKAGES`) is used only to install Gusic's own updates.

Server credentials and preferences are stored locally. The app does not sell or share user data.

## Files And Media

Gusic accesses local music only through user-selected documents (SAF): imported tracks, artwork reading, metadata editing, and backup export/import operate on the files the user explicitly picked, without broad media-storage permissions.

## Crash Reports

The app keeps crash logs locally for troubleshooting. It does not automatically upload crash reports.

## Contact

Open a [GitHub issue](https://github.com/gurio-wine/Gusic/issues) for privacy questions that do not contain sensitive personal data.

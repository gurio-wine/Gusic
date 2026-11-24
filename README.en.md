<p align="center">
  <strong>English</strong> | <a href="README.md">简体中文</a>
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" alt="Gusic logo" width="160"/>
</p>

<h1 align="center">Gusic</h1>

<p align="center">
  <a href="https://github.com/gurio-wine/Gusic/releases/latest">
    <img src="https://img.shields.io/github/v/release/gurio-wine/Gusic?include_prereleases&logo=github&style=for-the-badge&label=Latest%20Release" alt="Latest release">
  </a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <img src="https://img.shields.io/badge/License-GPLv3-blue?style=for-the-badge" alt="GPLv3 license">
</p>

<p align="center">
  An independently maintained Android music player by Gurio Wine, focused on local playback and offline use,
  with optional lx-music-mobile-compatible online music sources (自定义音源) and playlist import/export.
</p>

> **Note:** Gusic is an independent project. It is **not** an official version of PixelPlayerOSS, lx-music-mobile, lx-lxwalnut-music-mobile, or any music platform, and it is not endorsed by their original authors.

<p align="center">This software implements lx source import and playback online features solely for personal use. The app ships a built-in source slot (builders may place their own default source into it) that provides online playback while no user source is enabled; user sources take effect only after being imported and enabled. This software does not host, store, or own any copyrighted audio content.</p>

## Screenshots

<p align="center">
  <img src="assets/screenshot1.webp" alt="Screenshot 1" width="22%"/>
  <img src="assets/screenshot2.webp" alt="Screenshot 2" width="22%"/>
  <img src="assets/screenshot3.webp" alt="Screenshot 3" width="22%"/>
  <img src="assets/screenshot4.webp" alt="Screenshot 4" width="22%"/>
</p>

## Introduction

Gusic is independently maintained by Gurio Wine. It focuses on local playback and offline use, with optional user-imported sources compatible with lx-music-mobile and `.lxmc` playlist import/export.

Local playback and offline use work by default. The app provides a **built-in source slot** (not shown in the source list; builders may place a default source into it) that handles online search/playback while no user source is enabled, if a source has been placed there; you can also import and enable your own source scripts (or sign in to a supported platform), which then take over online requests.

Repository: [github.com/gurio-wine/Gusic](https://github.com/gurio-wine/Gusic)

Package name: `com.gurio.gusic`

Gurio Wine maintains Gusic’s current code, features, and releases; historical source information and third-party licenses and copyrights are documented in the project-origin and attribution section below.

## Features

### Local Playback & Library

| Area | Highlights |
| --- | --- |
| Playback | Media3 playback engine, FFmpeg support, gapless playback, crossfade, custom transitions, queue controls, shuffle, repeat, sleep timer, external file playback |
| Library | Local scanning for MP3, FLAC, AAC, OGG, WAV, M4A, albums, artists, genres, folders, favorites, playlists, stats, metadata editing |
| Lyrics | Embedded lyrics, local `.lrc` files, lyrics import/editing, optional LRCLIB lookup |
| Artwork | Local artwork, album-art palette extraction, optional Deezer artist image lookup |
| Metadata | On-demand MusicBrainz matching for recording, release, and artist identifiers |
| UI | Jetpack Compose, Material 3, dynamic color, light/dark themes, Glance widgets, animated player surfaces |
| Backup | Preferences, playlists, favorites, lyrics, stats, and app state backup/restore |

### Optional Online Sources

| Area | Highlights |
| --- | --- |
| Online sources | Built-in source slot (builders may place a default source into it); user sources are lx-music-mobile-compatible scripts (音源脚本) imported from a `.js` file or URL, managed from the in-app "LX Music" tab |
| Script engine | Sandboxed QuickJS runtime (`user-api-preload.js` bridge) with per-script isolation and the lx-music `lx_setup` API |
| Platforms | Built-in web browsing paths for major platforms; source scripts can declare additional sources |
| Quality | Per-platform quality selection (master, atmos, 24-bit FLAC, FLAC, 320 kbps, 128 kbps) with automatic fallback/downgrade |
| Playlists | Import/export `.lxmc` (落雪 / lx-music) playlists for interchange with lx-music-mobile |
| Downloads | Download online tracks with quality selection, plus a cache/offline path for source songs |
| Accounts | Public data display after basic web-based login for supported platforms |

## LX Music Sources (自定义音源)

The "LX Music" tab lets you search and play music from online platforms through **source scripts**. A source script is a plain JavaScript file that implements the lx-music-mobile source API (`lx_setup`).

- Scripts run in an isolated QuickJS instance so one slow or failing script does not affect the others.
- You can enable multiple sources; the preferred script is tried first, then enabled backup scripts, then the built-in source (if one has been placed) — the best available quality wins.
- You can import scripts from a local `.js` file or from a URL, enable/disable them, and delete them (long-press a script).
- The built-in source is separate from user sources: it never appears in the list above and only serves online search/playback while no user source is enabled.

> **Security:** Some scripts require you to sign in. Only use scripts you trust.

## Online Services & Data Sources

| Service | Purpose | Default |
| --- | --- | --- |
| LX Music sources | Online search/playback via the built-in source (if placed) and imported user source scripts | Built-in source serves while no user source is enabled; user scripts require manual import |
| Various platforms | Official web service | Off until used |
| MusicBrainz | On-demand metadata matching and identifier enrichment | Only when requested |
| LRCLIB | Search online lyrics when local or embedded lyrics are missing | ON |
| Deezer | Fetch missing artist artwork and cache it locally | OFF |
| GitHub Releases | Check for and download app updates | ON (auto-check can be disabled in Settings → App updates) |

LRCLIB and Deezer are partially enabled by default and can be toggled later from their respective switches in Settings.

## Download

Releases are published on GitHub:

```text
https://github.com/gurio-wine/Gusic/releases
```

To install a release, download the `arm64-v8a` APK and install it; it fits most modern Android devices.

The app checks GitHub Releases for new versions in-app; when one is found, it can be downloaded and installed directly from within the app (`arm64-v8a` package only, requires granting the "install unknown apps" permission; downloads automatically try mirrors). For other ABIs or debug builds, download manually from the releases page.

## Project Origin & Attribution

Gurio Wine maintains Gusic’s current code, features, and releases. The current project homepage, source navigation, issue tracker, and release entry point are the [Gusic repository](https://github.com/gurio-wine/Gusic). Gusic continues the PixelPlayerOLX codebase; the [original source repository](https://github.com/MiNoMinoaa/PixelPlayerOLX) is retained only as an origin and historical reference, not as the current Gusic navigation or release entry point. This project-origin statement does not assert that copyright or other rights were transferred; each work remains subject to its applicable terms and the rights of its respective holders. Gusic is not an official PixelPlayerOSS, lx-music-mobile, or platform release, and is not endorsed by their original authors.

PixelPlayerOSS is retained in this project only as historical foundation and attribution (GPL-3.0). Some online-source features are implemented with reference to [lx-music-mobile](https://github.com/lyswhut/lx-music-mobile) and [lx-lxwalnut-music-mobile](https://github.com/WalnutBai/lx-lxwalnut-music-mobile). The relevant licenses and copyrights are retained.

| Project | Repository | License | Copyright / attribution |
| --- | --- | --- | --- |
| PixelPlayerOSS | https://github.com/PixelPlayerHQ/PixelPlayerOSS | GPL-3.0 | Copyright © 2026 Duhan (lostf1sh) |
| Pixel Player (historical upstream of PixelPlayerOSS) | https://github.com/PixelPlayerHQ/PixelPlayer | Proprietary (formerly MIT) | Copyright © Theo Vilardo (theovilardo); logo designed by **Aureal** |
| lx-music-mobile | https://github.com/lyswhut/lx-music-mobile | Apache-2.0 | Copyright © lyswhut |
| lx-lxwalnut-music-mobile | https://github.com/WalnutBai/lx-lxwalnut-music-mobile | Apache-2.0 | Copyright © WalnutBai |

The LX Music source-script system in this project is compatible with the lx-music-mobile source API and is implemented with reference to it (Apache-2.0). No code is forked from lx-music-mobile or lx-lxwalnut-music-mobile.

## Icon Attribution and Third-Party Notices

This project's app icon is a derivative work based on icon assets from the following open-source music player projects:

- **lx-music-mobile**  
  Repository: https://github.com/lyswhut/lx-music-mobile  
  License: Apache License 2.0  
  The original icon assets are used and modified under the Apache License 2.0.  
  lx-music-mobile also states that some images, fonts, and other resources included in the project may come from the Internet and may not be owned by the project itself.

- **PixelPlayerOSS historical attribution**
  Repository: https://github.com/PixelPlayerHQ/PixelPlayerOSS  
  License: GNU General Public License v3.0 (GPL-3.0)  
  The original icon/logo design is attributed to **Aureal**, as stated in the PixelPlayerOSS project.

The combined/modified icon in this repository is provided for identification, attribution, and derivative-work documentation purposes only.  
This project is **not** an official version of lx-music-mobile or PixelPlayerOSS, and it is not endorsed by their original authors.

All respective copyrights belong to their original owners.  
If you are the copyright holder of any asset used here and wish to request removal or modification, please open an [issue](https://github.com/gurio-wine/Gusic/issues/new) or contact the repository maintainer.

## Disclaimer — Respect Copyright

The online music source feature retrieves data from third-party platforms through their public APIs and through user-provided source scripts. This project:

- Does not host, store, or own any copyrighted audio content.
- Cannot verify the legality, accuracy, or licensing of content returned by third-party sources or scripts.
- Is provided free of charge for technical learning and research only, and is not affiliated with or endorsed by any music platform.
- Is intended to be used in compliance with local laws and the terms of the platforms you access.

**Please respect copyright and support the official platforms.** Do not use this software in ways that infringe the rights of artists, labels, or platforms. Only use source scripts and accounts you are authorized to use.

## Contributing

Contributions are welcome. Open a focused [issue](https://github.com/gurio-wine/Gusic/issues/new) or [pull request](https://github.com/gurio-wine/Gusic/pulls), and include test/build results when possible.


See [SECURITY.md](SECURITY.md) for reporting vulnerabilities.

Privacy policy: [PRIVACY.md](PRIVACY.md)

## Acknowledgements

Thanks to the following projects and their maintainers for their contributions to the open-source community:

- [PixelPlayerOSS](https://github.com/PixelPlayerHQ/PixelPlayerOSS)
- [lx-music-mobile](https://github.com/lyswhut/lx-music-mobile)
- [lx-lxwalnut-music-mobile](https://github.com/WalnutBai/lx-lxwalnut-music-mobile)
- All source-script maintainers

## License

Gusic is licensed under the [GNU General Public License v3.0](LICENSE) (`SPDX-License-Identifier: GPL-3.0-or-later`).

```
Gusic
Copyright (C) 2026 MiNoMinoaa

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
```

Gusic retains PixelPlayerOSS historical foundation and attribution (GPL-3.0, Copyright © 2026 Duhan (lostf1sh)). Some online-source features are implemented with reference to lx-music-mobile (Apache-2.0, Copyright © lyswhut) and lx-lxwalnut-music-mobile (Apache-2.0, Copyright © WalnutBai), and are used under their respective licenses.

Distributed APKs include third-party components under their own licenses. In particular, the optional FFmpeg decoder dependency `org.jellyfin.media3:media3-ffmpeg-decoder` is GPL-3.0; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

<p align="center">
  Maintained by <a href="https://github.com/gurio-wine/Gusic">Gusic</a>
</p>

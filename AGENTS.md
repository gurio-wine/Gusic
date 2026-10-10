# 项目概览

Gusic 是基于公开源仓库 MiNoMinoaa/PixelPlayerOLX 的 Android 音乐播放器，默认以本地播放、自托管媒体库和离线使用为核心。应用内置一个隐藏且不可修改的授权兜底音源（仅在未启用用户音源时生效）；用户可另行导入兼容 lx-music-mobile 的音源脚本，属可选功能。该仓库是独立的私有副本，Git `upstream` 应指向 `https://github.com/MiNoMinoaa/PixelPlayerOLX.git`，不应将 PixelPlayerOSS 配置为本项目 upstream。

## 开发约定

- 本项目采用智能体团队协作：由负责人拆分任务，团队成员承担边界清晰的独立工作；合并前由负责人检查结果并按改动范围验证。
- 尊重上游及第三方许可证；联网服务保持用户可控，除内置授权兜底音源（隐藏、不可修改）外，在线音源均为可选功能。
- 项目主要使用 Kotlin、Jetpack Compose 和 Gradle；模块结构与构建配置以 `settings.gradle.kts`、`app/build.gradle.kts` 为准。

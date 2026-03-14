# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0-beta] - 2025-07-09

### Added

- SSH server based on Apache MINA SSHD 2.17.1
- SFTP subsystem for secure file transfer
- Jump host / bastion relay support (`ssh -J`)
- Native PTY support via JNI (`/dev/ptmx`) with ProcessBuilder fallback
- Public key authentication with in-app authorized_keys management
- `ssh-copy-id` support via exec channel path rewriting
- Optional password authentication (disabled when password is empty)
- Persistent host key — generated once, survives app restarts
- Foreground service with WakeLock, WifiLock, and NetworkCallback
- Boot auto-start option
- Port conflict detection and stale port release on startup
- Real-time active session tracking with keepalive
- Configurable shell path and home directory (Termux shell compatible)
- Chinese / English UI with runtime language switching
- Material 3 dynamic theme with Jetpack Compose
- Terminal-style app icon (dark background, green `>_` prompt)
- Build script (`build.sh`) for debug / release / clean builds

[Unreleased]: https://github.com/JoursBleu/ssh-server/compare/v0.1.0-beta...HEAD
[0.1.0-beta]: https://github.com/JoursBleu/ssh-server/releases/tag/v0.1.0-beta

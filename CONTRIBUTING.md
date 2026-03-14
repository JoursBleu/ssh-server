# Contributing to SSH Server

感谢你对本项目的关注！欢迎提交 Issue 和 Pull Request。

Thank you for your interest in contributing!

## Getting Started

### Prerequisites

- JDK 17+
- Android SDK 35 (`ANDROID_HOME` must be set)
- Android NDK 27+ (for native PTY)
- CMake 3.22+

### Build

```bash
git clone https://github.com/JoursBleu/ssh-server.git
cd ssh-server
./build.sh          # debug APK
./build.sh release  # release APK (requires release.keystore)
```

## How to Contribute

### Reporting Bugs

- Search existing [Issues](https://github.com/JoursBleu/ssh-server/issues) first
- Include: Android version, device model, steps to reproduce, expected vs actual behavior
- Attach logcat output if possible: `adb logcat -s SshServer`

### Suggesting Features

- Open an Issue with the **feature request** label
- Describe the use case and expected behavior

### Submitting Changes

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes
4. Test on a real Android device (emulators lack full PTY/network support)
5. Commit with clear messages: `git commit -m "add: description of change"`
6. Push and open a Pull Request against `main`

## Code Style

- **Kotlin**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Indentation**: 4 spaces (no tabs)
- **Max line length**: 120 characters
- **Compose**: One composable per file when it exceeds ~100 lines
- **Naming**: `camelCase` for functions/variables, `PascalCase` for classes/composables

## Project Structure

```
app/src/main/java/com/ssh/relay/
├── engine/    # SSH server/client core logic
├── service/   # Android foreground service, boot receiver
├── shell/     # Shell/PTY/exec command handling
├── ui/        # Jetpack Compose UI screens
└── cpp/       # Native C code (PTY)
```

## Commit Message Convention

Use a prefix to indicate the type of change:

| Prefix | Description |
|--------|-------------|
| `add:` | New feature |
| `fix:` | Bug fix |
| `refactor:` | Code restructuring (no behavior change) |
| `docs:` | Documentation only |
| `style:` | Formatting, no logic change |
| `build:` | Build system or dependency changes |
| `chore:` | Maintenance tasks |

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

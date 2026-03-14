# SSH Relay

Android SSH Server & Client app powered by Apache MINA SSHD.

## Features

- **SSH Server**: Run an SSH server on your Android device (port 2222)
- **SSH Client**: Connect to remote SSH servers
- **Port Forwarding**: Full support for Local (-L), Remote (-R) and Dynamic (-D SOCKS) forwarding
- **Jump Host**: Use your phone as an SSH jump host / relay
- **SFTP**: Built-in SFTP subsystem for file transfer
- **PTY**: Full interactive terminal with PTY support

## Tech Stack

- **Core**: Apache MINA SSHD 2.17.1
- **Crypto**: BouncyCastle 1.80
- **UI**: Jetpack Compose + Material 3
- **Language**: Kotlin
- **Native**: NDK/CMake for PTY JNI

## License

Apache License 2.0

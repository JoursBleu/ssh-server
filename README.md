# SSH Server

Android SSH Server app powered by Apache MINA SSHD.

[中文](#中文) | [English](#english)

---

## English

### Features

- **SSH Server** — Run a full SSH server on your Android device
- **Jump Host** — Use your phone as an SSH jump host (`ssh -J`)
- **SFTP** — Built-in SFTP subsystem for file transfer
- **PTY** — Full interactive terminal with PTY support (with ProcessBuilder fallback)
- **Public Key Auth** — Manage authorized keys, supports `ssh-copy-id`
- **Password Auth** — Optional password authentication (disabled when password is empty)
- **Persistent Host Key** — Server host key is generated once and persisted across restarts
- **Background Service** — Runs as a foreground service with WakeLock, survives app switch and screen off
- **Settings Cache** — Username, password and port are saved across app restarts
- **Clean Uninstall** — All data (keys, settings, authorized_keys) removed on uninstall

### Usage

1. Install the APK on your Android device
2. Open the app, configure username/password/port (default: `red` / empty / `2222`)
3. Tap **Start Server**
4. Connect from your computer:

```bash
# Password auth (if password is set)
ssh -p 2222 red@<phone-ip>

# Key auth
ssh-copy-id -p 2222 red@<phone-ip>
ssh -p 2222 red@<phone-ip>

# Use as jump host
ssh -J red@<phone-ip>:2222 user@target-host
```

### Tech Stack

| Component | Version |
|-----------|---------|
| Apache MINA SSHD | 2.17.1 |
| BouncyCastle | 1.80 |
| Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Kotlin | 2.1.0 |
| Android SDK | 35 (min 26) |
| NDK / CMake | 27 / 3.22.1 |

### Project Structure

```
app/src/main/java/com/ssh/relay/
├── SshServerApp.kt              # Application init (BouncyCastle, MINA Android mode)
├── engine/
│   ├── SshServerEngine.kt       # SSH server core (auth, shell, SFTP, forwarding)
│   ├── SshClientEngine.kt       # SSH client engine
│   └── AuthorizedKeysManager.kt # authorized_keys file management
├── service/
│   ├── SshServerService.kt      # Foreground service with WakeLock
│   └── BootReceiver.kt          # Boot completed receiver
├── shell/
│   ├── AndroidShellFactory.kt   # Shell factory
│   ├── AndroidShellCommand.kt   # PTY + ProcessBuilder shell
│   ├── AndroidCommandFactory.kt # Exec channel factory (ssh-copy-id)
│   ├── AndroidExecCommand.kt    # Exec command implementation
│   └── PtyCompat.kt             # JNI PTY bridge
├── ui/
│   ├── MainActivity.kt          # Entry activity
│   ├── Navigation.kt            # Server + Keys tabs
│   ├── ServerScreen.kt          # Server config & control
│   ├── KeysScreen.kt            # Authorized keys management
│   └── Theme.kt                 # Material 3 theme
└── cpp/
    └── pty_compat.c             # Native PTY via /dev/ptmx
```

### License

Apache License 2.0

---

## 中文

### 功能特性

- **SSH 服务器** — 在 Android 设备上运行完整的 SSH 服务器
- **跳板机** — 将手机作为 SSH 跳板机使用（`ssh -J`）
- **SFTP** — 内置 SFTP 子系统，支持文件传输
- **PTY** — 完整的交互式终端，支持 PTY（附 ProcessBuilder 回退方案）
- **公钥认证** — 管理 authorized_keys，支持 `ssh-copy-id`
- **密码认证** — 可选的密码认证（密码为空时自动禁用）
- **持久化主机密钥** — 服务器密钥生成后持久保存，重启不变
- **后台服务** — 以前台服务运行并持有 WakeLock，切换应用或熄屏不断连
- **配置缓存** — 用户名、密码、端口在应用重启后自动恢复
- **干净卸载** — 卸载时自动清除所有数据（密钥、配置、authorized_keys）

### 使用方法

1. 在 Android 设备上安装 APK
2. 打开应用，配置用户名/密码/端口（默认：`red` / 空 / `2222`）
3. 点击 **Start Server** 启动服务
4. 从电脑连接：

```bash
# 密码认证（需设置密码）
ssh -p 2222 red@<手机IP>

# 公钥认证
ssh-copy-id -p 2222 red@<手机IP>
ssh -p 2222 red@<手机IP>

# 用作跳板机
ssh -J red@<手机IP>:2222 user@目标主机
```

### 技术栈

| 组件 | 版本 |
|------|------|
| Apache MINA SSHD | 2.17.1 |
| BouncyCastle | 1.80 |
| Jetpack Compose + Material 3 | BOM 2024.12.01 |
| Kotlin | 2.1.0 |
| Android SDK | 35（最低 26） |
| NDK / CMake | 27 / 3.22.1 |

### 项目结构

```
app/src/main/java/com/ssh/relay/
├── SshServerApp.kt              # 应用初始化（BouncyCastle、MINA Android 模式）
├── engine/
│   ├── SshServerEngine.kt       # SSH 服务器核心（认证、Shell、SFTP、转发）
│   ├── SshClientEngine.kt       # SSH 客户端引擎
│   └── AuthorizedKeysManager.kt # authorized_keys 文件管理
├── service/
│   ├── SshServerService.kt      # 前台服务 + WakeLock
│   └── BootReceiver.kt          # 开机自启接收器
├── shell/
│   ├── AndroidShellFactory.kt   # Shell 工厂
│   ├── AndroidShellCommand.kt   # PTY + ProcessBuilder Shell
│   ├── AndroidCommandFactory.kt # Exec 通道工厂（ssh-copy-id）
│   ├── AndroidExecCommand.kt    # Exec 命令实现
│   └── PtyCompat.kt             # JNI PTY 桥接
├── ui/
│   ├── MainActivity.kt          # 入口 Activity
│   ├── Navigation.kt            # Server + Keys 标签页
│   ├── ServerScreen.kt          # 服务器配置与控制
│   ├── KeysScreen.kt            # 公钥管理页面
│   └── Theme.kt                 # Material 3 主题
└── cpp/
    └── pty_compat.c             # 原生 PTY（/dev/ptmx）
```

### 许可证

Apache License 2.0

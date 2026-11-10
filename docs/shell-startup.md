# Shell startup script / 登录初始化脚本

## 中文

在“设置 → Shell → 登录初始化脚本”填写可读脚本的绝对路径，点击“保存初始化脚本”，
重新连接 SSH 后生效。留空保存可关闭该功能。服务端不安装脚本，也不预设任何工具路径。

交互式终端、无 PTY 的 Shell 回退以及 SSH 单条远程命令都会先在同一 Shell 中加载脚本，
因此脚本定义的变量和函数可被后续命令使用。交互式连接保留真实 PTY 和作业控制。
SFTP 子系统不执行初始化脚本；脚本应避免输出，以免干扰 JSON 输出或旧式 SCP 协议。

保存前检查绝对路径、文件存在和读取权限。连接时若文件已被删除或不可读，
会在 stderr 提示并继续正常登录。Shell 路径参数经过转义，支持空格、引号和非 ASCII 字符。
脚本属于用户配置；修改其内容不需要重新编译 APK。

## English

Open **Settings → Shell → Shell startup script**, enter the absolute path of a
readable file, and save. Reconnect SSH to apply it. Save an empty value to disable
initialization. The server neither installs the file nor assumes any tool path.

Interactive PTY sessions, the pipe-based shell fallback, and SSH exec commands
source the file inside the same shell before user commands. Functions and variables
remain available to those commands. Interactive logins retain their real PTY and
job control. SFTP subsystems do not source it. Keep scripts quiet to avoid corrupting
structured output or legacy SCP traffic.

The setting validates absolute paths and readable regular files. If the file later
disappears or becomes unreadable, a stderr warning is emitted and login continues.
Shell quoting supports spaces, quotes, and non-ASCII paths. Editing the external
script never requires rebuilding the APK.

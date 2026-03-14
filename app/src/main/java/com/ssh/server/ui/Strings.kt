package com.ssh.server.ui

import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

enum class Language(val code: String, val label: String) {
    ZH("zh", "中文"),
    EN("en", "English");
}

val LocalLanguage = compositionLocalOf { mutableStateOf(Language.ZH) }

private const val PREFS_NAME = "ssh_server_prefs"
private const val KEY_LANG = "app_language"

fun loadLanguage(context: Context): Language {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val code = prefs.getString(KEY_LANG, "zh") ?: "zh"
    return Language.entries.find { it.code == code } ?: Language.ZH
}

fun saveLanguage(context: Context, lang: Language) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putString(KEY_LANG, lang.code).apply()
}

object S {
    // Server Screen
    val sshServer get() = if (isZh) "SSH 服务" else "SSH Server"
    val stopped get() = if (isZh) "○ 已停止" else "○ Stopped"
    val starting get() = if (isZh) "启动中..." else "Starting..."
    val running get() = if (isZh) "● 运行中" else "● Running"
    val stopping get() = if (isZh) "停止中..." else "Stopping..."
    val error get() = if (isZh) "✕ 错误" else "✕ Error"
    val sessions get() = if (isZh) "会话" else "Sessions"
    val noActiveConnections get() = if (isZh) "无活跃连接" else "No active connections"
    val port get() = if (isZh) "端口" else "Port"
    val username get() = if (isZh) "用户名" else "Username"
    val password get() = if (isZh) "密码" else "Password"
    val passwordHintEmpty get() = if (isZh) "留空则禁用密码认证" else "Leave empty to disable password auth"
    val passwordAuthDisabled get() = if (isZh) "密码认证已禁用，请使用 SSH 密钥。" else "Password auth disabled. Use SSH keys."
    val hostKeyFingerprint get() = if (isZh) "Host Key 指纹" else "Host Key Fingerprint"
    val fingerprintCopied get() = if (isZh) "已复制指纹" else "Fingerprint copied"
    val startServer get() = if (isZh) "启动服务" else "Start Server"
    val stopServer get() = if (isZh) "停止服务" else "Stop Server"

    // Background tip dialog
    val bgTipTitle get() = if (isZh) "后台保活设置" else "Background Keep-Alive"
    val bgTipContent get() = if (isZh)
        "为保证 SSH 服务在后台稳定运行，请根据手机品牌手动设置：\n\n" +
        "华为/荣耀：设置 → 应用启动管理 → 手动管理 → 允许后台活动\n" +
        "小米/红米：设置 → 省电策略 → 无限制\n" +
        "OPPO/vivo：设置 → 电池 → 允许后台运行"
    else
        "To keep SSH service running in background, configure your device:\n\n" +
        "Huawei/Honor: Settings → App Launch → Manual → Allow background\n" +
        "Xiaomi/Redmi: Settings → Battery Saver → No restrictions\n" +
        "OPPO/vivo: Settings → Battery → Allow background"
    val bgTipOk get() = if (isZh) "知道了" else "OK"
    val bgTipDontShow get() = if (isZh) "不再显示" else "Don't show again"

    // Keys Screen
    val authorizedKeys get() = if (isZh) "授权公钥" else "Authorized Keys"
    val keysDescription get() = if (isZh)
        "管理用于密钥认证的 SSH 公钥。\n可使用 ssh-copy-id 或手动添加。"
    else
        "Manage SSH public keys for key-based authentication.\nUse ssh-copy-id or add keys manually."
    val addPublicKey get() = if (isZh) "添加公钥" else "Add Public Key"
    val noAuthorizedKeys get() = if (isZh) "无授权密钥" else "No authorized keys"
    val noKeysHint get() = if (isZh)
        "仅密码认证可用。\n\n" +
        "从电脑添加密钥：\n  ssh-copy-id -p 2222 user@phone-ip\n\n" +
        "或点击上方按钮手动粘贴公钥。"
    else
        "Only password authentication is enabled.\n\n" +
        "To add a key from your computer:\n  ssh-copy-id -p 2222 user@phone-ip\n\n" +
        "Or paste a public key manually using the button above."
    fun keysConfigured(n: Int) = if (isZh) "已配置 $n 个密钥" else "$n key(s) configured"
    val keyCopied get() = if (isZh) "密钥已复制" else "Key copied"
    val keyRemoved get() = if (isZh) "密钥已删除" else "Key removed"
    val keyAdded get() = if (isZh) "密钥已添加" else "Key added"
    val invalidKey get() = if (isZh) "无效密钥或已存在" else "Invalid key or already exists"
    val addKeyTitle get() = if (isZh) "添加 SSH 公钥" else "Add SSH Public Key"
    val addKeyHint get() = if (isZh)
        "粘贴 OpenSSH 公钥：\nssh-rsa AAAA... user@host\nssh-ed25519 AAAA... user@host"
    else
        "Paste an OpenSSH public key:\nssh-rsa AAAA... user@host\nssh-ed25519 AAAA... user@host"
    val publicKey get() = if (isZh) "公钥" else "Public Key"
    val pasteFromClipboard get() = if (isZh) "从剪贴板粘贴" else "Paste from clipboard"
    val comment get() = if (isZh) "备注" else "Comment"
    val commentAutoExtracted get() = if (isZh) "备注已从公钥中自动提取" else "Comment auto-extracted from key"
    val addKey get() = if (isZh) "添加" else "Add Key"
    val cancel get() = if (isZh) "取消" else "Cancel"

    // Settings Screen
    val settings get() = if (isZh) "设置" else "Settings"
    val shell get() = if (isZh) "Shell" else "Shell"
    val homeDir get() = if (isZh) "Home 目录" else "Home Directory"
    val homeDirHint get() = if (isZh) "SSH 登录后的初始工作目录" else "Initial working directory after SSH login"
    val defaultShell get() = if (isZh) "默认 Shell" else "Default Shell"
    val shellHint get() = if (isZh) "可选择或手动输入 Shell 路径" else "Select or enter shell path manually"
    val boot get() = if (isZh) "启动" else "Boot"
    val autoStartOnBoot get() = if (isZh) "开机自启" else "Auto-start on boot"
    val autoStartHint get() = if (isZh) "设备启动后自动开启 SSH 服务" else "Start SSH server after device boot"
    val language get() = if (isZh) "语言" else "Language"
    val about get() = if (isZh) "关于" else "About"
    val appName get() = if (isZh) "应用名称" else "App Name"
    val version get() = if (isZh) "版本" else "Version"
    val packageName get() = if (isZh) "包名" else "Package"
    val sshEngine get() = if (isZh) "SSH 引擎" else "SSH Engine"
    val cryptoLib get() = if (isZh) "加密库" else "Crypto Library"

    // Host Key
    val hostKey get() = if (isZh) "服务器密钥" else "Host Key"
    val hostPrivateKey get() = if (isZh) "私钥 (PEM)" else "Private Key (PEM)"
    val hostPublicKey get() = if (isZh) "公钥 (OpenSSH)" else "Public Key (OpenSSH)"
    val hostKeyNotGenerated get() = if (isZh) "服务器尚未启动过，密钥未生成。" else "Server has not started yet. Key not generated."
    val hostKeyDescription get() = if (isZh) "服务器 RSA 主机密钥，首次启动时自动生成。" else "Server RSA host key, auto-generated on first start."
    val privateKeyCopied get() = if (isZh) "私钥已复制" else "Private key copied"
    val publicKeyCopied get() = if (isZh) "公钥已复制" else "Public key copied"
    val showPrivateKey get() = if (isZh) "显示私钥" else "Show Private Key"
    val hidePrivateKey get() = if (isZh) "隐藏私钥" else "Hide Private Key"

    // Navigation
    val navServer get() = if (isZh) "服务" else "Server"
    val navKeys get() = if (isZh) "密钥" else "Keys"
    val navSettings get() = if (isZh) "设置" else "Settings"

    // Helpers
    val hide get() = if (isZh) "隐藏" else "Hide"
    val show get() = if (isZh) "显示" else "Show"
    val copy get() = if (isZh) "复制" else "Copy"
    val remove get() = if (isZh) "删除" else "Remove"

    private val isZh: Boolean get() = currentLang == Language.ZH

    var currentLang: Language = Language.ZH
}

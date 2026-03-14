package com.ssh.relay

import android.app.Application
import org.apache.sshd.common.util.OsUtils
import org.apache.sshd.common.file.root.RootedFileSystemProvider
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.file.Paths
import java.security.Security

class SshServerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initSecurity()
        initMinaSshd()
    }

    private fun initSecurity() {
        // Remove Android's limited BouncyCastle and register the full one
        Security.removeProvider("BC")
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    private fun initMinaSshd() {
        // Tell MINA SSHD we're on Android
        OsUtils.setAndroid(true)

        // Set user home to app's files directory
        val home = filesDir.toPath()
        org.apache.sshd.common.util.io.PathUtils.setUserHomeFolderResolver { home }
    }

    companion object {
        lateinit var instance: SshServerApp
            private set
    }
}

package com.ssh.relay.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val langState = remember {
                val lang = loadLanguage(this@MainActivity)
                S.currentLang = lang
                mutableStateOf(lang)
            }
            CompositionLocalProvider(LocalLanguage provides langState) {
                SshServerTheme {
                    AppNavigation()
                }
            }
        }
    }
}

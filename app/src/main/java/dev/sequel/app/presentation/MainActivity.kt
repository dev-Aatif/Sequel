package dev.sequel.app.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.sequel.app.presentation.theme.SequelTheme



import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

import android.content.Intent
import androidx.compose.runtime.mutableStateOf

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var supabaseClient: SupabaseClient

    private val isRecovery = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        
        enableEdgeToEdge()
        setContent {
            SequelTheme {
                MainScaffold(
                    isRecovery = isRecovery.value,
                    onRecoveryConsumed = { isRecovery.value = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.let {
            supabaseClient.handleDeeplinks(it)
            val recovery = it.data?.fragment?.contains("type=recovery") == true || it.data?.getQueryParameter("type") == "recovery"
            if (recovery) {
                isRecovery.value = true
            }
        }
    }
}

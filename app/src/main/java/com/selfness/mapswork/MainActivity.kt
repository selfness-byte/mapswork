package com.selfness.mapswork

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import com.selfness.mapswork.ui.*
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingShop = MutableStateFlow<String?>(null)
    private val incomingUpdate = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        incomingShop.value = intent.getStringExtra(SHOP_ID_EXTRA)
        incomingUpdate.value = intent.getBooleanExtra(UPDATE_EXTRA, false)
        setContent {
            MapsworkTheme {
                val target by incomingShop.collectAsStateWithLifecycle()
                val showUpdate by incomingUpdate.collectAsStateWithLifecycle()
                val factory = remember { ViewModelProvider.AndroidViewModelFactory(application) }
                val model = viewModel<MapsworkViewModel>(factory = factory)
                MapsworkApp(model, target, showUpdate) {
                    incomingShop.value = null
                    incomingUpdate.value = false
                    intent.removeExtra(SHOP_ID_EXTRA)
                    intent.removeExtra(UPDATE_EXTRA)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingShop.value = intent.getStringExtra(SHOP_ID_EXTRA)
        incomingUpdate.value = intent.getBooleanExtra(UPDATE_EXTRA, false)
    }

    companion object {
        const val SHOP_ID_EXTRA = "shop_id"
        const val UPDATE_EXTRA = "open_updates"
    }
}

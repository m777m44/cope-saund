package com.mahdi.copesaund

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mahdi.copesaund.data.AppSettings
import com.mahdi.copesaund.data.HistoryStore
import com.mahdi.copesaund.model.HistoryItem
import com.mahdi.copesaund.ui.screens.HistoryScreen
import com.mahdi.copesaund.ui.screens.HomeScreen
import com.mahdi.copesaund.ui.screens.PlayerScreen
import com.mahdi.copesaund.ui.screens.SettingsScreen
import com.mahdi.copesaund.ui.theme.AppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings = remember { AppSettings(this) }
            val historyStore = remember { HistoryStore(this) }
            val history = remember {
                mutableStateListOf<HistoryItem>().apply { addAll(historyStore.load()) }
            }
            var tab by rememberSaveable { mutableIntStateOf(0) }
            var selected by remember { mutableStateOf<HistoryItem?>(null) }

            AppTheme(themeMode = settings.themeMode) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_home)) }
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Filled.List, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_history)) }
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                label = { Text(stringResource(R.string.tab_settings)) }
                            )
                        }
                    }) { padding ->
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (tab) {
                                0 -> Box(Modifier.padding(padding)) {
                                    HomeScreen(
                                        settings = settings,
                                        historyStore = historyStore,
                                        history = history,
                                        onCreated = { item -> selected = item }
                                    )
                                }
                                1 -> Box(Modifier.padding(padding)) {
                                    HistoryScreen(
                                        history = history,
                                        store = historyStore,
                                        onOpen = { selected = it }
                                    )
                                }
                                else -> Box(Modifier.padding(padding)) {
                                    SettingsScreen(settings = settings, onClearHistory = {
                                        historyStore.clear(history)
                                    })
                                }
                            }
                        }
                    }

                    selected?.let { item ->
                        BackHandler { selected = null }
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            PlayerScreen(item = item, onClose = { selected = null })
                        }
                    }
                }
            }
        }
    }
}

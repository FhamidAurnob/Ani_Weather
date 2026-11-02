package com.example.wuwaweather

import android.Manifest
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wuwaweather.data.WeatherViewModel
import com.example.wuwaweather.ui.SplashOverlay
import com.example.wuwaweather.ui.VeloraApp
import com.example.wuwaweather.ui.WeatherActions
import com.example.wuwaweather.alerts.AlarmScheduler
import com.example.wuwaweather.alerts.Notifications
import com.example.wuwaweather.quakes.QuakeWorker
import com.example.wuwaweather.ui.theme.VeloraTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the status & navigation bars, with light icons
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        // Alerts: notification channels, upcoming alarms, background earthquake checks
        Notifications.ensureChannels(this)
        runCatching { AlarmScheduler.rescheduleAll(this) }
        QuakeWorker.schedule(this)

        setContent {
            VeloraTheme {
                val vm: WeatherViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()

                // Location permission (approximate location is enough for weather)
                val userAsked = remember { mutableStateOf(false) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted -> vm.onLocationPermissionResult(granted, userAsked.value) }

                // First launch: offer location once
                LaunchedEffect(Unit) {
                    if (vm.shouldAskLocationOnStart()) {
                        permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    VeloraApp(
                        state = state,
                        actions = WeatherActions(
                            onRefresh = vm::refresh,
                            onToggleMode = vm::toggleBackgroundMode,
                            onToggleUnits = vm::toggleUnits,
                            onQueryChanged = vm::onQueryChanged,
                            onSearch = vm::addCity,
                            onSuggestion = vm::addSuggestion,
                            onUseLocation = {
                                if (vm.hasLocationPermission()) {
                                    vm.enableLocation()
                                } else {
                                    userAsked.value = true
                                    permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                            },
                            onSelectPlace = vm::selectPage,
                            onRemovePlace = vm::removePage,
                            onDismissMessage = vm::dismissMessage
                        )
                    )
                    AnimatedVisibility(
                        visible = state.showSplash,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(700))
                    ) {
                        SplashOverlay()
                    }
                }
            }
        }
    }
}

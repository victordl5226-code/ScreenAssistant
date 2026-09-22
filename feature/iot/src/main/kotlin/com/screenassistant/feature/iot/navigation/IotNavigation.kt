package com.screenassistant.feature.iot.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.screenassistant.feature.iot.ui.permissions.IoTPermissionsScreen
import com.screenassistant.feature.iot.ui.settings.IoTSettingsScreen

/**
 * Rutas de navegación para el módulo IoT.
 */
object IoTRoutes {
    const val SETTINGS = "iot_settings"
    const val PERMISSIONS = "iot_permissions"
}

/**
 * Componente de navegación del módulo IoT.
 *
 * Maneja las transiciones entre las pantallas de configuración
 * y permisos IoT.
 */
@Composable
fun IotNavigation(
    navController: NavHostController,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    startDestination: String = IoTRoutes.SETTINGS,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            fadeIn(animationSpec = tween(300)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(300),
                    )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(300))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(300)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(300),
                    )
        },
    ) {
        composable(route = IoTRoutes.SETTINGS) {
            IoTSettingsScreen(
                onNavigateToPermissions = {
                    navController.navigate(IoTRoutes.PERMISSIONS)
                },
                onBack = onExit,
            )
        }

        composable(route = IoTRoutes.PERMISSIONS) {
            IoTPermissionsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }
}

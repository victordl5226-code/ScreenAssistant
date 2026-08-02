package com.screenassistant.service.system.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.screenassistant.feature.chat.ChatScreen
import com.screenassistant.feature.chat.ChatViewModel

object Routes {
    const val CHAT = "chat"
}

@Composable
fun ScreenAssistantNavGraph(chatViewModel: ChatViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT
    ) {
        composable(Routes.CHAT) {
            ChatScreen(viewModel = chatViewModel)
        }
    }
}

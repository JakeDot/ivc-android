package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.AuthState
import com.example.ui.AuthViewModel
import com.example.ui.ChatScreen
import com.example.ui.LoginScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    IVCApp()
                }
            }
        }
    }
}

@Composable
fun IVCApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()

    NavHost(
        navController = navController,
        startDestination = "auth_router"
    ) {
        composable("auth_router") {
            when (authState) {
                is AuthState.Authenticated -> {
                    ChatScreen(
                        authViewModel = authViewModel,
                        onSignOut = {
                            // Automatically switches state, but we can do extra logic here if needed
                        }
                    )
                }
                is AuthState.Unauthenticated, is AuthState.Error -> {
                    LoginScreen(
                        authViewModel = authViewModel,
                        onLoginSuccess = {
                            // Automatically switches state
                        }
                    )
                }
                is AuthState.Loading -> {
                    // Could show a terminal cursor blinking or similar loading state
                    Surface(modifier = Modifier.fillMaxSize()) {}
                }
            }
        }
    }
}


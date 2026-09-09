package dev.sequel.app.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.sequel.app.presentation.components.glassmorphicBackground
import dev.sequel.app.presentation.components.hapticClickable
import dev.sequel.app.presentation.navigation.BottomNavItem
import dev.sequel.app.presentation.navigation.Screen
import dev.sequel.app.presentation.navigation.SequelNavGraph

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background

@Composable
fun MainScaffold(viewModel: MainViewModel = hiltViewModel()) {
    val startDestination by viewModel.startDestination.collectAsState()

    if (startDestination == null) {
        // Show blank background matching splash screen while determining session state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        )
    } else {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        // Bottom bar is visible only on main tab screens
        val bottomBarRoutes = BottomNavItem.entries.map { it.route }
        val showBottomBar = currentRoute in bottomBarRoutes

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                SequelNavGraph(
                    navController = navController,
                    startDestination = startDestination!!,
                    modifier = Modifier.fillMaxSize() // Let screens handle their own top insets for true edge-to-edge
                )

                AnimatedVisibility(
                    visible = showBottomBar,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(200)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(200)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp)
                ) {
                    SequelBottomBar(
                        navController = navController,
                        currentRoute = currentRoute
                    )
                }
            }
        }
    }
}

@Composable
private fun SequelBottomBar(
    navController: NavHostController,
    currentRoute: String?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .glassmorphicBackground(
                shape = RoundedCornerShape(32.dp),
                blurRadius = 24.dp,
                surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                borderColor = Color.White.copy(alpha = 0.1f)
            ),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem.entries.forEach { item ->
            androidx.compose.runtime.key(item.route) {
                val isSelected = currentRoute == item.route
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.15f else 1.0f,
                    animationSpec = tween(150),
                    label = "icon_scale"
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .hapticClickable(
                            indication = null, // No ripple for clean glassmorphic look
                        ) {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(Screen.Home.route) { 
                                        saveState = true 
                                    }
                                    launchSingleTop = true
                                    // Don't restore state when navigating to Home to prevent bringing back popped tabs
                                    restoreState = item.route != Screen.Home.route
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                        contentDescription = item.label,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .scale(scale)
                            .shadow(
                                elevation = if (isSelected) 8.dp else 0.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = MaterialTheme.colorScheme.primary,
                                spotColor = MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
        }
    }
}

package net.levente.cantotrack.mobile

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.MutableStateFlow
import net.levente.cantotrack.mobile.data.session.Session
import net.levente.cantotrack.mobile.data.session.SessionState
import net.levente.cantotrack.mobile.ui.components.StopClockDialog
import net.levente.cantotrack.mobile.ui.home.TicketsScreen
import net.levente.cantotrack.mobile.ui.home.TicketsViewModel
import net.levente.cantotrack.mobile.ui.hours.HoursScreen
import net.levente.cantotrack.mobile.ui.hours.HoursViewModel
import net.levente.cantotrack.mobile.ui.login.LoginScreen
import net.levente.cantotrack.mobile.ui.rememberClockActions
import net.levente.cantotrack.mobile.ui.settings.SettingsScreen
import net.levente.cantotrack.mobile.ui.theme.CantoTrackTheme
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import net.levente.cantotrack.mobile.ui.ticket.TicketScreen
import net.levente.cantotrack.mobile.ui.ticket.TicketViewModel

class MainActivity : ComponentActivity() {
    /** A ticket to open, from the clock's notification. */
    private val openTicket = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen starts with the dark header, so the status bar icons are light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) openTicket.value = intent.getStringExtra(EXTRA_TICKET)
        val container = (application as CantoTrackApp).container
        setContent {
            CantoTrackTheme { CantoTrackNavigation(container, openTicket) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_TICKET)?.let { openTicket.value = it }
    }

    companion object {
        const val EXTRA_TICKET = "ticket"
    }
}

private object Routes {
    const val TICKETS = "tickets"
    const val HOURS = "hours"
    const val TICKET = "ticket/{key}"
    const val SETTINGS = "settings"

    fun ticket(key: String) = "ticket/$key"
}

@Composable
private fun CantoTrackNavigation(container: AppContainer, openTicket: MutableStateFlow<String?>) {
    val sessionState by container.sessions.state.collectAsStateWithLifecycle()

    when (val current = sessionState) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(CtTheme.colors.header))
        is SessionState.SignedOut -> LoginScreen(container.sessions, current.expired)
        // Signing out leaves this branch, so every sign-in starts afresh on the tickets.
        is SessionState.SignedIn -> SignedIn(container, current.session, openTicket)
    }
}

@Composable
private fun SignedIn(container: AppContainer, session: Session, openTicket: MutableStateFlow<String?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val clock by container.timer.clock.collectAsStateWithLifecycle()
    var stopping by rememberSaveable { mutableStateOf(false) }
    // Bumped when time was logged, so the lists showing hours read them again.
    var logged by remember { mutableStateOf(0) }
    val clockActions = rememberClockActions(container, snackbar) { logged++ }

    // The web may have started or stopped the clock while the app was away.
    LifecycleResumeEffect(Unit) {
        clockActions.refresh()
        onPauseOrDispose { }
    }

    val pending by openTicket.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        pending?.let {
            nav.navigate(Routes.ticket(it)) { launchSingleTop = true }
            openTicket.value = null
        }
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = route == Routes.TICKETS || route == Routes.HOURS

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) {
                TabBar(route) { tab ->
                    nav.navigate(tab) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TICKETS, modifier = Modifier.padding(padding)) {
            composable(Routes.TICKETS) {
                val viewModel: TicketsViewModel = viewModel(
                    factory = viewModelFactory { initializer { TicketsViewModel(container.api, container.sessions) } },
                )
                LaunchedEffect(logged) { if (logged > 0) viewModel.loadToday() }
                TicketsScreen(
                    viewModel = viewModel,
                    user = session.user,
                    clock = clock,
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                    onStopClock = { stopping = true },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.HOURS) {
                val viewModel: HoursViewModel = viewModel(
                    factory = viewModelFactory { initializer { HoursViewModel(container.api, container.sessions) } },
                )
                LaunchedEffect(logged) { if (logged > 0) viewModel.refresh() }
                HoursScreen(
                    viewModel = viewModel,
                    clock = clock,
                    snackbar = snackbar,
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                    onStopClock = { stopping = true },
                )
            }
            composable(Routes.TICKET, arguments = listOf(navArgument("key") { type = NavType.StringType })) { backStack ->
                val key = backStack.arguments?.getString("key").orEmpty()
                val viewModel: TicketViewModel = viewModel(
                    key = "ticket-$key",
                    factory = viewModelFactory { initializer { TicketViewModel(key, container.api, container.sessions) } },
                )
                LaunchedEffect(logged) { if (logged > 0) viewModel.refresh() }
                TicketScreen(
                    viewModel = viewModel,
                    user = session.user,
                    clock = clock,
                    clockActions = clockActions,
                    snackbar = snackbar,
                    onStopClock = { stopping = true },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(session = session, onSignOut = container.sessions::signOut, onBack = { nav.popBackStack() })
            }
        }
    }

    val running = clock
    if (stopping && running != null) {
        StopClockDialog(
            clock = running,
            onStop = { note -> stopping = false; clockActions.stop(note) },
            onDiscard = { stopping = false; clockActions.discard() },
            onDismiss = { stopping = false },
        )
    } else if (stopping) {
        // Stopped elsewhere while the question was about to open.
        LaunchedEffect(Unit) { stopping = false }
    }
}

@Composable
private fun TabBar(route: String?, onTab: (String) -> Unit) {
    val colors = CtTheme.colors
    NavigationBar(containerColor = colors.surface) {
        listOf(
            Triple(Routes.TICKETS, Icons.Rounded.TaskAlt, R.string.tab_tickets),
            Triple(Routes.HOURS, Icons.Rounded.Schedule, R.string.tab_hours),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = route == tab,
                onClick = { if (route != tab) onTab(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(stringResource(label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.primary,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primarySoft,
                    unselectedIconColor = colors.muted,
                    unselectedTextColor = colors.muted,
                ),
            )
        }
    }
}

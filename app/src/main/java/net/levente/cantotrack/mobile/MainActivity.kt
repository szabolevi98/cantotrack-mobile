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
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.ViewKanban
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import net.levente.cantotrack.mobile.data.news.NewsWorker
import net.levente.cantotrack.mobile.data.session.Session
import net.levente.cantotrack.mobile.data.session.SessionState
import net.levente.cantotrack.mobile.ui.board.BoardScreen
import net.levente.cantotrack.mobile.ui.board.BoardViewModel
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.components.StopClockDialog
import net.levente.cantotrack.mobile.ui.home.TicketsScreen
import net.levente.cantotrack.mobile.ui.home.TicketsViewModel
import net.levente.cantotrack.mobile.ui.hours.HoursScreen
import net.levente.cantotrack.mobile.ui.hours.HoursViewModel
import net.levente.cantotrack.mobile.ui.login.LoginScreen
import net.levente.cantotrack.mobile.ui.news.NotificationsScreen
import net.levente.cantotrack.mobile.ui.news.NotificationsViewModel
import net.levente.cantotrack.mobile.ui.newticket.NewTicketScreen
import net.levente.cantotrack.mobile.ui.newticket.NewTicketViewModel
import net.levente.cantotrack.mobile.ui.rememberClockActions
import net.levente.cantotrack.mobile.ui.settings.SettingsScreen
import net.levente.cantotrack.mobile.ui.theme.CantoTrackTheme
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import net.levente.cantotrack.mobile.ui.ticket.TicketScreen
import net.levente.cantotrack.mobile.ui.ticket.TicketViewModel

/** Where the app was asked to go from outside: the shade, the widget, the icon's shortcuts. */
sealed interface Opening {
    data class Ticket(val key: String) : Opening
    data object LogTime : Opening
    data object NewTicket : Opening
    data object Clock : Opening
}

class MainActivity : ComponentActivity() {
    private val opening = MutableStateFlow<Opening?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Every screen starts with the dark header, so the status bar icons are light.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        if (savedInstanceState == null) opening.value = openingOf(intent)
        val container = (application as CantoTrackApp).container
        setContent {
            CantoTrackTheme { CantoTrackNavigation(container, opening) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openingOf(intent)?.let { opening.value = it }
    }

    private fun openingOf(intent: Intent): Opening? = when (intent.action) {
        ACTION_LOG_TIME -> Opening.LogTime
        ACTION_NEW_TICKET -> Opening.NewTicket
        ACTION_CLOCK -> Opening.Clock
        else -> intent.getStringExtra(EXTRA_TICKET)?.let(Opening::Ticket)
    }

    companion object {
        const val EXTRA_TICKET = "ticket"
        const val ACTION_LOG_TIME = "net.levente.cantotrack.mobile.LOG_TIME"
        const val ACTION_NEW_TICKET = "net.levente.cantotrack.mobile.NEW_TICKET"
        const val ACTION_CLOCK = "net.levente.cantotrack.mobile.CLOCK"
    }
}

private object Routes {
    const val TICKETS = "tickets"
    const val BOARD = "board"
    const val NEWS = "news"
    const val HOURS = "hours"
    const val TICKET = "ticket/{key}"
    const val NEW_TICKET = "new-ticket"
    const val SETTINGS = "settings"

    val TABS = setOf(TICKETS, BOARD, NEWS, HOURS)

    fun ticket(key: String) = "ticket/$key"
}

@Composable
private fun CantoTrackNavigation(container: AppContainer, opening: MutableStateFlow<Opening?>) {
    val sessionState by container.sessions.state.collectAsStateWithLifecycle()

    when (val current = sessionState) {
        SessionState.Loading -> Box(Modifier.fillMaxSize().background(CtTheme.colors.header))
        is SessionState.SignedOut -> LoginScreen(container.sessions, current.expired)
        // Signing out leaves this branch, so every sign-in starts afresh on the tickets.
        is SessionState.SignedIn -> SignedIn(container, current.session, opening)
    }
}

@Composable
private fun SignedIn(container: AppContainer, session: Session, opening: MutableStateFlow<Opening?>) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val clock by container.timer.clock.collectAsStateWithLifecycle()
    val unread by container.news.unread.collectAsStateWithLifecycle()
    var stopping by rememberSaveable { mutableStateOf(false) }
    var startLogging by rememberSaveable { mutableStateOf(false) }
    var newsAlerts by remember { mutableStateOf(container.prefs.newsAlerts) }
    // Bumped when time was logged, so the lists showing hours read them again.
    var logged by remember { mutableStateOf(0) }
    val clockActions = rememberClockActions(container, snackbar) { logged++ }
    val user = session.user

    // The web may have started or stopped the clock while the app was away.
    LifecycleResumeEffect(Unit) {
        clockActions.refresh()
        onPauseOrDispose { }
    }

    // The number on the bell, every minute while the app is in front.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                runCatching { container.sessions.call { container.news.refresh(it) } }
                delay(60_000)
            }
        }
    }

    val pending by opening.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        when (val open = pending) {
            null -> return@LaunchedEffect
            is Opening.Ticket -> nav.navigate(Routes.ticket(open.key)) { launchSingleTop = true }
            Opening.NewTicket -> if (!user.isGuest) nav.navigate(Routes.NEW_TICKET) { launchSingleTop = true }
            Opening.LogTime -> {
                startLogging = true
                nav.goToTab(Routes.HOURS)
            }
            Opening.Clock -> {
                val running = container.timer.clock.value
                val last = container.prefs.lastClockTicket
                when {
                    running != null -> stopping = true
                    last != null -> nav.navigate(Routes.ticket(last)) { launchSingleTop = true }
                    else -> nav.goToTab(Routes.TICKETS)
                }
            }
        }
        opening.value = null
    }

    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = route in Routes.TABS

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) TabBar(route, unread) { tab -> nav.goToTab(tab) }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.TICKETS, modifier = Modifier.padding(padding)) {
            composable(Routes.TICKETS) {
                val viewModel: TicketsViewModel = viewModel(factory = viewModelFactory { initializer { TicketsViewModel(container) } })
                LaunchedEffect(logged) { if (logged > 0) viewModel.loadToday() }
                TicketsScreen(
                    viewModel = viewModel,
                    user = user,
                    clock = clock,
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                    onNewTicket = { nav.navigate(Routes.NEW_TICKET) },
                    onStopClock = { stopping = true },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.BOARD) {
                val viewModel: BoardViewModel = viewModel(factory = viewModelFactory { initializer { BoardViewModel(container) } })
                BoardScreen(
                    viewModel = viewModel,
                    user = user,
                    clock = clock,
                    snackbar = snackbar,
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                )
            }
            composable(Routes.NEWS) {
                val viewModel: NotificationsViewModel = viewModel(factory = viewModelFactory { initializer { NotificationsViewModel(container) } })
                NotificationsScreen(
                    viewModel = viewModel,
                    snackbar = snackbar,
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                    onOpened = container.news::clearShade,
                )
            }
            composable(Routes.HOURS) {
                val viewModel: HoursViewModel = viewModel(factory = viewModelFactory { initializer { HoursViewModel(container) } })
                LaunchedEffect(logged) { if (logged > 0) viewModel.refresh() }
                HoursScreen(
                    viewModel = viewModel,
                    user = user,
                    clock = clock,
                    snackbar = snackbar,
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                    onStopClock = { stopping = true },
                    onLogged = { logged++ },
                    startLogging = startLogging,
                    onStartedLogging = { startLogging = false },
                )
            }
            composable(Routes.TICKET, arguments = listOf(navArgument("key") { type = NavType.StringType })) { backStack ->
                val key = backStack.arguments?.getString("key").orEmpty()
                val viewModel: TicketViewModel = viewModel(
                    key = "ticket-$key",
                    factory = viewModelFactory { initializer { TicketViewModel(key, container) } },
                )
                LaunchedEffect(logged) { if (logged > 0) viewModel.refresh() }
                TicketScreen(
                    viewModel = viewModel,
                    user = user,
                    clock = clock,
                    clockActions = clockActions,
                    snackbar = snackbar,
                    onStopClock = { stopping = true },
                    onTicket = { nav.navigate(Routes.ticket(it)) },
                    onBack = { if (!nav.popBackStack()) nav.goToTab(Routes.TICKETS) },
                )
            }
            composable(Routes.NEW_TICKET) {
                val viewModel: NewTicketViewModel = viewModel(factory = viewModelFactory { initializer { NewTicketViewModel(container, user) } })
                NewTicketScreen(
                    viewModel = viewModel,
                    user = user,
                    snackbar = snackbar,
                    onCreated = { key ->
                        nav.navigate(Routes.ticket(key)) { popUpTo(Routes.NEW_TICKET) { inclusive = true } }
                    },
                    onBack = { nav.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    session = session,
                    newsAlerts = newsAlerts,
                    onNewsAlerts = { on ->
                        newsAlerts = on
                        container.prefs.newsAlerts = on
                        if (on) NewsWorker.schedule(container.context) else NewsWorker.cancel(container.context)
                    },
                    onSignOut = container.sessions::signOut,
                    onBack = { nav.popBackStack() },
                )
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

/** One of the four tabs, each keeping where it was. */
private fun NavHostController.goToTab(tab: String) {
    navigate(tab) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun TabBar(route: String?, unread: Int, onTab: (String) -> Unit) {
    val colors = CtTheme.colors
    NavigationBar(containerColor = colors.surface) {
        listOf<Triple<String, ImageVector, Int>>(
            Triple(Routes.TICKETS, Icons.Rounded.TaskAlt, R.string.tab_tickets),
            Triple(Routes.BOARD, Icons.Rounded.ViewKanban, R.string.tab_board),
            Triple(Routes.NEWS, Icons.Rounded.Notifications, R.string.tab_news),
            Triple(Routes.HOURS, Icons.Rounded.Schedule, R.string.tab_hours),
        ).forEach { (tab, icon, label) ->
            NavigationBarItem(
                selected = route == tab,
                onClick = { if (route != tab) onTab(tab) },
                icon = {
                    if (tab == Routes.NEWS && unread > 0) {
                        BadgedBox(badge = { Badge(containerColor = colors.danger) { Text(if (unread > 99) "99+" else "$unread") } }) {
                            Icon(icon, contentDescription = null)
                        }
                    } else {
                        Icon(icon, contentDescription = null)
                    }
                },
                label = { Text(stringResource(label), maxLines = 1) },
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

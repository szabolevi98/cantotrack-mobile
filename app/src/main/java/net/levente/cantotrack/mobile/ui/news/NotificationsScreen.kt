package net.levente.cantotrack.mobile.ui.news

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Notification
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.EmptyState
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.HeaderToggle
import net.levente.cantotrack.mobile.ui.components.rememberNotificationPermission
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel,
    snackbar: SnackbarHostState,
    onTicket: (String) -> Unit,
    onOpened: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    val context = LocalContext.current
    val list = rememberLazyListState()
    val askForNotifications = rememberNotificationPermission()

    // Looked at in the app: the shade's copies go, and the news may be shown from now on.
    LaunchedEffect(Unit) {
        onOpened()
        askForNotifications()
    }

    var resumedBefore by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (resumedBefore) viewModel.load() else resumedBefore = true
        onPauseOrDispose { }
    }

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.messageShown()
        }
    }

    val nearEnd by remember { derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 5 } }
    LaunchedEffect(nearEnd, state.items.size) { if (nearEnd) viewModel.loadMore() }

    Column(Modifier.fillMaxSize()) {
        CtHeader(
            title = stringResource(R.string.news_title),
            subtitle = if (state.unread > 0) stringResource(R.string.news_unread, state.unread) else stringResource(R.string.news_all_read),
            actions = {
                if (state.unread > 0) {
                    IconButton(onClick = viewModel::markAllRead) {
                        Icon(Icons.Rounded.DoneAll, stringResource(R.string.news_read_all), tint = Color.White)
                    }
                }
            },
        ) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderToggle(stringResource(R.string.news_everything), !state.unreadOnly) { viewModel.setUnreadOnly(false) }
                HeaderToggle(stringResource(R.string.news_unread_only), state.unreadOnly) { viewModel.setUnreadOnly(true) }
            }
        }

        PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                state.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                when {
                    state.loading -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.items.isEmpty() && state.error == null -> item(key = "empty") {
                        EmptyState(
                            Icons.Rounded.NotificationsNone,
                            stringResource(if (state.unreadOnly) R.string.news_empty_unread_title else R.string.news_empty_title),
                            stringResource(R.string.news_empty_text),
                        )
                    }
                    else -> {
                        items(state.items, key = { it.id }) { item ->
                            NotificationRow(item) {
                                viewModel.markRead(item)
                                when {
                                    item.ticket != null -> onTicket(item.ticket.key)
                                    item.url != null -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                                }
                            }
                        }
                        if (state.loadingMore) {
                            item(key = "more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(item: Notification, onClick: () -> Unit) {
    val colors = CtTheme.colors
    val (icon, tint) = kindIcon(item.kind)
    CtCard(
        onClick = onClick,
        border = if (item.read) colors.border else colors.primary.copy(alpha = 0.35f),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (item.read) colors.slateSoft else colors.primarySoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (item.read) colors.muted else tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val subject = item.ticket?.let { "${it.key} · ${it.title}" } ?: item.epic?.title
                if (subject != null) {
                    Text(
                        subject,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (item.read) colors.muted else colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (item.read) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(ago(item.createdAt), style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
            if (!item.read) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.padding(top = 4.dp).size(9.dp).clip(CircleShape).background(colors.primary))
            }
        }
    }
}

@Composable
private fun kindIcon(kind: String): Pair<ImageVector, Color> {
    val colors = CtTheme.colors
    return when (kind) {
        "assigned" -> Icons.Rounded.AssignmentInd to colors.violet
        "mentioned" -> Icons.Rounded.AlternateEmail to colors.warning
        "status" -> Icons.Rounded.SwapHoriz to colors.success
        "commented" -> Icons.AutoMirrored.Rounded.Chat to colors.primary
        else -> Icons.Rounded.Edit to colors.primary
    }
}

/**
 * "5 minutes ago", in the phone's language. The server writes the moment in
 * its own time zone; a phone in another one would read a moment still to
 * come, which is said as "now" rather than "in an hour".
 */
private fun ago(value: String): String = try {
    val now = System.currentTimeMillis()
    val millis = LocalDateTime.parse(value.replace(' ', 'T')).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    DateUtils.getRelativeTimeSpanString(minOf(millis, now), now, DateUtils.MINUTE_IN_MILLIS).toString()
} catch (e: DateTimeParseException) {
    value
}

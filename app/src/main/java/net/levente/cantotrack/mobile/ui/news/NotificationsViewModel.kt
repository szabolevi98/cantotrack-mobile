package net.levente.cantotrack.mobile.ui.news

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.levente.cantotrack.mobile.AppContainer
import net.levente.cantotrack.mobile.data.api.ApiException
import net.levente.cantotrack.mobile.data.api.Notification
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.call
import net.levente.cantotrack.mobile.ui.toUiText

data class NotificationsState(
    val items: List<Notification> = emptyList(),
    val unreadOnly: Boolean = false,
    val unread: Int = 0,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val page: Int = 1,
    val pages: Int = 1,
    val error: UiText? = null,
    val message: UiText? = null,
) {
    val hasMore: Boolean get() = page < pages
}

/** The bell: what I was told about, newest first, and marking it read. */
class NotificationsViewModel(private val container: AppContainer) : ViewModel() {
    private val api = container.api
    private val sessions = container.sessions
    private val _state = MutableStateFlow(NotificationsState())
    val state: StateFlow<NotificationsState> = _state.asStateFlow()

    init {
        load()
    }

    fun setUnreadOnly(value: Boolean) {
        if (value == state.value.unreadOnly) return
        _state.update { it.copy(unreadOnly = value, items = emptyList()) }
        load()
    }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        val unreadOnly = state.value.unreadOnly
        _state.update { it.copy(loading = !refreshing && it.items.isEmpty(), refreshing = refreshing, error = null) }
        viewModelScope.launch {
            try {
                val page = sessions.call { api.notifications(it, unreadOnly = unreadOnly) }
                container.news.set(page.meta.unread)
                _state.update { it.copy(items = page.data, page = page.meta.page, pages = page.meta.pages, unread = page.meta.unread) }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loading = false, refreshing = false) }
            }
        }
    }

    fun loadMore() {
        val current = state.value
        if (!current.hasMore || current.loadingMore || current.loading) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            try {
                val page = sessions.call { api.notifications(it, page = current.page + 1, unreadOnly = current.unreadOnly) }
                _state.update { state ->
                    val known = state.items.map { it.id }.toSet()
                    state.copy(items = state.items + page.data.filter { it.id !in known }, page = page.meta.page, pages = page.meta.pages)
                }
            } catch (e: ApiException) {
                _state.update { it.copy(error = e.toUiText()) }
            } finally {
                _state.update { it.copy(loadingMore = false) }
            }
        }
    }

    /** Opened: it is read now, here and on the web's bell. */
    fun markRead(notification: Notification) {
        if (notification.read) return
        markLocally { if (it.id == notification.id) it.copy(read = true) else it }
        viewModelScope.launch {
            try {
                sessions.call { api.readNotification(it, notification.id) }
            } catch (e: ApiException) {
                // Opening the ticket marks it read on the server as well.
            }
        }
    }

    fun markAllRead() {
        markLocally { it.copy(read = true) }
        viewModelScope.launch {
            try {
                sessions.call { api.readAllNotifications(it) }
                container.news.set(0)
                if (state.value.unreadOnly) _state.update { it.copy(items = emptyList()) }
            } catch (e: ApiException) {
                _state.update { it.copy(message = e.toUiText()) }
                load(refreshing = true)
            }
        }
    }

    private fun markLocally(change: (Notification) -> Notification) {
        _state.update { state ->
            val items = state.items.map(change)
            val unread = (state.unread - state.items.count { !it.read } + items.count { !it.read }).coerceAtLeast(0)
            container.news.set(unread)
            state.copy(items = items, unread = unread)
        }
    }

    fun messageShown() = _state.update { it.copy(message = null) }
}

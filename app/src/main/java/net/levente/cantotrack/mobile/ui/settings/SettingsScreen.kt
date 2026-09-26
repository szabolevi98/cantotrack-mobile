package net.levente.cantotrack.mobile.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import net.levente.cantotrack.mobile.BuildConfig
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.session.Session
import net.levente.cantotrack.mobile.ui.components.Avatar
import net.levente.cantotrack.mobile.ui.components.CtButton
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.SectionLabel
import net.levente.cantotrack.mobile.ui.theme.CtTheme

@Composable
fun SettingsScreen(session: Session, onSignOut: () -> Unit, onBack: () -> Unit) {
    val colors = CtTheme.colors
    val context = LocalContext.current
    var confirming by remember { mutableStateOf(false) }
    val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val user = session.user

    Column(Modifier.fillMaxSize()) {
        CtHeader(title = stringResource(R.string.settings_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CtCard(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.settings_account))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(user.initials, size = 48.dp, background = colors.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(user.name, style = MaterialTheme.typography.titleMedium)
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                        Text(roleName(user.role), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = colors.border)
                Setting(Icons.Rounded.Dns, stringResource(R.string.settings_server), session.baseUrl)
                HorizontalDivider(color = colors.border)
                Setting(
                    if (notifications) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff,
                    stringResource(R.string.settings_notifications),
                    stringResource(if (notifications) R.string.settings_notifications_on else R.string.settings_notifications_off),
                    onClick = {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                        }
                        context.startActivity(intent)
                    },
                )
            }

            CtCard(Modifier.fillMaxWidth()) {
                SectionLabel(stringResource(R.string.settings_signed_in_here))
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_token_explained), style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Spacer(Modifier.height(16.dp))
                CtButton(
                    stringResource(R.string.settings_sign_out),
                    onClick = { confirming = true },
                    icon = Icons.AutoMirrored.Rounded.Logout,
                    color = colors.danger,
                    outlined = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = colors.muted,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.settings_sign_out_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_text)) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onSignOut() }) { Text(stringResource(R.string.settings_sign_out), color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Setting(icon: ImageVector, label: String, value: String, onClick: (() -> Unit)? = null) {
    val colors = CtTheme.colors
    Surface(onClick = onClick ?: {}, enabled = onClick != null, color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.muted)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun roleName(role: String): String = stringResource(
    when (role) {
        "admin" -> R.string.role_admin
        "guest" -> R.string.role_guest
        else -> R.string.role_member
    },
)

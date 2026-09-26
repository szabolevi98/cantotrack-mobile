package net.levente.cantotrack.mobile.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Status
import net.levente.cantotrack.mobile.ui.UiText
import net.levente.cantotrack.mobile.ui.theme.CtTheme

/**
 * The gradient header every screen starts with, like the web app's sidebar.
 * It runs under the status bar; [content] goes below the title.
 */
@Composable
fun CtHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val colors = CtTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.header)
            .statusBarsPadding()
            .padding(start = if (onBack != null) 4.dp else 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = Color.White)
                }
            }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.sidebarText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            actions()
        }
        Column(Modifier.padding(start = if (onBack != null) 16.dp else 0.dp, end = 12.dp), content = content)
    }
}

/** A card with the web app's soft border and shadow. */
@Composable
fun CtCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    border: Color = CtTheme.colors.border,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val shadow = if (CtTheme.colors.dark) Color.Transparent else Color(0x2216202F)
    val cardModifier = modifier.shadow(4.dp, shape, ambientColor = shadow, spotColor = shadow)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = cardModifier, shape = shape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(modifier = cardModifier, shape = shape, color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, border)) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** The mark — a clock's ring running into a tick — on its blue tile, beside the name. */
@Composable
fun CtLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_mark), contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text("CantoTrack", style = MaterialTheme.typography.headlineSmall, color = Color.White)
    }
}

/** A large button; [outlined] for the second choice beside it. */
@Composable
fun CtButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    color: Color = CtTheme.colors.primary,
    icon: ImageVector? = null,
    outlined: Boolean = false,
) {
    val content: @Composable RowScope.() -> Unit = {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = if (outlined) color else Color.White, strokeWidth = 2.5.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (outlined) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.height(52.dp),
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
            contentPadding = PaddingValues(horizontal = 16.dp),
            content = content,
        )
    } else {
        val onColor = if (CtTheme.colors.dark && color == CtTheme.colors.primary) Color(0xFF0B1628) else Color.White
        Button(
            onClick = onClick,
            enabled = enabled && !loading,
            modifier = modifier.height(52.dp),
            shape = MaterialTheme.shapes.medium,
            colors = ButtonDefaults.buttonColors(
                containerColor = color,
                contentColor = onColor,
                disabledContainerColor = color.copy(alpha = 0.35f),
                disabledContentColor = onColor,
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            content = content,
        )
    }
}

/** The person's initials in a circle, as the web's top bar shows them. */
@Composable
fun Avatar(initials: String, modifier: Modifier = Modifier, size: Dp = 36.dp, background: Color = Color.White.copy(alpha = 0.18f)) {
    Box(
        modifier.size(size).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

/** The small upper-case group label of the web sidebar. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = CtTheme.colors.muted)
}

/** A status in its category's colour: grey to do, blue under way, green done. */
@Composable
fun StatusChip(status: Status, modifier: Modifier = Modifier, trailing: ImageVector? = null) {
    val colors = CtTheme.colors
    val (fg, bg) = when (status.category) {
        "done" -> colors.success to colors.successSoft
        "in_progress" -> colors.primary to colors.primarySoft
        else -> colors.slate to colors.slateSoft
    }
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(status.name, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (trailing != null) {
            Icon(trailing, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
    }
}

/** The ticket's type as the web draws it: a bug in red, a story in green, a task in blue. */
@Composable
fun TypeIcon(type: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val colors = CtTheme.colors
    val (icon, tint) = when (type) {
        "bug" -> Icons.Rounded.BugReport to colors.danger
        "story" -> Icons.Rounded.BookmarkBorder to colors.success
        "epic" -> Icons.Rounded.Bolt to colors.violet
        "subtask" -> Icons.Rounded.SubdirectoryArrowRight to colors.muted
        else -> Icons.Rounded.CheckBox to colors.primary
    }
    Icon(icon, contentDescription = type, tint = tint, modifier = modifier.size(size))
}

@Composable
fun PriorityIcon(priority: String, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val colors = CtTheme.colors
    val (icon, tint) = when (priority) {
        "urgent", "highest" -> Icons.Rounded.KeyboardDoubleArrowUp to colors.danger
        "high" -> Icons.Rounded.KeyboardArrowUp to colors.warning
        "low" -> Icons.Rounded.KeyboardArrowDown to colors.primary
        "lowest" -> Icons.Rounded.KeyboardDoubleArrowDown to colors.primary
        else -> Icons.Rounded.Remove to colors.muted
    }
    Icon(icon, contentDescription = priority, tint = tint, modifier = modifier.size(size))
}

/** Centered icon and text for an empty list. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val colors = CtTheme.colors
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(colors.primarySoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.muted, textAlign = TextAlign.Center)
    }
}

/** A failed request, said in a red box. */
@Composable
fun ErrorBanner(message: UiText, modifier: Modifier = Modifier) {
    val colors = CtTheme.colors
    Row(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(colors.dangerSoft).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = colors.danger)
        Spacer(Modifier.width(8.dp))
        Text(message.asString(), style = MaterialTheme.typography.bodyMedium, color = colors.text)
    }
}

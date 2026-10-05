package net.levente.cantotrack.mobile.ui.newticket

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.User
import net.levente.cantotrack.mobile.ui.PRIORITIES
import net.levente.cantotrack.mobile.ui.TYPES
import net.levente.cantotrack.mobile.ui.components.ChoiceDialog
import net.levente.cantotrack.mobile.ui.components.CtButton
import net.levente.cantotrack.mobile.ui.components.CtCard
import net.levente.cantotrack.mobile.ui.components.CtHeader
import net.levente.cantotrack.mobile.ui.components.DatePickDialog
import net.levente.cantotrack.mobile.ui.components.ErrorBanner
import net.levente.cantotrack.mobile.ui.components.PersonPickerDialog
import net.levente.cantotrack.mobile.ui.components.PriorityIcon
import net.levente.cantotrack.mobile.ui.components.SectionLabel
import net.levente.cantotrack.mobile.ui.components.TypeIcon
import net.levente.cantotrack.mobile.ui.components.rememberPhotoSource
import net.levente.cantotrack.mobile.ui.priorityName
import net.levente.cantotrack.mobile.ui.shortDate
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import net.levente.cantotrack.mobile.ui.typeName
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewTicketScreen(
    viewModel: NewTicketViewModel,
    user: User,
    snackbar: SnackbarHostState,
    onCreated: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = CtTheme.colors
    var pickingProject by rememberSaveable { mutableStateOf(false) }
    var pickingPerson by rememberSaveable { mutableStateOf(false) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val noCamera = stringResource(R.string.attachment_no_camera)
    val source = rememberPhotoSource(onPicked = viewModel::addFile, onNoCamera = { viewModel.update { it.copy(error = net.levente.cantotrack.mobile.ui.UiText.Raw(noCamera)) } })

    val message = state.message?.asString()
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.update { it.copy(message = null) }
        }
    }
    LaunchedEffect(state.created) { state.created?.let(onCreated) }

    Column(Modifier.fillMaxSize()) {
        CtHeader(title = stringResource(R.string.new_ticket), subtitle = state.project?.let { code -> state.projects?.firstOrNull { it.code == code }?.name }, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            state.error?.let { ErrorBanner(it) }

            CtCard(Modifier.fillMaxWidth()) {
                Field(Icons.Rounded.Folder, stringResource(R.string.new_ticket_project), state.project?.let { code ->
                    state.projects?.firstOrNull { it.code == code }?.let { "${it.code} · ${it.name}" } ?: code
                } ?: if (state.projects == null) "…" else stringResource(R.string.new_ticket_no_project)) { pickingProject = true }
                HorizontalDivider(color = colors.border)
                Field(Icons.Rounded.Person, stringResource(R.string.ticket_assignee), state.assignee?.let {
                    if (it.id == user.id) stringResource(R.string.person_me, it.name) else it.name
                } ?: stringResource(R.string.ticket_unassigned)) { viewModel.loadPeople(); pickingPerson = true }
                HorizontalDivider(color = colors.border)
                Field(Icons.Rounded.Event, stringResource(R.string.ticket_due), state.dueOn?.let(::shortDate) ?: "—", onClear = state.dueOn?.let { { viewModel.update { s -> s.copy(dueOn = null) } } }) {
                    pickingDate = true
                }
            }

            SectionLabel(stringResource(R.string.new_ticket_type))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TYPES.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.update { it.copy(type = type) } },
                        leadingIcon = { TypeIcon(type) },
                        label = { Text(typeName(type)) },
                    )
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = { value -> viewModel.update { it.copy(title = value.take(250)) } },
                label = { Text(stringResource(R.string.new_ticket_title)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = { value -> viewModel.update { it.copy(description = value.take(20000)) } },
                label = { Text(stringResource(R.string.new_ticket_description)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel(stringResource(R.string.new_ticket_priority))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRIORITIES.forEach { priority ->
                    FilterChip(
                        selected = state.priority == priority,
                        onClick = { viewModel.update { it.copy(priority = priority) } },
                        leadingIcon = { PriorityIcon(priority) },
                        label = { Text(priorityName(priority)) },
                    )
                }
            }

            SectionLabel(stringResource(R.string.ticket_attachments, state.files.size))
            if (state.files.isNotEmpty() || state.preparing) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.files.forEach { pending ->
                        Box(Modifier.size(96.dp).clip(RoundedCornerShape(10.dp)).background(colors.slateSoft)) {
                            val preview = pending.preview
                            if (preview != null) {
                                Image(preview, contentDescription = pending.file.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            } else {
                                Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.AutoMirrored.Rounded.InsertDriveFile, null, tint = colors.muted)
                                    Text(pending.file.name, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Surface(
                                onClick = { viewModel.removeFile(pending) },
                                shape = RoundedCornerShape(50),
                                color = Color.Black.copy(alpha = 0.55f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(26.dp),
                            ) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                    if (state.preparing) {
                        Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CtButton(stringResource(R.string.attachment_camera), onClick = source.camera, icon = Icons.Rounded.PhotoCamera, outlined = true, modifier = Modifier.weight(1f))
                CtButton(stringResource(R.string.attachment_gallery), onClick = source.gallery, icon = Icons.Rounded.PhotoLibrary, outlined = true, modifier = Modifier.weight(1f))
                CtButton(stringResource(R.string.attachment_file), onClick = source.file, icon = Icons.Rounded.AttachFile, outlined = true, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }

        Surface(color = colors.surface, shadowElevation = 8.dp) {
            CtButton(
                stringResource(R.string.new_ticket_create),
                onClick = viewModel::save,
                enabled = state.canSave,
                loading = state.saving,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }

    if (pickingProject) {
        val projects = state.projects.orEmpty()
        ChoiceDialog(
            title = stringResource(R.string.new_ticket_project),
            options = projects.map { it.code },
            selected = state.project,
            label = { code -> projects.first { it.code == code }.let { "${it.code} · ${it.name}" } },
            onPick = { code -> pickingProject = false; viewModel.update { it.copy(project = code) } },
            onDismiss = { pickingProject = false },
        )
    }
    if (pickingPerson) {
        PersonPickerDialog(
            people = state.people,
            meId = user.id,
            current = state.assignee?.id,
            onPick = { person -> pickingPerson = false; viewModel.update { it.copy(assignee = person) } },
            onDismiss = { pickingPerson = false },
        )
    }
    if (pickingDate) {
        DatePickDialog(
            initial = state.dueOn?.let(LocalDate::parse),
            notBefore = LocalDate.now(),
            onPick = { day -> pickingDate = false; viewModel.update { it.copy(dueOn = day.toString()) } },
            onDismiss = { pickingDate = false },
        )
    }
}

/** A line of the form: what, and its value, tapped to change. */
@Composable
fun Field(icon: ImageVector, label: String, value: String, onClear: (() -> Unit)? = null, onClick: () -> Unit) {
    val colors = CtTheme.colors
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodySmall, color = colors.muted)
                Text(value, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (onClear != null) {
                IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, stringResource(R.string.clear), tint = colors.muted) }
            }
        }
    }
}

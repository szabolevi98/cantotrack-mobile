package net.levente.cantotrack.mobile.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.levente.cantotrack.mobile.R
import net.levente.cantotrack.mobile.data.api.Person
import net.levente.cantotrack.mobile.data.files.Images
import net.levente.cantotrack.mobile.ui.theme.CtTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Any day, the days to come too: a due date, the last day of a holiday. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickDialog(initial: LocalDate?, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, notBefore: LocalDate? = null) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) =
                notBefore == null || !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(notBefore)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                picker.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(picker) }
}

/**
 * Who a ticket goes to: me with one tap, nobody, or anybody from the list,
 * found by a few letters of their name.
 */
@Composable
fun PersonPickerDialog(
    people: List<Person>?,
    meId: Int,
    current: Int?,
    onPick: (Person?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val colors = CtTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ticket_assignee)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text(stringResource(R.string.person_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.size(8.dp))
                if (people == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), strokeWidth = 2.dp)
                } else {
                    val me = people.firstOrNull { it.id == meId }
                    val shown = people
                        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
                        .sortedBy { if (it.id == meId) 0 else 1 }
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        if (query.isBlank()) {
                            item {
                                PersonRow(stringResource(R.string.ticket_unassigned), current == null) { onPick(null) }
                                HorizontalDivider(color = colors.border)
                            }
                        }
                        items(shown, key = { it.id }) { person ->
                            PersonRow(
                                if (person == me) stringResource(R.string.person_me, person.name) else person.name,
                                current == person.id,
                            ) { onPick(person) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun PersonRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = CtTheme.colors.primary) else Spacer(Modifier.width(24.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/** One choice of a few, in a dialog. */
@Composable
fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(options) { option -> PersonRow(label(option), option == selected) { onPick(option) } }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A photo from the camera, or a picture or file from the phone, for a ticket. */
class PhotoSource(val camera: () -> Unit, val gallery: () -> Unit, val file: () -> Unit)

@Composable
fun rememberPhotoSource(onPicked: (Uri) -> Unit, onNoCamera: () -> Unit = {}): PhotoSource {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        pending?.let { if (taken) picked(Uri.parse(it)) }
        pending = null
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(picked) }
    val document = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(picked) }
    return remember(context) {
        PhotoSource(
            camera = {
                val uri = Images.newPhoto(context)
                pending = uri.toString()
                try {
                    camera.launch(uri)
                } catch (e: ActivityNotFoundException) {
                    pending = null
                    onNoCamera()
                }
            },
            gallery = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            file = { document.launch(arrayOf("*/*")) },
        )
    }
}

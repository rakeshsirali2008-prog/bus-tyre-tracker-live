package com.bustyre.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.bustyre.tracker.data.CONDITION_NEW
import com.bustyre.tracker.data.CONDITION_RESOLE
import com.bustyre.tracker.data.FleetRepository
import com.bustyre.tracker.data.SPARE
import com.bustyre.tracker.data.TYRE_POSITIONS
import com.bustyre.tracker.data.TyreEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

enum class StepKind { SPARE_PRESENT, CONDITION, RATING }

/** The next thing the first-visit popups must ask, in tyre order. Null when the vehicle is complete. */
fun nextStep(tyres: List<TyreEntity>): Pair<String, StepKind>? {
    for (position in TYRE_POSITIONS) {
        val tyre = tyres.firstOrNull { it.position == position } ?: continue
        if (position == SPARE) {
            if (tyre.present == null) return position to StepKind.SPARE_PRESENT
            if (tyre.present == false) continue
        }
        if (tyre.condition == null) return position to StepKind.CONDITION
        if (tyre.rating == null) return position to StepKind.RATING
    }
    return null
}

@Composable
fun DetailScreen(
    repo: FleetRepository,
    number: String,
    stars: Int,
    onBlockChange: (Boolean) -> Unit
) {
    val tyres by repo.observeTyres(number).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<Pair<String, StepKind>?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    val wizard = nextStep(tyres)
    val blocked = wizard != null
    val active = editing ?: wizard

    LaunchedEffect(blocked) { onBlockChange(blocked) }
    DisposableEffect(Unit) { onDispose { onBlockChange(false) } }

    val visible = if (stars == 0) tyres else tyres.filter { it.rating == stars }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(number, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            if (stars == 0) "All tyres" else "Tyres rated $stars ⭐",
            style = MaterialTheme.typography.bodyMedium
        )
        if (blocked) {
            Spacer(Modifier.padding(4.dp))
            Text(
                "Enter every tyre to continue.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.padding(6.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.position }) { tyre ->
                Box {
                    Card(
                        onClick = { menuFor = tyre.position },
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ratingColor(tyre.rating)
                                ?: MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tyre.position,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(tyreSummary(tyre), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = menuFor == tyre.position,
                        onDismissRequest = { menuFor = null }
                    ) {
                        if (tyre.position == SPARE) {
                            DropdownMenuItem(
                                text = { Text("Spare present?") },
                                onClick = {
                                    menuFor = null
                                    editing = tyre.position to StepKind.SPARE_PRESENT
                                }
                            )
                        }
                        if (tyre.position != SPARE || tyre.present == true) {
                            DropdownMenuItem(
                                text = { Text("Condition (New / Resole)") },
                                onClick = {
                                    menuFor = null
                                    editing = tyre.position to StepKind.CONDITION
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Rating (1 ⭐ / 2 ⭐ / 3 ⭐)") },
                                onClick = {
                                    menuFor = null
                                    editing = tyre.position to StepKind.RATING
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    active?.let { (position, kind) ->
        val isEditing = editing != null
        StepDialog(
            repo = repo,
            scope = scope,
            number = number,
            position = position,
            kind = kind,
            dismissable = isEditing,
            onFinished = { editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun StepDialog(
    repo: FleetRepository,
    scope: CoroutineScope,
    number: String,
    position: String,
    kind: StepKind,
    dismissable: Boolean,
    onFinished: () -> Unit,
    onDismiss: () -> Unit
) {
    val title: String
    val labels: List<String>
    when (kind) {
        StepKind.SPARE_PRESENT -> {
            title = "Is spare present?"
            labels = listOf("Yes, spare is present", "No, spare is not present")
        }
        StepKind.CONDITION -> {
            title = "$position · Condition"
            labels = listOf("New", "Resole")
        }
        StepKind.RATING -> {
            title = "$position · Rating"
            labels = listOf("1 ⭐ Best", "2 ⭐ Good", "3 ⭐ Worst")
        }
    }

    fun choose(index: Int) {
        // Saves straight away. The scope belongs to DetailScreen, so the write finishes
        // even though this dialog closes right after.
        scope.launch {
            when (kind) {
                StepKind.SPARE_PRESENT -> repo.setSparePresent(number, index == 0)
                StepKind.CONDITION -> repo.setCondition(
                    number, position, if (index == 0) CONDITION_NEW else CONDITION_RESOLE
                )
                StepKind.RATING -> repo.setRating(number, position, index + 1)
            }
        }
        onFinished()
    }

    AlertDialog(
        onDismissRequest = { if (dismissable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = dismissable,
            dismissOnClickOutside = dismissable
        ),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    Button(
                        onClick = { choose(index) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        },
        confirmButton = {}
    )
}

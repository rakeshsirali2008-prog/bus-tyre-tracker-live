package com.bustyre.tracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bustyre.tracker.data.FleetRepository

/** Start screen: every vehicle, large font, numeric order. */
@Composable
fun VehicleListScreen(repo: FleetRepository, onOpen: (String) -> Unit) {
    val vehicles by repo.observeVehicles().collectAsState(initial = emptyList())

    if (vehicles.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("No vehicles yet.", style = MaterialTheme.typography.titleMedium)
            Text(
                "Open the menu and choose Add Vehicle.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(vehicles, key = { it.number }) { vehicle ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(vehicle.number) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    vehicle.number,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }
}

/** 1 / 2 / 3 star table: each vehicle with only the tyres rated [stars]. */
@Composable
fun StarListScreen(repo: FleetRepository, stars: Int, onOpen: (String) -> Unit) {
    val rows by repo.observeStarRows(stars).collectAsState(initial = emptyList())
    val colour = ratingColor(stars)

    if (rows.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Text("No tyres rated $stars ⭐", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(rows, key = { it.vehicle }) { row ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpen(row.vehicle) },
                colors = CardDefaults.cardColors(
                    containerColor = colour ?: MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        row.vehicle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        row.tyres.joinToString("  ·  ") { it.position },
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

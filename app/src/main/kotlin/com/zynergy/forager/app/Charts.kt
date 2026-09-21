package com.zynergy.forager.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zynergy.forager.domain.Seasonality
import java.time.Month

private val BAR = Color(0xFF2E7D32)
private val BAR_CHOSEN = Color(0xFFC62828)
private val BASELINE = Color(0xFFBDBDBD)
private val BAR_THIN = Color(0xFFA5C8A7)

/**
 * Twelve bars, one per month, with the planned month picked out.
 *
 * Scaled against the busiest month rather than a fixed ceiling, so the shape of the year is
 * readable for a taxon with 20 records and one with 20,000. The caption states what a bar is a
 * count of, because a tall bar in a foraging app invites being read as "good chance here", and the
 * honest reading is "this is when people filed records".
 */
@Composable
fun SeasonalityChart(
    seasonality: Seasonality,
    chosenMonth: Month,
    modifier: Modifier = Modifier,
) {
    val counts = Month.entries.map { seasonality.countsByMonth[it] ?: 0 }
    val peak = counts.maxOrNull() ?: 0

    Column(modifier = modifier.testTag("seasonality-chart")) {
        Text(
            "${seasonality.species.displayName}: when it is recorded here",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            when {
                peak == 0 ->
                    "No records in this area at all, so nothing can be said about timing."
                !seasonality.hasEnoughRecordsForPattern ->
                    "Only ${seasonality.total} records here, which is too few to read as a season. " +
                        "The bars below are those few records, not a pattern. Try a bigger area."
                else ->
                    "${seasonality.total} records, busiest in ${seasonality.busiestMonth?.displayName()}. " +
                        "Bars count records people filed, not fruitings."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("chart-caption"),
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("chart-canvas"),
        ) {
            val slot = size.width / 12f
            val barWidth = slot * 0.6f
            drawLine(
                color = BASELINE,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 2f,
            )
            // Below the evidence threshold the bars are drawn muted and at half scale, so a
            // three-record year cannot be mistaken at a glance for a strong season.
            val thin = !seasonality.hasEnoughRecordsForPattern
            counts.forEachIndexed { index, count ->
                val raw = if (peak == 0) 0f else count.toFloat() / peak
                val fraction = if (thin) raw * 0.45f else raw
                val barHeight = fraction * (size.height - 4f)
                val left = index * slot + (slot - barWidth) / 2f
                drawRect(
                    color = when {
                        thin -> BAR_THIN
                        Month.entries[index] == chosenMonth -> BAR_CHOSEN
                        else -> BAR
                    },
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Month.entries.forEach { month ->
                Text(
                    month.initial(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (month == chosenMonth) BAR_CHOSEN else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

fun Month.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

fun Month.initial(): String = when (this) {
    Month.JANUARY -> "J"; Month.FEBRUARY -> "F"; Month.MARCH -> "M"; Month.APRIL -> "A"
    Month.MAY -> "M"; Month.JUNE -> "J"; Month.JULY -> "J"; Month.AUGUST -> "A"
    Month.SEPTEMBER -> "S"; Month.OCTOBER -> "O"; Month.NOVEMBER -> "N"; Month.DECEMBER -> "D"
}

package com.school.servicemonitor.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.school.servicemonitor.ui.theme.AppColors

/** A circle with the student's initials, tinted with the accent color. */
@Composable
fun InitialsAvatar(
    first: String,
    last: String,
    diameter: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val letters = (first.trim().take(1) + last.trim().take(1)).uppercase()
    Box(
        modifier = modifier
            .size(diameter)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.22f))
            .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letters,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = (diameter.value * 0.36f).sp,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** A ring showing how much of the assigned service is already done. */
@Composable
fun HoursRing(
    outstandingMinutes: Int,
    completedMinutes: Int,
    diameter: Dp = 150.dp,
    modifier: Modifier = Modifier
) {
    val accent = MaterialTheme.colorScheme.primary
    val total = outstandingMinutes + completedMinutes
    val fraction = if (total <= 0) 0f else completedMinutes.toFloat() / total

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val stroke = 12.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arc = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(
                color = AppColors.Border,
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = topLeft, size = arc, style = Stroke(stroke)
            )
            if (fraction > 0f) {
                drawArc(
                    color = accent,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false,
                    topLeft = topLeft, size = arc,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                formatMinutes(outstandingMinutes),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                "remaining",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
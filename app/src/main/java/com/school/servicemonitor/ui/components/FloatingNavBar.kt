package com.school.servicemonitor.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.school.servicemonitor.ui.theme.AppColors

data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val alsoSelectedOn: Set<String> = emptySet()
)

@Composable
fun FloatingNavBar(
    items: List<NavItem>,
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = AppColors.Card.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route || currentRoute in item.alsoSelectedOn
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (selected) accent.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(item.route) }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        item.label,
                        fontSize = 10.sp,
                        maxLines = 1,
                        color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
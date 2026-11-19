package com.anywall.converter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anywall.converter.domain.model.FormatCategory
import com.anywall.converter.ui.theme.CategoryArchiveColor
import com.anywall.converter.ui.theme.CategoryDocumentColor
import com.anywall.converter.ui.theme.CategoryImageColor
import com.anywall.converter.ui.theme.CategoryMediaColor

/**
 * Tarjeta de acceso rápido por categoría en `HomeScreen`.
 */
@Composable
fun CategoryQuickCard(
    category: FormatCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = categoryAccentColor(category)
    val icon = categoryIcon(category)

    ElevatedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = category.displayNameEs,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = category.displayNameEs,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = category.descriptionEs,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

fun categoryAccentColor(category: FormatCategory): Color {
    return when (category) {
        FormatCategory.IMAGE -> CategoryImageColor
        FormatCategory.DOCUMENT -> CategoryDocumentColor
        FormatCategory.MEDIA -> CategoryMediaColor
        FormatCategory.ARCHIVE -> CategoryArchiveColor
    }
}

fun categoryIcon(category: FormatCategory): ImageVector {
    return when (category) {
        FormatCategory.IMAGE -> Icons.Default.Image
        FormatCategory.DOCUMENT -> Icons.Default.Description
        FormatCategory.MEDIA -> Icons.Default.Movie
        FormatCategory.ARCHIVE -> Icons.Default.FolderZip
    }
}

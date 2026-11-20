package com.anywall.converter.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anywall.converter.core.LocalThumbnailLoader
import com.anywall.converter.domain.model.SupportedFormat

/**
 * Contenedor que carga de forma asíncrona una miniatura 100% local (imagen, portada PDF o fotograma de video)
 * y muestra la insignia del formato como respaldo inmediato.
 */
@Composable
fun ThumbnailPreviewBox(
    uri: Uri,
    format: SupportedFormat,
    thumbnailLoader: LocalThumbnailLoader?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    fallbackContent: (@Composable () -> Unit)? = null
) {
    val accent = categoryAccentColor(format.category)

    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = uri, key2 = format) {
        value = thumbnailLoader?.loadThumbnail(uri = uri, format = format)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
    ) {
        val currentBmp = bitmap
        if (currentBmp != null && !currentBmp.isRecycled) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = format.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (fallbackContent != null) {
            fallbackContent()
        } else {
            Text(
                text = format.label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
        }
    }
}

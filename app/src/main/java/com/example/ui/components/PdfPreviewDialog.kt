package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class PdfPreviewRequest(
    val file: File,
    val title: String,
    val chooserTitle: String,
    val whatsAppCaption: String = ""
)

@Composable
fun PdfPreviewDialog(
    request: PdfPreviewRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var renderedPages by remember(request.file.absolutePath, request.file.lastModified()) {
        mutableStateOf<List<ImageBitmap>>(emptyList())
    }
    var isLoading by remember(request.file.absolutePath, request.file.lastModified()) {
        mutableStateOf(true)
    }
    var renderError by remember(request.file.absolutePath, request.file.lastModified()) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(request.file.absolutePath, request.file.lastModified()) {
        isLoading = true
        renderError = null
        val (pages, err) = withContext(Dispatchers.IO) {
            val outBitmaps = mutableListOf<Bitmap>()
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                if (!request.file.exists() || request.file.length() <= 0L) {
                    return@withContext Pair(emptyList<ImageBitmap>(), "PDF file could not be generated or is empty.")
                }
                pfd = ParcelFileDescriptor.open(request.file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                if (pageCount <= 0) {
                    return@withContext Pair(emptyList<ImageBitmap>(), "PDF contains no pages.")
                }
                for (i in 0 until pageCount) {
                    var page: PdfRenderer.Page? = null
                    try {
                        page = renderer.openPage(i)
                        // Cap target width at 840px to guarantee sharp preview without OOM on any Android phone
                        val scale = (840f / page.width.coerceAtLeast(1)).coerceIn(1.0f, 1.8f)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        canvas.drawColor(AndroidColor.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        outBitmaps.add(bmp)
                    } finally {
                        try { page?.close() } catch (_: Throwable) {}
                    }
                }
                Pair(outBitmaps.map { it.asImageBitmap() }, null)
            } catch (t: Throwable) {
                Pair(emptyList<ImageBitmap>(), t.message ?: "Unable to open PDF preview")
            } finally {
                try { renderer?.close() } catch (_: Throwable) {}
                try { pfd?.close() } catch (_: Throwable) {}
            }
        }
        renderedPages = pages
        renderError = err
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = WaterSkySurface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("pdf_preview_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(
                    color = WaterNavyPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = request.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (renderedPages.isNotEmpty()) {
                                        "PDF Review (${renderedPages.size} page${if (renderedPages.size > 1) "s" else ""}) • شیئر کرنے سے پہلے چیک کریں"
                                    } else {
                                        "PDF Document Preview"
                                    },
                                    color = WaterSkyLight,
                                    fontSize = 9.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("pdf_preview_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close PDF Preview",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFE2E8F0)),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = WaterNavyPrimary)
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Opening PDF preview...",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        renderError != null -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = renderError ?: "Error loading PDF",
                                    color = ErrorRed,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        else -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(renderedPages) { index, pageBitmap ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(6.dp)) {
                                            Image(
                                                bitmap = pageBitmap,
                                                contentDescription = "PDF Page ${index + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                            if (renderedPages.size > 1) {
                                                Text(
                                                    text = "Page ${index + 1} of ${renderedPages.size}",
                                                    fontSize = 9.5.sp,
                                                    color = TextSecondaryDark,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier
                                                        .align(Alignment.End)
                                                        .padding(top = 4.dp, end = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Bar (WhatsApp & General Share)
                Surface(
                    color = Color.White,
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                PdfGenerator.sharePdfToWhatsApp(
                                    context = context,
                                    file = request.file,
                                    caption = request.whatsAppCaption,
                                    chooserTitle = request.chooserTitle
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessGreen,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("pdf_preview_whatsapp_btn")
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("WhatsApp", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                PdfGenerator.sharePdf(
                                    context = context,
                                    file = request.file,
                                    chooserTitle = request.chooserTitle
                                )
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WaterNavyPrimary,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("pdf_preview_share_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share PDF", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

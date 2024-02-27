package com.hippo.ehviewer.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil3.BitmapImage
import coil3.Image as CoilImage
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.data.BaseGalleryInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.download.archiveFile
import com.hippo.ehviewer.gallery.ArchivePageLoader
import com.hippo.ehviewer.gallery.EhPageLoader
import com.hippo.ehviewer.ui.LockDrawer
import com.hippo.ehviewer.ui.tools.Deferred
import com.ramcosta.composedestinations.annotation.Destination
import eu.kanade.tachiyomi.source.model.Page.State.DOWNLOAD_IMAGE
import eu.kanade.tachiyomi.source.model.Page.State.ERROR
import eu.kanade.tachiyomi.source.model.Page.State.LOAD_PAGE
import eu.kanade.tachiyomi.source.model.Page.State.QUEUE
import eu.kanade.tachiyomi.source.model.Page.State.READY

@Destination
@Composable
fun ReaderScreen(info: BaseGalleryInfo, page: Int = -1) {
    LockDrawer(true)
    val pageLoader = remember {
        val dir = DownloadManager.getDownloadInfo(info.gid)?.archiveFile
        dir?.let { ArchivePageLoader(it, info.gid, page) } ?: EhPageLoader(info, page)
    }
    DisposableEffect(Unit) {
        pageLoader.start()
        onDispose {
            pageLoader.stop()
        }
    }
    Deferred({ pageLoader.awaitReady() }) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(pageLoader.mPages) { page ->
                pageLoader.request(page.index)
                val state by page.status.collectAsState()
                when (state) {
                    QUEUE, LOAD_PAGE -> {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(DEFAULT_ASPECT)) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    DOWNLOAD_IMAGE -> {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(DEFAULT_ASPECT)) {
                            val progress by page.progressFlow.collectAsState()
                            CircularProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    READY -> {
                        val image = page.image!!.innerImage!!
                        val painter = remember(image) { image.toPainter() }
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.FillWidth,
                        )
                    }
                    ERROR -> {
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(DEFAULT_ASPECT)) {
                            Column(
                                modifier = Modifier.align(Companion.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(text = page.errorMsg.orEmpty())
                                Button(onClick = { }) {
                                    Text(text = stringResource(id = R.string.action_retry))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun CoilImage.toPainter() = when (this) {
    is BitmapImage -> BitmapPainter(image = bitmap.asImageBitmap())
    else -> TODO()
}

private const val DEFAULT_ASPECT = 1 / 1.4125f
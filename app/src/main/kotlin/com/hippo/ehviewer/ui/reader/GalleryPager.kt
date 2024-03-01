package com.hippo.ehviewer.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hippo.ehviewer.gallery.PageLoader2
import eu.kanade.tachiyomi.ui.reader.model.ReaderPage
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType.CONTINUOUS_VERTICAL
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType.DEFAULT
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType.LEFT_TO_RIGHT
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType.RIGHT_TO_LEFT
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType.VERTICAL
import eu.kanade.tachiyomi.ui.reader.setting.ReadingModeType.WEBTOON
import me.saket.telephoto.zoomable.ZoomableState
import me.saket.telephoto.zoomable.zoomable

@Composable
fun GalleryPager(
    type: ReadingModeType,
    pagerState: PagerState,
    lazyListState: LazyListState,
    zoomableState: ZoomableState,
    pageLoader: PageLoader2,
    onMenuRegionClick: () -> Unit,
    item: @Composable (ReaderPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = pageLoader.mPages
    when (type) {
        DEFAULT, LEFT_TO_RIGHT, RIGHT_TO_LEFT -> {
            val zoomModifier = Modifier.fillMaxSize().zoomable(
                state = zoomableState,
                onClick = { onMenuRegionClick() },
            )
            HorizontalPager(
                state = pagerState,
                modifier = modifier,
                reverseLayout = type == RIGHT_TO_LEFT,
                key = { it },
            ) { index ->
                Box(modifier = zoomModifier, contentAlignment = Alignment.Center) {
                    item(items[index])
                }
            }
        }
        VERTICAL -> {
            val zoomModifier = Modifier.fillMaxSize().zoomable(
                state = zoomableState,
                onClick = { onMenuRegionClick() },
            )
            VerticalPager(
                state = pagerState,
                modifier = modifier,
                key = { it },
            ) { index ->
                Box(modifier = zoomModifier, contentAlignment = Alignment.Center) {
                    item(items[index])
                }
            }
        }
        WEBTOON, CONTINUOUS_VERTICAL -> {
            LazyColumn(
                modifier = modifier.zoomable(
                    state = zoomableState,
                    onClick = { onMenuRegionClick() },
                ),
                state = lazyListState,
                verticalArrangement = Arrangement.spacedBy(if (type != WEBTOON) 15.dp else 0.dp),
            ) {
                items(items, key = { it.index }) { page ->
                    item(page)
                }
            }
        }
    }
}

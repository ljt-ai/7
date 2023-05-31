/*
 * Copyright 2016 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.ehviewer.ui.scene

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DismissDirection
import androidx.compose.material3.DismissValue
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismiss
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDismissState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import arrow.core.partially1
import com.hippo.ehviewer.EhDB
import com.hippo.ehviewer.R
import com.hippo.ehviewer.Settings
import com.hippo.ehviewer.client.data.GalleryInfo
import com.hippo.ehviewer.dao.DownloadInfo
import com.hippo.ehviewer.download.DownloadManager
import com.hippo.ehviewer.ui.CommonOperations
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.addToFavorites
import com.hippo.ehviewer.ui.compose.Deferred
import com.hippo.ehviewer.ui.compose.DialogState
import com.hippo.ehviewer.ui.compose.data.GalleryInfoListItem
import com.hippo.ehviewer.ui.compose.rememberDialogState
import com.hippo.ehviewer.ui.compose.setMD3Content
import com.hippo.ehviewer.ui.confirmRemoveDownload
import com.hippo.ehviewer.ui.navToReader
import com.hippo.ehviewer.ui.removeFromFavorites
import com.hippo.ehviewer.ui.selectGalleryInfoAction
import com.hippo.ehviewer.ui.showMoveDownloadLabel
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import eu.kanade.tachiyomi.util.system.pxToDp
import kotlinx.coroutines.delay
import moe.tarsin.coroutines.runSuspendCatching
import my.nanihadesuka.compose.InternalLazyColumnScrollbar

class HistoryScene : BaseScene() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(inflater.context).apply {
            setMD3Content {
                val dialogState = rememberDialogState()
                dialogState.Handler()
                val coroutineScope = rememberCoroutineScope()
                val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
                val historyData = remember { Pager(PagingConfig(20, jumpThreshold = 40)) { EhDB.historyLazyList }.flow.cachedIn(lifecycleScope) }.collectAsLazyPagingItems()
                Scaffold(
                    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                    topBar = {
                        TopAppBar(
                            title = { Text(text = stringResource(id = R.string.history)) },
                            navigationIcon = {
                                IconButton(onClick = ::onNavigationClick) {
                                    Icon(imageVector = Icons.Default.Menu, contentDescription = null)
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    coroutineScope.launchIO {
                                        val clear = dialogState.show(
                                            confirmText = R.string.clear_all,
                                            dismissText = android.R.string.cancel,
                                            text = { Text(text = stringResource(id = R.string.clear_all_history)) },
                                        )
                                        if (clear) EhDB.clearHistoryInfo()
                                    }
                                }) {
                                    Icon(imageVector = Icons.Default.ClearAll, contentDescription = null)
                                }
                            },
                            scrollBehavior = scrollBehavior,
                        )
                    },
                ) { paddingValues ->
                    val state = rememberLazyListState()
                    Box {
                        LazyColumn(
                            modifier = Modifier.padding(horizontal = dimensionResource(id = R.dimen.gallery_list_margin_h), vertical = dimensionResource(id = R.dimen.gallery_list_margin_v)),
                            state = state,
                            contentPadding = paddingValues,
                            verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.gallery_list_interval)),
                        ) {
                            items(
                                count = historyData.itemCount,
                                key = historyData.itemKey(key = { item -> item.gid }),
                                contentType = historyData.itemContentType(),
                            ) { index ->
                                val info = historyData[index]
                                // TODO: item delete & add animation
                                // Bug tracker: https://issuetracker.google.com/issues/150812265
                                info?.let {
                                    val dismissState = rememberDismissState(
                                        confirmValueChange = {
                                            if (it == DismissValue.DismissedToStart) {
                                                coroutineScope.launchIO {
                                                    EhDB.deleteHistoryInfo(info)
                                                }
                                            }
                                            true
                                        },
                                    )

                                    SwipeToDismiss(
                                        state = dismissState,
                                        background = {},
                                        dismissContent = {
                                            // TODO: item delete & add animation
                                            // Bug tracker: https://issuetracker.google.com/issues/150812265
                                            GalleryInfoListItem(
                                                onClick = ::onItemClick.partially1(it),
                                                onLongClick = {
                                                    coroutineScope.launchIO {
                                                        val selected = dialogState.selectGalleryInfoAction(info)
                                                        withUIContext { handleLongClick(selected, info, dialogState) }
                                                    }
                                                },
                                                info = it,
                                                modifier = Modifier.height(cardHeight),
                                            )
                                        },
                                        directions = setOf(DismissDirection.EndToStart),
                                    )
                                }
                            }
                        }
                        Box(modifier = Modifier.padding(paddingValues = paddingValues)) {
                            InternalLazyColumnScrollbar(
                                listState = state,
                                thumbColor = MaterialTheme.colorScheme.primary,
                                thumbSelectedColor = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Deferred({ delay(200) }) {
                        if (historyData.itemCount == 0) {
                            Column(
                                modifier = Modifier.padding(paddingValues).fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.big_history),
                                    contentDescription = null,
                                    Modifier.padding(16.dp),
                                )
                                Text(
                                    text = stringResource(id = R.string.no_history),
                                    style = MaterialTheme.typography.headlineMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun onNavigationClick() {
        toggleDrawer(GravityCompat.START)
    }

    private fun onItemClick(gi: GalleryInfo) {
        val args = Bundle()
        args.putString(GalleryDetailScene.KEY_ACTION, GalleryDetailScene.ACTION_GALLERY_INFO)
        args.putParcelable(GalleryDetailScene.KEY_GALLERY_INFO, gi)
        navigate(R.id.galleryDetailScene, args)
    }

    private suspend fun handleLongClick(which: Int, gi: GalleryInfo, dialogState: DialogState) {
        val downloaded = DownloadManager.getDownloadState(gi.gid) != DownloadInfo.STATE_INVALID
        val favourite = gi.favoriteSlot != -2
        when (which) {
            0 -> context?.navToReader(gi)
            1 -> if (downloaded) {
                if (dialogState.confirmRemoveDownload(gi)) DownloadManager.deleteDownload(gi.gid)
            } else {
                CommonOperations.startDownload(activity as MainActivity, gi, false)
            }
            2 -> if (favourite) {
                lifecycleScope.launchIO {
                    runSuspendCatching {
                        removeFromFavorites(gi)
                        showTip(R.string.remove_from_favorite_success, LENGTH_SHORT)
                    }.onFailure {
                        showTip(R.string.remove_from_favorite_failure, LENGTH_LONG)
                    }
                }
            } else {
                lifecycleScope.launchIO {
                    runSuspendCatching {
                        requireContext().addToFavorites(gi)
                        showTip(R.string.add_to_favorite_success, LENGTH_SHORT)
                    }.onFailure {
                        showTip(R.string.add_to_favorite_failure, LENGTH_LONG)
                    }
                }
            }
            3 -> dialogState.showMoveDownloadLabel(gi)
        }
    }

    private val cardHeight = (Settings.listThumbSize * 3).pxToDp.dp
}

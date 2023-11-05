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

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewAnimationUtils
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.core.content.ContextCompat
import androidx.core.text.getSpans
import androidx.core.text.inSpans
import androidx.core.text.parseAsHtml
import androidx.core.text.set
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.hippo.ehviewer.R
import com.hippo.ehviewer.client.EhEngine
import com.hippo.ehviewer.client.EhFilter.remember
import com.hippo.ehviewer.client.EhUrl
import com.hippo.ehviewer.client.data.GalleryComment
import com.hippo.ehviewer.client.data.GalleryCommentList
import com.hippo.ehviewer.client.data.GalleryDetail
import com.hippo.ehviewer.client.data.ListUrlBuilder
import com.hippo.ehviewer.dao.Filter
import com.hippo.ehviewer.dao.FilterMode
import com.hippo.ehviewer.databinding.ItemDrawerFavoritesBinding
import com.hippo.ehviewer.databinding.ItemGalleryCommentBinding
import com.hippo.ehviewer.databinding.SceneGalleryCommentsBinding
import com.hippo.ehviewer.ui.MainActivity
import com.hippo.ehviewer.ui.jumpToReaderByPage
import com.hippo.ehviewer.ui.legacy.BaseDialogBuilder
import com.hippo.ehviewer.ui.legacy.CoilImageGetter
import com.hippo.ehviewer.ui.legacy.EditTextDialogBuilder
import com.hippo.ehviewer.ui.legacy.ViewTransition
import com.hippo.ehviewer.ui.legacy.WindowInsetsAnimationHelper
import com.hippo.ehviewer.ui.openBrowser
import com.hippo.ehviewer.ui.scene.GalleryListScene.Companion.toStartArgs
import com.hippo.ehviewer.ui.tools.LocalDialogState
import com.hippo.ehviewer.util.AnimationUtils
import com.hippo.ehviewer.util.ExceptionUtils
import com.hippo.ehviewer.util.IntList
import com.hippo.ehviewer.util.ReadableTime
import com.hippo.ehviewer.util.SimpleAnimatorListener
import com.hippo.ehviewer.util.TextUrl
import com.hippo.ehviewer.util.addTextToClipboard
import com.hippo.ehviewer.util.applyNavigationBarsPadding
import com.hippo.ehviewer.util.findActivity
import com.hippo.ehviewer.util.getParcelableCompat
import com.hippo.ehviewer.util.toBBCode
import com.ramcosta.composedestinations.annotation.Destination
import dev.chrisbanes.insetter.applyInsetter
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.util.lang.withUIContext
import kotlin.math.hypot
import kotlinx.coroutines.launch
import moe.tarsin.coroutines.runSuspendCatching
import rikka.core.res.resolveColor

interface ActionScope {
    infix fun String.thenDo(that: suspend () -> Unit)
}

private inline fun buildAction(builder: ActionScope.() -> Unit) = buildList {
    builder(object : ActionScope {
        override fun String.thenDo(that: suspend () -> Unit) {
            add(this to that)
        }
    })
}

private fun Context.generateComment(
    textView: TextView,
    comment: GalleryComment,
): Pair<CharSequence, CharSequence> {
    val sp = comment.comment.orEmpty().parseAsHtml(imageGetter = CoilImageGetter(textView))
    val ssb = SpannableStringBuilder(sp)
    if (0L != comment.id && 0 != comment.score) {
        val score = comment.score
        val scoreString = if (score > 0) "+$score" else score.toString()
        ssb.append("  ").inSpans(
            RelativeSizeSpan(0.8f),
            StyleSpan(Typeface.BOLD),
            ForegroundColorSpan(theme.resolveColor(android.R.attr.textColorSecondary)),
        ) {
            append(scoreString)
        }
    }
    if (comment.lastEdited != 0L) {
        val str = getString(
            R.string.last_edited,
            ReadableTime.getTimeAgo(comment.lastEdited),
        )
        ssb.append("\n\n").inSpans(
            RelativeSizeSpan(0.8f),
            StyleSpan(Typeface.BOLD),
            ForegroundColorSpan(theme.resolveColor(android.R.attr.textColorSecondary)),
        ) {
            append(str)
        }
    }
    return TextUrl.handleTextUrl(ssb) to sp
}

@Destination
@Composable
fun GalleryCommentsScreen(galleryDetail: GalleryDetail, navigator: NavController) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val dialogState = LocalDialogState.current
    val coroutineScope = rememberCoroutineScope()
    var commenting by rememberSaveable { mutableStateOf(false) }
    var userComment by rememberSaveable { mutableStateOf("") }
    var comments by rememberSaveable { mutableStateOf(galleryDetail.comments) }
    var refreshing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    suspend fun refreshComment(showAll: Boolean) {
        val url = EhUrl.getGalleryDetailUrl(galleryDetail.gid, galleryDetail.token, 0, showAll)
        val detail = EhEngine.getGalleryDetail(url)
        comments = detail.comments
    }

    val copyComment = stringResource(R.string.copy_comment_text)
    val blockCommenter = stringResource(R.string.block_commenter)
    val cancelVoteUp = stringResource(R.string.cancel_vote_up)
    val cancelVoteDown = stringResource(R.string.cancel_vote_down)
    val voteUp = stringResource(R.string.vote_up)
    val voteDown = stringResource(R.string.vote_down)

    suspend fun Context.showFilterCommenter(comment: GalleryComment) {
        val commenter = comment.user ?: return
        dialogState.awaitPermissionOrCancel { Text(text = stringResource(R.string.filter_the_commenter, commenter)) }
        Filter(FilterMode.COMMENTER, commenter).remember()
        comments = comments.copy(comments = comments.comments.filter { it == comment })
        findActivity<MainActivity>().showTip(R.string.filter_added, BaseScene.LENGTH_SHORT)
    }

    BackHandler(commenting) {
        commenting = false
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.gallery_comments)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.popBackStack() }) {
                        Icon(imageVector = Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (!commenting) {
                FloatingActionButton(onClick = { commenting = true }) {
                    Icon(imageVector = Icons.AutoMirrored.Default.Reply, contentDescription = null)
                }
            }
        },
    ) { paddingValues ->
        val keylineMargin = dimensionResource(id = R.dimen.keyline_margin)
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.padding(horizontal = keylineMargin),
                contentPadding = paddingValues,
            ) {
                items(comments.comments) { item ->
                    val recomposeScope = currentRecomposeScope

                    suspend fun Context.voteComment(comment: GalleryComment, isUp: Boolean) {
                        galleryDetail.runSuspendCatching {
                            EhEngine.voteComment(apiUid, apiKey, gid, token, comment.id, if (isUp) 1 else -1)
                        }.onSuccess { result ->
                            findActivity<MainActivity>().showTip(
                                if (isUp) (if (0 != result.vote) R.string.vote_up_successfully else R.string.cancel_vote_up_successfully) else if (0 != result.vote) R.string.vote_down_successfully else R.string.cancel_vote_down_successfully,
                                BaseScene.LENGTH_SHORT,
                            )
                            comment.score = result.score
                            if (isUp) {
                                comment.voteUpEd = 0 != result.vote
                                comment.voteDownEd = false
                            } else {
                                comment.voteDownEd = 0 != result.vote
                                comment.voteUpEd = false
                            }
                            recomposeScope.invalidate()
                        }.onFailure {
                            findActivity<MainActivity>().showTip(R.string.vote_failed, BaseScene.LENGTH_LONG)
                        }
                    }

                    suspend fun Context.doCommentAction(comment: GalleryComment, realText: CharSequence) {
                        val actions = buildAction {
                            copyComment thenDo { findActivity<MainActivity>().addTextToClipboard(realText) }
                            if (!comment.uploader && !comment.editable) {
                                blockCommenter thenDo suspend { showFilterCommenter(comment) }
                            }
                            if (comment.voteUpAble) {
                                (if (comment.voteUpEd) cancelVoteUp else voteUp) thenDo { voteComment(comment, true) }
                            }
                            if (comment.voteDownAble) {
                                (if (comment.voteDownEd) cancelVoteDown else voteDown) thenDo { voteComment(comment, false) }
                            }
                        }
                        dialogState.showSelectItem(*actions.toTypedArray()).invoke()
                    }

                    AndroidViewBinding(factory = ItemGalleryCommentBinding::inflate) {
                        user.text = item.user?.let {
                            if (item.uploader) context.getString(R.string.comment_user_uploader, it) else it
                        }
                        user.setBackgroundColor(Color.TRANSPARENT)
                        user.setOnClickListener {
                            val lub = ListUrlBuilder(
                                mode = ListUrlBuilder.MODE_UPLOADER,
                                mKeyword = item.user,
                            )
                            navigator.navAnimated(R.id.galleryListScene, lub.toStartArgs(), true)
                        }
                        time.text = ReadableTime.getTimeAgo(item.time)
                        comment.maxLines = 5
                        val (commentText, realtext) = context.generateComment(comment, item)
                        comment.text = commentText
                        comment.setOnClickListener {
                            val span = comment.currentSpan
                            comment.clearCurrentSpan()
                            if (span is URLSpan) {
                                val activity = context.findActivity<MainActivity>()
                                if (!activity.jumpToReaderByPage(span.url, galleryDetail)) {
                                    if (!navigator.navWithUrl(span.url)) {
                                        activity.openBrowser(span.url)
                                    }
                                }
                            }
                        }
                        card.setOnClickListener {
                            coroutineScope.launch {
                                context.doCommentAction(item, realtext)
                            }
                        }
                    }
                }
                if (comments.hasMore) {
                    item {
                        // TODO: This animation need to be investigated
                        AnimatedVisibility(refreshing) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).padding(keylineMargin),
                                )
                            }
                        }
                        AnimatedVisibility(!refreshing) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launchIO {
                                        refreshing = true
                                        runSuspendCatching {
                                            refreshComment(true)
                                        }
                                        refreshing = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().padding(keylineMargin),
                            ) {
                                Text(text = stringResource(id = R.string.click_more_comments))
                            }
                        }
                    }
                }
            }
            if (commenting) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        BasicTextField2(
                            value = userComment,
                            onValueChange = { userComment = it },
                            modifier = Modifier.weight(1f).padding(keylineMargin),
                        )
                        IconButton(
                            onClick = { commenting = false },
                            modifier = Modifier.align(Alignment.CenterVertically).padding(16.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Default.Send,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

class GalleryCommentsFragment : BaseScene(), View.OnClickListener {
    private var _binding: SceneGalleryCommentsBinding? = null
    private val binding get() = _binding!!
    private var mGalleryDetail: GalleryDetail? = null
    private var mViewTransition: ViewTransition? = null
    private var mSendDrawable: Drawable? = null
    private var mPencilDrawable: Drawable? = null
    private var mCommentId: Long = 0
    private var mInAnimation = false
    private var mShowAllComments = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = SceneGalleryCommentsBinding.inflate(inflater, FrameLayout(inflater.context))
        val tip = binding.tip
        ViewCompat.setWindowInsetsAnimationCallback(
            binding.root,
            WindowInsetsAnimationHelper(
                WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP,
                binding.editPanel,
                binding.fabLayout,
            ),
        )
        val context = requireContext()
        val drawable = ContextCompat.getDrawable(context, R.drawable.big_sad_pandroid)
        drawable!!.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        tip.setCompoundDrawables(null, drawable, null, null)
        mSendDrawable = ContextCompat.getDrawable(context, R.drawable.v_send_dark_x24)
        mPencilDrawable = ContextCompat.getDrawable(context, R.drawable.v_pencil_dark_x24)
        binding.recyclerView.layoutManager = LinearLayoutManager(
            context,
            RecyclerView.VERTICAL,
            false,
        )
        binding.recyclerView.setHasFixedSize(true)
        // Cancel change animator
        val itemAnimator = binding.recyclerView.itemAnimator
        if (itemAnimator is DefaultItemAnimator) {
            itemAnimator.supportsChangeAnimations = false
        }
        binding.send.setOnClickListener(this)
        binding.editText.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                requireActivity().menuInflater.inflate(R.menu.context_comment, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                return true
            }

            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
                item?.let {
                    val text = binding.editText.editableText
                    val start = binding.editText.selectionStart
                    val end = binding.editText.selectionEnd
                    when (item.itemId) {
                        R.id.action_bold -> text[start, end] = StyleSpan(Typeface.BOLD)

                        R.id.action_italic -> text[start, end] = StyleSpan(Typeface.ITALIC)

                        R.id.action_underline -> text[start, end] = UnderlineSpan()

                        R.id.action_strikethrough -> text[start, end] = StrikethroughSpan()

                        R.id.action_url -> {
                            val oldSpans = text.getSpans<URLSpan>(start, end)
                            var oldUrl = "https://"
                            oldSpans.forEach {
                                if (!it.url.isNullOrEmpty()) {
                                    oldUrl = it.url
                                }
                            }
                            val builder = EditTextDialogBuilder(
                                context,
                                oldUrl,
                                getString(R.string.format_url),
                            )
                            builder.setTitle(getString(R.string.format_url))
                            builder.setPositiveButton(android.R.string.ok, null)
                            val dialog = builder.show()
                            val button: View? = dialog.getButton(DialogInterface.BUTTON_POSITIVE)
                            button?.setOnClickListener(
                                View.OnClickListener {
                                    val url = builder.text.trim()
                                    if (url.isEmpty()) {
                                        builder.setError(getString(R.string.text_is_empty))
                                        return@OnClickListener
                                    } else {
                                        builder.setError(null)
                                    }
                                    text.clearSpan(start, end, true)
                                    text[start, end] = URLSpan(url)
                                    dialog.dismiss()
                                },
                            )
                        }

                        R.id.action_clear -> {
                            text.clearSpan(start, end, false)
                        }

                        else -> return false
                    }
                    mode?.finish()
                }
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode?) {
            }
        }
        binding.fab.setOnClickListener(this)
        mViewTransition = ViewTransition(binding.refreshLayout, tip)
        updateView(false)
        binding.editPanel.applyInsetter {
            type(ime = true, navigationBars = true) {
                padding()
            }
        }
        binding.recyclerView.applyInsetter {
            type(ime = true, navigationBars = true) {
                padding()
            }
        }
        binding.fabLayout.applyNavigationBarsPadding()
        return ComposeWithMD3 {
            val galleryDetail = remember { requireArguments().getParcelableCompat<GalleryDetail>(KEY_GALLERY_DETAIL)!! }
            val navController = remember { findNavController() }
            GalleryCommentsScreen(galleryDetail = galleryDetail, navigator = navController)
        }
    }

    fun Spannable.clearSpan(start: Int, end: Int, url: Boolean) {
        val spans = if (url) getSpans<URLSpan>(start, end) else getSpans<CharacterStyle>(start, end)
        spans.forEach {
            val spanStart = getSpanStart(it)
            val spanEnd = getSpanEnd(it)
            removeSpan(it)
            if (spanStart < start) {
                this[spanStart, start] = it
            }
            if (spanEnd > end) {
                this[end, spanEnd] = it
            }
        }
    }

    @SuppressLint("InflateParams")
    fun showVoteStatusDialog(context: Context, voteStatus: String) {
        val temp = voteStatus.split(',')
        val length = temp.size
        val userArray = arrayOfNulls<String>(length)
        val voteArray = arrayOfNulls<String>(length)
        for (i in 0 until length) {
            val str = temp[i].trim()
            val index = str.lastIndexOf(' ')
            if (index < 0) {
                Log.d(TAG, "Something wrong happened about vote state")
                userArray[i] = str
                voteArray[i] = ""
            } else {
                userArray[i] = str.substring(0, index).trim()
                voteArray[i] = str.substring(index + 1).trim()
            }
        }
        val builder = BaseDialogBuilder(context)
        val builderContext = builder.context
        val inflater = LayoutInflater.from(builderContext)
        val rv = inflater.inflate(R.layout.dialog_recycler_view, null) as RecyclerView
        rv.adapter = object : RecyclerView.Adapter<VoteHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoteHolder {
                return VoteHolder(ItemDrawerFavoritesBinding.inflate(inflater, parent, false))
            }

            override fun onBindViewHolder(holder: VoteHolder, position: Int) {
                holder.bind(userArray[position], voteArray[position])
            }

            override fun getItemCount(): Int {
                return length
            }
        }
        rv.layoutManager = LinearLayoutManager(builderContext)
        rv.clipToPadding = false
        builder.setView(rv).show()
    }

    private fun showCommentDialog(position: Int, text: CharSequence) {
        val context = context
        if (context == null || mGalleryDetail == null || position >= mGalleryDetail!!.comments.comments.size || position < 0) {
            return
        }
        val comment = mGalleryDetail!!.comments.comments[position]
        val menu: MutableList<String> = ArrayList()
        val menuId = IntList()
        val resources = context.resources
        menu.add(resources.getString(R.string.copy_comment_text))
        menuId.add(R.id.copy)
        if (!comment.voteState.isNullOrEmpty()) {
            menu.add(resources.getString(R.string.check_vote_status))
            menuId.add(R.id.check_vote_status)
        }
        BaseDialogBuilder(context)
            .setItems(menu.toTypedArray()) { _: DialogInterface?, which: Int ->
                if (which < 0 || which >= menuId.size) {
                    return@setItems
                }
                when (menuId[which]) {
                    R.id.check_vote_status -> showVoteStatusDialog(context, comment.voteState!!)
                    R.id.edit_comment -> prepareEditComment(comment.id, text)
                }
            }.show()
    }

    private fun updateView(animation: Boolean) {
        if (null == mViewTransition) {
            return
        }
        if (mGalleryDetail == null || mGalleryDetail!!.comments.comments.isEmpty()) {
            mViewTransition!!.showView(1, animation)
        } else {
            mViewTransition!!.showView(0, animation)
        }
    }

    private fun prepareNewComment() {
        mCommentId = 0
        binding.send.setImageDrawable(mSendDrawable)
    }

    private fun prepareEditComment(commentId: Long, text: CharSequence) {
        mCommentId = commentId
        binding.editText.setText(text)
        binding.send.setImageDrawable(mPencilDrawable)
    }

    private fun showEditPanelWithAnimation() {
        mInAnimation = true
        binding.fab.translationX = 0.0f
        binding.fab.translationY = 0.0f
        binding.fab.scaleX = 1.0f
        binding.fab.scaleY = 1.0f
        val fabEndX = binding.editPanel.left + binding.editPanel.width / 2 - binding.fab.width / 2
        val fabEndY = binding.editPanel.top + binding.editPanel.height / 2 - binding.fab.height / 2
        binding.fab.animate().x(fabEndX.toFloat()).y(fabEndY.toFloat()).scaleX(0.0f).scaleY(0.0f)
            .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
            .setDuration(300L).setListener(object : SimpleAnimatorListener() {
                override fun onAnimationEnd(animation: Animator) {
                    (binding.fab as View).visibility = View.INVISIBLE
                    binding.editPanel.visibility = View.VISIBLE
                    val halfW = binding.editPanel.width / 2
                    val halfH = binding.editPanel.height / 2
                    val animator = ViewAnimationUtils.createCircularReveal(
                        binding.editPanel,
                        halfW,
                        halfH,
                        0f,
                        hypot(halfW.toDouble(), halfH.toDouble()).toFloat(),
                    ).setDuration(300L)
                    animator.addListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(animation: Animator) {
                            mInAnimation = false
                        }
                    })
                    animator.start()
                }
            }).start()
    }

    private fun hideEditPanelWithAnimation() {
        mInAnimation = true
        val halfW = binding.editPanel.width / 2
        val halfH = binding.editPanel.height / 2
        val animator = ViewAnimationUtils.createCircularReveal(
            binding.editPanel,
            halfW,
            halfH,
            hypot(halfW.toDouble(), halfH.toDouble()).toFloat(),
            0.0f,
        ).setDuration(300L)
        animator.addListener(object : SimpleAnimatorListener() {
            override fun onAnimationEnd(animation: Animator) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    // Some devices may run this block in non-UI thread.
                    // It might be a bug of Android OS.
                    // Check it here to avoid crash.
                    return
                }
                binding.editPanel.visibility = View.GONE
                (binding.fab as View).visibility = View.VISIBLE
                val fabStartX =
                    binding.editPanel.left + binding.editPanel.width / 2 - binding.fab.width / 2
                val fabStartY =
                    binding.editPanel.top + binding.editPanel.height / 2 - binding.fab.height / 2
                binding.fab.x = fabStartX.toFloat()
                binding.fab.y = fabStartY.toFloat()
                binding.fab.scaleX = 0.0f
                binding.fab.scaleY = 0.0f
                binding.fab.rotation = -45.0f
                binding.fab.animate().translationX(0.0f).translationY(0.0f).scaleX(1.0f)
                    .scaleY(1.0f)
                    .rotation(0.0f)
                    .setInterpolator(AnimationUtils.SLOW_FAST_SLOW_INTERPOLATOR)
                    .setDuration(300L).setListener(object : SimpleAnimatorListener() {
                        override fun onAnimationEnd(animation: Animator) {
                            mInAnimation = false
                        }
                    }).start()
            }
        })
        animator.start()
    }

    private fun hideEditPanel(animation: Boolean) {
        hideSoftInput()
        if (animation) {
            hideEditPanelWithAnimation()
        } else {
            (binding.fab as View).visibility = View.VISIBLE
            binding.editPanel.visibility = View.INVISIBLE
        }
    }

    private val galleryDetailUrl: String?
        get() = if (mGalleryDetail != null && mGalleryDetail!!.gid != -1L && mGalleryDetail!!.token != null) {
            EhUrl.getGalleryDetailUrl(
                mGalleryDetail!!.gid,
                mGalleryDetail!!.token,
                0,
                mShowAllComments,
            )
        } else {
            null
        }

    override fun onClick(v: View) {
        val context = context
        val activity = mainActivity
        if (null == context || null == activity) {
            return
        }
        if (binding.fab === v) {
            if (!mInAnimation) {
                prepareNewComment()
            }
        } else if (binding.send === v) {
            if (!mInAnimation) {
                val comment = binding.editText.text?.toBBCode()?.takeIf { it.isNotBlank() } ?: return
                val url = galleryDetailUrl ?: return
                lifecycleScope.launchIO {
                    runSuspendCatching {
                        EhEngine.commentGallery(
                            url,
                            comment,
                            if (mCommentId != 0L) mCommentId.toString() else null,
                        )
                    }.onSuccess {
                        showTip(
                            if (mCommentId != 0L) R.string.edit_comment_successfully else R.string.comment_successfully,
                            LENGTH_SHORT,
                        )
                        withUIContext {
                            onCommentGallerySuccess(it)
                        }
                    }.onFailure {
                        showTip(
                            """
    ${getString(if (mCommentId != 0L) R.string.edit_comment_failed else R.string.comment_failed)}
    ${ExceptionUtils.getReadableString(it)}
                            """.trimIndent(),
                            LENGTH_LONG,
                        )
                    }
                }
                hideSoftInput()
                hideEditPanel(true)
            }
        }
    }

    private fun onCommentGallerySuccess(result: GalleryCommentList) {
        mGalleryDetail!!.comments = result
        // Remove text
        binding.editText.setText("")
        updateView(true)
    }

    private class VoteHolder(private val binding: ItemDrawerFavoritesBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(user: String?, vote: String?) {
            binding.key.text = user
            binding.value.text = vote
        }
    }

    companion object {
        val TAG: String = GalleryCommentsFragment::class.java.simpleName
        const val KEY_API_UID = "api_uid"
        const val KEY_API_KEY = "api_key"
        const val KEY_GID = "gid"
        const val KEY_TOKEN = "token"
        const val KEY_COMMENT_LIST = "comment_list"
        const val KEY_GALLERY_DETAIL = "gallery_detail"
    }
}

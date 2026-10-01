package com.imomali.chatapp

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.imomali.chatapp.domain.ChatSummary
import com.imomali.chatapp.domain.DiscoveryPolicy
import com.imomali.chatapp.domain.UserProfile
import java.text.DateFormat
import java.util.Date

/** Native, width-adaptive discovery surface; messaging controls arrive in Thursday's scope. */
class DiscoveryView(context: Context, private val model: DiscoveryViewModel) : LinearLayout(context) {
    private val controls = LinearLayout(context)
    private val back = button(R.string.back) { hideKeyboard(); model.back() }
    private val newChat = button(R.string.new_chat) { model.showSearch() }
    private val ownProfile = button(R.string.my_profile) { model.showOwnProfile() }
    private val searchInput = TextInputEditText(context).apply {
        id = R.id.userSearch; isSingleLine = true; maxLines = 1; isSaveEnabled = false
        hint = context.getString(R.string.search_by_name)
        minHeight = dp(56)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        inputType = android.text.InputType.TYPE_CLASS_TEXT
        filters = arrayOf(android.text.InputFilter.LengthFilter(60))
    }
    private val searchBox = TextInputLayout(context).apply {
        isHintEnabled = false
        addView(searchInput)
    }
    private val progress = ProgressBar(context)
    private val status = label().apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val retry = button(R.string.retry) { model.retry() }
    private val details = LinearLayout(context).apply { orientation = VERTICAL }
    private val detailsScroll = ScrollView(context).apply { addView(details) }
    private val adapter = RowsAdapter()
    private val list = RecyclerView(context).apply {
        id = R.id.discoveryList; layoutManager = LinearLayoutManager(context); adapter = this@DiscoveryView.adapter
        isSaveEnabled = false
    }
    private var rendering = false
    private var lastScreen: DiscoveryScreen? = null

    init {
        orientation = VERTICAL
        controls.addView(back, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(newChat, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        controls.addView(ownProfile, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(controls); addView(searchBox)
        addView(progress, LayoutParams(dp(32), dp(32)).apply { gravity = android.view.Gravity.CENTER })
        addView(status); addView(retry)
        addView(detailsScroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        searchInput.doAfterTextChanged { if (!rendering) model.search(it.toString()) }
    }
    fun render(state: DiscoveryState) {
        rendering = true
        if (state.screen != lastScreen) { hideKeyboard(); lastScreen = state.screen }
        back.visibility = if (state.screen == DiscoveryScreen.CHATS) GONE else VISIBLE
        back.isEnabled = !state.starting
        newChat.visibility = if (state.screen == DiscoveryScreen.CHATS) VISIBLE else GONE
        ownProfile.visibility = newChat.visibility
        val searching = state.screen == DiscoveryScreen.SEARCH
        searchBox.visibility = if (searching) VISIBLE else GONE
        if (searchInput.text.toString() != state.query) searchInput.setText(state.query)
        searchInput.isEnabled = !state.starting
        val busy = when (state.screen) {
            DiscoveryScreen.CHATS -> state.loadingChats
            DiscoveryScreen.SEARCH -> state.searching || state.starting
            DiscoveryScreen.PROFILE -> state.loadingProfile
            else -> false
        }
        progress.visibility = if (busy) VISIBLE else GONE
        val error = if (state.screen == DiscoveryScreen.CHATS) state.chatsError else state.error
        status.text = error ?: when (state.screen) {
            DiscoveryScreen.CHATS -> if (!busy && state.chats.isEmpty()) context.getString(R.string.no_chats) else ""
            DiscoveryScreen.SEARCH -> when {
                state.starting -> context.getString(R.string.opening_chat)
                state.query.isBlank() -> context.getString(R.string.search_hint)
                !busy && state.results.isEmpty() -> context.getString(R.string.no_users)
                !busy && state.results.size == 20 -> context.getString(R.string.refine_search)
                else -> ""
            }
            else -> ""
        }
        status.visibility = if (status.text.isEmpty()) GONE else VISIBLE
        retry.visibility = if (error != null && !state.starting) VISIBLE else GONE
        details.removeAllViews()
        list.visibility = if (state.screen == DiscoveryScreen.CHATS || searching) VISIBLE else GONE
        detailsScroll.visibility = if (list.visibility == GONE) VISIBLE else GONE
        when (state.screen) {
            DiscoveryScreen.CHATS -> adapter.show(state.chats.map { chat ->
                Row(chat.participant?.displayName.orEmpty(), chat.lastMessage?.text ?: context.getString(R.string.no_messages),
                    chat.activityMillis, { model.open(chat) })
            })
            DiscoveryScreen.SEARCH -> adapter.show(state.results.map { user ->
                Row(user.displayName, user.bio.ifBlank { context.getString(R.string.tap_to_chat) }, 0,
                    { if (!state.starting) { hideKeyboard(); model.start(user) } })
            })
            DiscoveryScreen.PROFILE -> if (!busy && state.error == null) {
                profileDetails(state.profile)
            }
            DiscoveryScreen.CONVERSATION -> {
                val user = state.participant
                details.addView(label(24f).apply { text = user?.displayName?.ifBlank { null } ?: context.getString(R.string.unknown_user) })
                details.addView(label().apply {
                    text = state.chats.firstOrNull { it.conversation.id == state.conversationId }?.lastMessage?.text
                        ?: context.getString(R.string.conversation_ready)
                })
                if (user != null) details.addView(button(R.string.view_profile) { model.showProfile(user.uid) })
            }
        }
        rendering = false
    }
    private fun profileDetails(profile: UserProfile?) {
        details.addView(label(36f).apply { text = DiscoveryPolicy.initials(profile?.displayName.orEmpty()); gravity = android.view.Gravity.CENTER })
        details.addView(label(24f).apply { text = profile?.displayName?.ifBlank { null } ?: context.getString(R.string.unknown_user) })
        details.addView(label().apply { text = profile?.bio?.ifBlank { null } ?: context.getString(R.string.no_bio) })
    }
    private fun hideKeyboard() {
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        searchInput.clearFocus()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(size: Float = 16f) = TextView(context).apply {
        textSize = size; setTextColor(Color.rgb(36, 36, 61)); setPadding(dp(4), dp(8), dp(4), dp(8))
    }
    private fun button(text: Int, action: () -> Unit) = MaterialButton(context).apply {
        setText(text); minHeight = dp(48); setOnClickListener { action() }
    }
    private data class Row(val name: String, val preview: String, val time: Long, val select: () -> Unit)
    private inner class RowsAdapter : RecyclerView.Adapter<RowHolder>() {
        private var rows = emptyList<Row>()
        fun show(value: List<Row>) { rows = value; notifyDataSetChanged() }
        override fun getItemCount() = rows.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder = RowHolder()
        override fun onBindViewHolder(holder: RowHolder, position: Int) { holder.bind(rows[position]) }
    }
    private inner class RowHolder : RecyclerView.ViewHolder(LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setPadding(dp(4), dp(8), dp(4), dp(8)); minimumHeight = dp(80)
        isClickable = true; isFocusable = true
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        setBackgroundResource(value.resourceId)
    }) {
        private val avatar = label(22f).apply { gravity = android.view.Gravity.CENTER; setTextColor(Color.rgb(86, 71, 190)) }
        private val name = label(18f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val preview = label(14f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        private val time = label(12f)
        init {
            (itemView as LinearLayout).apply {
                addView(avatar, LayoutParams(dp(52), LayoutParams.WRAP_CONTENT))
                addView(LinearLayout(context).apply { orientation = VERTICAL; addView(name); addView(preview) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                addView(time)
            }
        }
        fun bind(row: Row) {
            name.text = row.name.ifBlank { context.getString(R.string.unknown_user) }
            avatar.text = DiscoveryPolicy.initials(row.name); preview.text = row.preview
            time.text = if (row.time > 0) DateFormat.getDateInstance(DateFormat.SHORT).format(Date(row.time)) else ""
            itemView.setOnClickListener { row.select() }
        }
    }
}

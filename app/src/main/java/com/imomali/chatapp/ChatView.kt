package com.imomali.chatapp

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.imomali.chatapp.domain.*

class ChatView(context: Context, private val model: ChatViewModel, profile: () -> Unit) : LinearLayout(context) {
    private val name = label(18f).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val profileButton = MaterialButton(context).apply {
        setText(R.string.view_profile); minHeight = dp(48); setOnClickListener { profile() }
    }.secondary()
    private val progress = ProgressBar(context).apply { contentDescription = context.getString(R.string.loading_messages) }
    private val status = label(13f).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
    private val retryHistory = MaterialButton(context).apply {
        setText(R.string.retry); setOnClickListener { model.retryHistory() }
    }
    private val adapter = MessagesAdapter()
    private val layout = LinearLayoutManager(context).apply { stackFromEnd = true }
    private val history = RecyclerView(context).apply {
        id = R.id.messageHistory; layoutManager = layout; adapter = this@ChatView.adapter
        isSaveEnabled = false; clipToPadding = false
    }
    private val input = object : TextInputEditText(context) {
        override fun onCreateInputConnection(outAttrs: EditorInfo): android.view.inputmethod.InputConnection? {
            val connection = super.onCreateInputConnection(outAttrs)
            outAttrs.imeOptions = (outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv() and EditorInfo.IME_MASK_ACTION.inv()) or EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            return connection
        }
    }.apply {
        id = R.id.messageInput; hint = context.getString(R.string.message_hint)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        minLines = 1; maxLines = 4; minHeight = dp(48); isSaveEnabled = false
        filters = arrayOf(InputFilter.LengthFilter(MessagePolicy.MAX_LENGTH))
        contentDescription = context.getString(R.string.message_hint)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat(); setColor(Color.WHITE); setStroke(dp(1), Color.rgb(205, 201, 224))
        }
        setTextColor(Color.rgb(36, 36, 61)); setHintTextColor(Color.rgb(100, 97, 119))
    }
    private val send = MaterialButton(context).apply {
        id = R.id.sendMessage; setText(R.string.send_message); minHeight = dp(48)
        setOnClickListener { model.send() }
    }
    private val count = label(12f)
    private var rendering = false
    private var conversation: String? = null
    private val participantHeader = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(name, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(profileButton)
    }
    fun compactComposer(compact: Boolean) {
        participantHeader.visibility = if (compact) GONE else VISIBLE
        count.visibility = if (compact) GONE else VISIBLE
        input.maxLines = if (compact) 1 else 4
        status.maxLines = if (compact) 1 else Int.MAX_VALUE
        status.ellipsize = if (compact) TextUtils.TruncateAt.END else null
    }
    init {
        orientation = VERTICAL
        addView(participantHeader)
        addView(progress, LayoutParams(dp(24), dp(24)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        addView(status); addView(retryHistory)
        addView(history, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(LinearLayout(context).apply {
            gravity = Gravity.BOTTOM
            isBaselineAligned = false
            addView(input, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
            addView(send)
        })
        addView(count)
        input.doAfterTextChanged { if (!rendering) model.edit(it.toString()) }
        input.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_SEND) { model.send(); true } else false
        }
        input.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ENTER && !event.isShiftPressed) {
                if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) model.send()
                true
            } else false
        }
    }
    fun participant(profile: UserProfile?) {
        name.text = profile?.displayName?.ifBlank { null } ?: context.getString(R.string.unknown_user)
        profileButton.isEnabled = profile != null
    }
    fun render(state: MessagingState) {
        rendering = true
        val newConversation = conversation != state.conversationId
        conversation = state.conversationId
        if (input.text.toString() != state.draft) {
            input.setText(state.draft); input.setSelection(input.text?.length ?: 0)
        }
        input.isEnabled = !state.draftLocked
        send.isEnabled = state.canSend
        send.setText(if (state.outgoing?.status == SendStatus.FAILED || state.outgoing?.status == SendStatus.WAITING) R.string.retry else R.string.send_message)
        send.contentDescription = if (state.sending) context.getString(R.string.message_sending) else send.text
        count.text = context.getString(R.string.message_count, state.draft.length, MessagePolicy.MAX_LENGTH)
        progress.visibility = if (state.loading) VISIBLE else GONE
        status.text = when {
            state.receiveError -> context.getString(R.string.history_failed)
            state.outgoing?.status == SendStatus.FAILED -> context.getString(R.string.send_failed)
            state.outgoing?.status == SendStatus.WAITING -> context.getString(R.string.send_waiting)
            state.sending -> context.getString(R.string.message_sending)
            state.fromCache -> context.getString(R.string.history_connecting)
            !state.loading && state.messages.isEmpty() && state.outgoing == null -> context.getString(R.string.empty_messages)
            state.messages.size >= 50 -> context.getString(R.string.recent_history)
            else -> ""
        }
        status.visibility = if (status.text.isEmpty()) GONE else VISIBLE
        retryHistory.visibility = if (state.receiveError) VISIBLE else GONE
        val rows = state.messages.map { message ->
            MessageRow(message, message.senderId == state.uid,
                state.outgoing?.takeIf { it.id == message.id }?.status)
        }.toMutableList()
        state.outgoing?.let { outgoing ->
            if (rows.none { it.message.id == outgoing.id }) rows += MessageRow(
                Message(outgoing.id, state.uid.orEmpty(), outgoing.text, 0, true), true, outgoing.status)
        }
        val atBottom = !history.canScrollVertically(1)
        val previousLast = adapter.currentList.lastOrNull()?.message?.id
        adapter.submitList(rows) {
            if (rows.isNotEmpty() && (newConversation || atBottom ||
                (rows.last().mine && rows.last().message.id != previousLast))) history.scrollToPosition(rows.lastIndex)
        }
        rendering = false
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(size: Float) = TextView(context).apply {
        textSize = size; setTextColor(Color.rgb(36, 36, 61)); setPadding(dp(4), dp(4), dp(4), dp(4))
    }
    private data class MessageRow(val message: Message, val mine: Boolean, val status: SendStatus?)
    private inner class MessagesAdapter : ListAdapter<MessageRow, MessageHolder>(object : DiffUtil.ItemCallback<MessageRow>() {
        override fun areItemsTheSame(old: MessageRow, new: MessageRow) = old.message.id == new.message.id
        override fun areContentsTheSame(old: MessageRow, new: MessageRow) = old == new
    }) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = MessageHolder()
        override fun onBindViewHolder(holder: MessageHolder, position: Int) { holder.bind(getItem(position)) }
    }
    private inner class MessageHolder : RecyclerView.ViewHolder(LinearLayout(context).apply {
        orientation = VERTICAL
        layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        setPadding(0, dp(4), 0, dp(4))
    }) {
        private val body = label(16f).apply { setPadding(dp(12), dp(10), dp(12), dp(10)) }
        private val timestamp = label(12f)
        init { (itemView as LinearLayout).apply { addView(body); addView(timestamp) } }
        fun bind(row: MessageRow) {
            val side = if (row.mine) Gravity.END else Gravity.START
            (itemView as LinearLayout).gravity = side
            timestamp.gravity = side
            body.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                if (row.mine) marginStart = dp(36) else marginEnd = dp(36)
            }
            body.text = row.message.text
            body.setTextColor(if (row.mine) Color.WHITE else Color.rgb(36, 36, 61))
            body.background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(if (row.mine) Color.rgb(81, 67, 188) else Color.rgb(234, 231, 250))
            }
            val statusText = when {
                row.status == SendStatus.FAILED -> context.getString(R.string.message_failed)
                row.status == SendStatus.WAITING -> context.getString(R.string.message_waiting)
                row.message.pending || row.status == SendStatus.SENDING -> context.getString(R.string.message_sending)
                else -> MessageTime.format(row.message.createdAtMillis) ?: context.getString(R.string.time_unavailable)
            }
            timestamp.text = statusText
            timestamp.contentDescription = if (row.message.pending || row.status != null) statusText
                else MessageTime.format(row.message.createdAtMillis, full = true) ?: statusText
            itemView.contentDescription = context.getString(if (row.mine) R.string.outgoing_message else R.string.incoming_message,
                row.message.text, timestamp.contentDescription)
            itemView.isFocusable = true
            body.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            timestamp.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }
}

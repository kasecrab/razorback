package io.github.kasecrab.razorback.ui.chat

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.github.kasecrab.razorback.model.Message
import io.github.kasecrab.razorback.model.Role

/** Binds the engine's message list; streaming updates arrive as a payload and touch only text. */
class ChatAdapter(private val messages: List<Message>) : RecyclerView.Adapter<ChatAdapter.Holder>() {

    /** Called with the adapter position when a message asks for its action menu. */
    var onMenu: ((Int) -> Unit)? = null

    class Holder(view: View) : RecyclerView.ViewHolder(view)

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int = messages.size

    override fun getItemId(position: Int): Long = messages[position].id.hashCode().toLong()

    override fun getItemViewType(position: Int): Int = if (messages[position].role == Role.USER) USER else ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = if (viewType == USER) UserMessageView(parent.context) else AssistantMessageView(parent.context)
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        val holder = Holder(view)
        val menu: () -> Unit = { onMenu?.invoke(holder.bindingAdapterPosition) }
        when (view) {
            is UserMessageView -> view.onMenu = menu
            is AssistantMessageView -> view.onMenu = menu
        }
        return holder
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val m = messages[position]
        when (val v = holder.itemView) {
            is UserMessageView -> v.bind(m)
            is AssistantMessageView -> v.bind(m)
        }
    }

    override fun onBindViewHolder(holder: Holder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty() && payloads.all { it === STREAM }) {
            (holder.itemView as? AssistantMessageView)?.bindStream(messages[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    companion object {
        const val USER = 0
        const val ASSISTANT = 1
        val STREAM = Any()
    }
}

package one.mixin.android.ui.media

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ViewAnimator
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.timehop.stickyheadersrecyclerview.StickyRecyclerHeadersDecoration
import kotlinx.android.synthetic.main.layout_recycler_view.*
import one.mixin.android.Constants
import one.mixin.android.R
import one.mixin.android.extension.withArgs
import one.mixin.android.ui.common.BaseViewModelFragment
import one.mixin.android.util.AudioPlayer
import one.mixin.android.vo.MessageItem

class AudioFragment : BaseViewModelFragment<SharedMediaViewModel>() {
    companion object {
        const val TAG = "AudioFragment"

        fun newInstance(conversationId: String) = AudioFragment().withArgs {
            putString(Constants.ARGS_CONVERSATION_ID, conversationId)
        }
    }

    override fun getModelClass() = SharedMediaViewModel::class.java

    private val conversationId: String by lazy {
        arguments!!.getString(Constants.ARGS_CONVERSATION_ID)!!
    }

    private val adapter = AudioAdapter(fun(messageItem: MessageItem) {
        if (AudioPlayer.get().isPlay(messageItem.messageId)) {
            AudioPlayer.get().pause()
        } else {
            AudioPlayer.get().play(messageItem, autoPlayNext = false)
        }
    })

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.layout_recycler_view, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recycler_view.layoutManager = LinearLayoutManager(requireContext())
        recycler_view.addItemDecoration(StickyRecyclerHeadersDecoration(adapter))
        recycler_view.adapter = adapter
        empty_iv.setImageResource(R.drawable.ic_empty_audio)
        empty_tv.setText(R.string.no_audio)
        viewModel.getAudioMessages(conversationId).observe(this, Observer {
            if (it.size <= 0) {
                (view as ViewAnimator).displayedChild = 1
            } else {
                (view as ViewAnimator).displayedChild = 0
            }
            adapter.submitList(it)
        })
    }
}

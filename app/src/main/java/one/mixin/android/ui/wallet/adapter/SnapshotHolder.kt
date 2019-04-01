package one.mixin.android.ui.wallet.adapter

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.item_transaction_header.view.*
import kotlinx.android.synthetic.main.item_wallet_transactions.view.*
import one.mixin.android.R
import one.mixin.android.extension.dpToPx
import one.mixin.android.extension.formatPublicKey
import one.mixin.android.extension.numberFormat
import one.mixin.android.extension.timeAgoDay
import one.mixin.android.ui.common.recyclerview.NormalHolder
import one.mixin.android.vo.SnapshotItem
import one.mixin.android.vo.SnapshotType
import org.jetbrains.anko.backgroundResource
import org.jetbrains.anko.textColorResource

class SnapshotHolder(itemView: View) : NormalHolder(itemView) {
    private val padding = itemView.context.dpToPx(16f)

    fun bind(snapshot: SnapshotItem, listener: OnSnapshotListener?, isLast: Boolean) {
        val isPositive = snapshot.amount.toFloat() > 0
        when {
            snapshot.type == SnapshotType.pending.name -> {
                itemView.name.text = itemView.context.getString(R.string.pending_confirmations, snapshot.confirmations, snapshot.assetConfirmations)
                itemView.avatar.setUrl(null, R.drawable.ic_transaction_up)
                itemView.bg.setConfirmation(snapshot.assetConfirmations, snapshot.confirmations ?: 0)
            }
            snapshot.type == SnapshotType.deposit.name -> {
                itemView.name.setText(R.string.filters_deposit)
                itemView.avatar.setUrl(null, R.drawable.ic_transaction_down)
            }
            snapshot.type == SnapshotType.transfer.name -> {
                itemView.name.setText(R.string.filters_transfer)
                itemView.avatar.setInfo(snapshot.opponentFullName, snapshot.avatarUrl, snapshot.opponentId ?: "")
                itemView.avatar.setOnClickListener {
                    listener?.onUserClick(snapshot.opponentId!!)
                }
                itemView.avatar.setTextSize(12f)
            }
            snapshot.type == SnapshotType.withdrawal.name -> {
                itemView.name.setText(R.string.filters_withdrawal)
                itemView.avatar.setUrl(null, R.drawable.ic_transaction_up)
            }
            snapshot.type == SnapshotType.fee.name -> {
                itemView.name.setText(R.string.filters_fee)
                itemView.avatar.setUrl(null, R.drawable.ic_transaction_up)
            }
            snapshot.type == SnapshotType.rebate.name -> {
                itemView.name.setText(R.string.filters_rebate)
                itemView.avatar.setUrl(null, R.drawable.ic_transaction_down)
            }
            else -> {
                itemView.name.text = snapshot.receiver!!.formatPublicKey()
                itemView.avatar.setUrl(null, R.drawable.ic_transaction_down)
            }
        }

        if (isLast) {
            itemView.root.backgroundResource = R.drawable.bg_wallet_transactions_bottom
            itemView.root.setPadding(padding, 0, padding, padding)
            itemView.bg.roundBottom(true)
            itemView.transaction_shadow_left.visibility = View.GONE
            itemView.transaction_shadow_right.visibility = View.GONE
        } else {
            itemView.root.backgroundResource = R.color.white
            itemView.root.setPadding(0, 0, 0, 0)
            itemView.bg.roundBottom(false)
            itemView.transaction_shadow_left.visibility = View.VISIBLE
            itemView.transaction_shadow_right.visibility = View.VISIBLE
        }

        itemView.value.text = if (isPositive) "+${snapshot.amount.numberFormat()}"
        else snapshot.amount.numberFormat()
        itemView.value.textColorResource = when {
            snapshot.type == SnapshotType.pending.name -> R.color.wallet_text_dark
            isPositive -> R.color.wallet_green
            else -> R.color.wallet_pink
        }

        itemView.setOnClickListener {
            if (snapshot.type != SnapshotType.pending.name) {
                listener?.onNormalItemClick(snapshot)
            }
        }
    }
}

class SnapshotHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun bind(time: String) {
        itemView.date_tv.timeAgoDay(time)
    }
}

interface OnSnapshotListener {
    fun <T> onNormalItemClick(item: T)
    fun onUserClick(userId: String)
}
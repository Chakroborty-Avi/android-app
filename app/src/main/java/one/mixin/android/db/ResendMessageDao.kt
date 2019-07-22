package one.mixin.android.db

import androidx.room.Dao
import androidx.room.Query
import one.mixin.android.vo.ResendMessage

@Dao
interface ResendMessageDao : BaseDao<ResendMessage> {

    @Query("SELECT * FROM resend_messages WHERE user_id = :userId AND message_id = :messageId")
    fun findResendMessage(userId: String, messageId: String): ResendMessage?
}

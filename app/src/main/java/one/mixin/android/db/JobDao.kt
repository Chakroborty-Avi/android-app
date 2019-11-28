package one.mixin.android.db

import androidx.room.Dao
import androidx.room.Query
import one.mixin.android.vo.Job

@Dao
interface JobDao : BaseDao<Job> {
    @Query("SELECT * FROM jobs WHERE `action` = 'ACKNOWLEDGE_MESSAGE_RECEIPTS' ORDER BY created_at ASC LIMIT 100")
    suspend fun findAckJobs(): List<Job>?

    @Query("SELECT * FROM jobs WHERE `action` = 'CREATE_MESSAGE' ORDER BY created_at ASC LIMIT 100")
    suspend fun findCreateMessageJobs(): List<Job>?
}

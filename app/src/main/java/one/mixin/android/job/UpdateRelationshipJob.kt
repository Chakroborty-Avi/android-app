package one.mixin.android.job

import android.annotation.SuppressLint
import com.birbit.android.jobqueue.Params
import io.reactivex.schedulers.Schedulers
import one.mixin.android.api.MixinResponse
import one.mixin.android.api.request.RelationshipAction
import one.mixin.android.api.request.RelationshipAction.ADD
import one.mixin.android.api.request.RelationshipAction.BLOCK
import one.mixin.android.api.request.RelationshipAction.REMOVE
import one.mixin.android.api.request.RelationshipAction.UNBLOCK
import one.mixin.android.api.request.RelationshipRequest
import one.mixin.android.util.ErrorHandler
import one.mixin.android.util.Session
import one.mixin.android.vo.User
import one.mixin.android.vo.UserRelationship
import timber.log.Timber

class UpdateRelationshipJob(private val request: RelationshipRequest, private val deleteConversationId: String? = null) :
    BaseJob(Params(PRIORITY_UI_HIGH).addTags(GROUP).groupBy("relationship").requireNetwork()) {

    companion object {
        private const val serialVersionUID = 1L
        const val GROUP = "UpdateRelationshipJob"
    }

    override fun onAdded() {
        if (request.user_id == Session.getAccountId()) {
            return
        }
        when {
            RelationshipAction.valueOf(request.action) == ADD ->
                userDao.updateUserRelationship(request.user_id, UserRelationship.FRIEND.name)
            RelationshipAction.valueOf(request.action) == REMOVE ->
                userDao.updateUserRelationship(request.user_id, UserRelationship.STRANGER.name)
            RelationshipAction.valueOf(request.action) == BLOCK ->
                userDao.updateUserRelationship(request.user_id, UserRelationship.BLOCKING.name)
            RelationshipAction.valueOf(request.action) == UNBLOCK ->
                userDao.updateUserRelationship(request.user_id, UserRelationship.STRANGER.name)
        }
    }

    @SuppressLint("CheckResult")
    override fun onRun() {
        if (request.user_id == Session.getAccountId()) {
            return
        }
        if (deleteConversationId != null) {
            userService.report(request)
                .subscribeOn(Schedulers.io())
                .subscribe({ r: MixinResponse<User> ->
                    if (r.isSuccess) {
                        r.data?.let { u ->
                            if (u.app != null) {
                                u.appId = u.app!!.appId
                                userRepo.insertApp(u.app!!)
                            }
                            userRepo.upsert(u)
                        }
                        conversationDao.deleteConversationById(deleteConversationId)
                    } else {
                        ErrorHandler.handleMixinError(r.errorCode)
                    }
                }, { t: Throwable ->
                    Timber.e(t)
                })
        } else {
            userService.relationship(request)
                .subscribeOn(Schedulers.io())
                .subscribe({ r: MixinResponse<User> ->
                    if (r.isSuccess) {
                        r.data?.let { u ->
                            if (u.app != null) {
                                u.appId = u.app!!.appId
                                userRepo.insertApp(u.app!!)
                            }
                            userRepo.upsert(u)
                        }
                    } else {
                        ErrorHandler.handleMixinError(r.errorCode)
                    }
                }, { t: Throwable ->
                    Timber.e(t)
                })
        }
    }
}

package one.mixin.android.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import one.mixin.android.Constants.CONVERSATION_PAGE_SIZE
import one.mixin.android.api.handleMixinResponse
import one.mixin.android.api.request.CircleConversationRequest
import one.mixin.android.api.request.ConversationRequest
import one.mixin.android.api.request.ParticipantRequest
import one.mixin.android.extension.nowInUtc
import one.mixin.android.job.ConversationJob
import one.mixin.android.job.ConversationJob.Companion.TYPE_CREATE
import one.mixin.android.job.MixinJobManager
import one.mixin.android.repository.ConversationRepository
import one.mixin.android.repository.UserRepository
import one.mixin.android.util.SINGLE_DB_THREAD
import one.mixin.android.vo.Circle
import one.mixin.android.vo.CircleConversation
import one.mixin.android.vo.CircleOrder
import one.mixin.android.vo.Conversation
import one.mixin.android.vo.ConversationCategory
import one.mixin.android.vo.ConversationItem
import one.mixin.android.vo.ConversationStatus
import one.mixin.android.vo.Participant
import one.mixin.android.vo.User
import one.mixin.android.vo.generateConversationId

class ConversationListViewModel @Inject
internal constructor(
    private val messageRepository: ConversationRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
    private val jobManager: MixinJobManager
) : ViewModel() {

    fun observeConversations(circleId: String?): LiveData<PagedList<ConversationItem>> {
        return LivePagedListBuilder(
            messageRepository.conversations(circleId), PagedList.Config.Builder()
            .setPrefetchDistance(CONVERSATION_PAGE_SIZE * 2)
            .setPageSize(CONVERSATION_PAGE_SIZE)
            .setEnablePlaceholders(true)
            .build()
        ).build()
    }

    fun createGroupConversation(conversationId: String) {
        val c = messageRepository.getConversation(conversationId)
        c?.let {
            val participants = messageRepository.getGroupParticipants(conversationId)
            val mutableList = mutableListOf<Participant>()
            val createAt = nowInUtc()
            participants.mapTo(mutableList) { Participant(conversationId, it.userId, "", createAt) }
            val conversation = Conversation(c.conversationId, c.ownerId, c.category, c.name, c.iconUrl,
                c.announcement, null, c.payType, createAt, null, null,
                null, 0, ConversationStatus.START.ordinal, null)
            viewModelScope.launch {
                messageRepository.insertConversation(conversation, mutableList)
            }

            val participantRequestList = mutableListOf<ParticipantRequest>()
            mutableList.mapTo(participantRequestList) { ParticipantRequest(it.userId, it.role) }
            val request = ConversationRequest(conversationId, it.category!!, it.name, it.iconUrl,
                it.announcement, participantRequestList)
            jobManager.addJobInBackground(ConversationJob(request, type = TYPE_CREATE))
        }
    }

    fun deleteConversation(conversationId: String) = viewModelScope.launch {
        messageRepository.deleteConversationById(conversationId)
    }

    fun updateConversationPinTimeById(conversationId: String, circleId: String?, pinTime: String?) = viewModelScope.launch {
        messageRepository.updateConversationPinTimeById(conversationId, circleId, pinTime)
    }

    fun mute(senderId: String, recipientId: String, duration: Long) {
        viewModelScope.launch(SINGLE_DB_THREAD) {
            var conversationId = messageRepository.getConversationIdIfExistsSync(recipientId)
            if (conversationId == null) {
                conversationId = generateConversationId(senderId, recipientId)
            }
            val participantRequest = ParticipantRequest(recipientId, "")
            jobManager.addJobInBackground(
                ConversationJob(
                    ConversationRequest(
                        conversationId,
                        ConversationCategory.CONTACT.name, duration = duration, participants = listOf(participantRequest)
                    ),
                    recipientId = recipientId, type = ConversationJob.TYPE_MUTE
                )
            )
        }
    }

    fun mute(conversationId: String, duration: Long) {
        jobManager.addJobInBackground(
            ConversationJob(
                conversationId = conversationId,
                request = ConversationRequest(conversationId, ConversationCategory.GROUP.name, duration = duration),
                type = ConversationJob.TYPE_MUTE
            )
        )
    }

    suspend fun suspendFindUserById(query: String) = userRepository.suspendFindUserById(query)

    suspend fun findFirstUnreadMessageId(conversationId: String, offset: Int): String? =
        conversationRepository.findFirstUnreadMessageId(conversationId, offset)

    fun observeAllCircleItem() = userRepository.observeAllCircleItem()

    suspend fun circleRename(circleId: String, name: String) = userRepository.circleRename(circleId, name)

    suspend fun deleteCircle(circleId: String) = userRepository.deleteCircle(circleId)

    suspend fun deleteCircleById(circleId: String) = userRepository.deleteCircleById(circleId)

    suspend fun insertCircle(circle: Circle) = userRepository.insertCircle(circle)

    suspend fun getFriends(): List<User> = userRepository.getFriends()

    suspend fun successConversationList() = conversationRepository.successConversationList()

    suspend fun findConversationItemByCircleId(circleId: String) =
        userRepository.findConversationItemByCircleId(circleId)

    suspend fun updateCircleConversations(id: String, circleConversationRequests: List<CircleConversationRequest>) =
        userRepository.updateCircleConversations(id, circleConversationRequests)

    fun sortCircleConversations(list: List<CircleOrder>?) = viewModelScope.launch { userRepository.sortCircleConversations(list) }

    suspend fun saveCircle(
        oldCircleConversationRequests: Set<CircleConversationRequest>,
        newCircleConversationRequests: List<CircleConversationRequest>,
        circleId: String
    ): Boolean {
        handleMixinResponse(
            switchContext = Dispatchers.IO,
            invokeNetwork = {
                userRepository.getCircleById(circleId)
            },
            successBlock = {
                it.data?.let { circle ->
                    userRepository.insertCircle(circle)
                    return@handleMixinResponse circle
                }
            }
        ) ?: return false

        val safeSet = oldCircleConversationRequests.intersect(newCircleConversationRequests)
        val removeSet = oldCircleConversationRequests.subtract(safeSet)
        val addSet = newCircleConversationRequests.subtract(safeSet)

        removeSet.forEach { cc ->
            userRepository.deleteCircleConversation(cc.conversationId, circleId)
        }
        addSet.forEach { cc ->
            val circleConversation = CircleConversation(
                cc.conversationId,
                cc.contactId,
                circleId,
                nowInUtc(),
                null
            )
            userRepository.insertCircleConversation(circleConversation)
        }
        return true
    }

    suspend fun findCircleConversationByCircleId(circleId: String) =
        userRepository.findCircleConversationByCircleId(circleId)
}

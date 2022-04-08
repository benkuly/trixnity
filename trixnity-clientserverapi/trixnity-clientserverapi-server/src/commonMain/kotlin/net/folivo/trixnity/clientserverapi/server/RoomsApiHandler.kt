package net.folivo.trixnity.clientserverapi.server

import net.folivo.trixnity.api.server.MatrixEndpointContext
import net.folivo.trixnity.clientserverapi.model.rooms.*
import net.folivo.trixnity.core.model.events.Event
import net.folivo.trixnity.core.model.events.MessageEventContent
import net.folivo.trixnity.core.model.events.RoomAccountDataEventContent
import net.folivo.trixnity.core.model.events.StateEventContent
import net.folivo.trixnity.core.model.events.m.TagEventContent

interface RoomsApiHandler {
    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomideventeventid">matrix spec</a>
     */
    suspend fun getEvent(context: MatrixEndpointContext<GetEvent, Unit, Event<*>>): Event<*>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    suspend fun getStateEvent(context: MatrixEndpointContext<GetStateEvent, Unit, StateEventContent>): StateEventContent

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidstate">matrix spec</a>
     */
    suspend fun getState(context: MatrixEndpointContext<GetState, Unit, List<Event.StateEvent<*>>>): List<Event.StateEvent<*>>

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidmembers">matrix spec</a>
     */
    suspend fun getMembers(context: MatrixEndpointContext<GetMembers, Unit, GetMembers.Response>): GetMembers.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidjoined_members">matrix spec</a>
     */
    suspend fun getJoinedMembers(context: MatrixEndpointContext<GetJoinedMembers, Unit, GetJoinedMembers.Response>): GetJoinedMembers.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidmessages">matrix spec</a>
     */
    suspend fun getEvents(context: MatrixEndpointContext<GetEvents, Unit, GetEvents.Response>): GetEvents.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidstateeventtypestatekey">matrix spec</a>
     */
    suspend fun sendStateEvent(context: MatrixEndpointContext<SendStateEvent, StateEventContent, SendEventResponse>): SendEventResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidsendeventtypetxnid">matrix spec</a>
     */
    suspend fun sendMessageEvent(context: MatrixEndpointContext<SendMessageEvent, MessageEventContent, SendEventResponse>): SendEventResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidredacteventidtxnid">matrix spec</a>
     */
    suspend fun redactEvent(context: MatrixEndpointContext<RedactEvent, RedactEvent.Request, SendEventResponse>): SendEventResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3createroom">matrix spec</a>
     */
    suspend fun createRoom(context: MatrixEndpointContext<CreateRoom, CreateRoom.Request, CreateRoom.Response>): CreateRoom.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun setRoomAlias(context: MatrixEndpointContext<SetRoomAlias, SetRoomAlias.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun getRoomAlias(context: MatrixEndpointContext<GetRoomAlias, Unit, GetRoomAlias.Response>): GetRoomAlias.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidaliases">matrix spec</a>
     */
    suspend fun getRoomAliases(context: MatrixEndpointContext<GetRoomAliases, Unit, GetRoomAliases.Response>): GetRoomAliases.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3directoryroomroomalias">matrix spec</a>
     */
    suspend fun deleteRoomAlias(context: MatrixEndpointContext<DeleteRoomAlias, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3joined_rooms">matrix spec</a>
     */
    suspend fun getJoinedRooms(context: MatrixEndpointContext<GetJoinedRooms, Unit, GetJoinedRooms.Response>): GetJoinedRooms.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidinvite">matrix spec</a>
     */
    suspend fun inviteUser(context: MatrixEndpointContext<InviteUser, InviteUser.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidkick">matrix spec</a>
     */
    suspend fun kickUser(context: MatrixEndpointContext<KickUser, KickUser.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidban">matrix spec</a>
     */
    suspend fun banUser(context: MatrixEndpointContext<BanUser, BanUser.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidunban">matrix spec</a>
     */
    suspend fun unbanUser(context: MatrixEndpointContext<UnbanUser, UnbanUser.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3joinroomidoralias">matrix spec</a>
     */
    suspend fun joinRoom(context: MatrixEndpointContext<JoinRoom, JoinRoom.Request, JoinRoom.Response>): JoinRoom.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3knockroomidoralias">matrix spec</a>
     */
    suspend fun knockRoom(context: MatrixEndpointContext<KnockRoom, KnockRoom.Request, KnockRoom.Response>): KnockRoom.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidforget">matrix spec</a>
     */
    suspend fun forgetRoom(context: MatrixEndpointContext<ForgetRoom, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidleave">matrix spec</a>
     */
    suspend fun leaveRoom(context: MatrixEndpointContext<LeaveRoom, LeaveRoom.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidreceiptreceipttypeeventid">matrix spec</a>
     */
    suspend fun setReceipt(context: MatrixEndpointContext<SetReceipt, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidread_markers">matrix spec</a>
     */
    suspend fun setReadMarkers(context: MatrixEndpointContext<SetReadMarkers, SetReadMarkers.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3roomsroomidtypinguserid">matrix spec</a>
     */
    suspend fun setTyping(context: MatrixEndpointContext<SetTyping, SetTyping.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    suspend fun getAccountData(context: MatrixEndpointContext<GetRoomAccountData, Unit, RoomAccountDataEventContent>): RoomAccountDataEventContent

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridroomsroomidaccount_datatype">matrix spec</a>
     */
    suspend fun setAccountData(context: MatrixEndpointContext<SetRoomAccountData, RoomAccountDataEventContent, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3directorylistroomroomid">matrix spec</a>
     */
    suspend fun getDirectoryVisibility(context: MatrixEndpointContext<GetDirectoryVisibility, Unit, GetDirectoryVisibility.Response>): GetDirectoryVisibility.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3directorylistroomroomid">matrix spec</a>
     */
    suspend fun setDirectoryVisibility(context: MatrixEndpointContext<SetDirectoryVisibility, SetDirectoryVisibility.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3publicrooms">matrix spec</a>
     */
    suspend fun getPublicRooms(context: MatrixEndpointContext<GetPublicRooms, Unit, GetPublicRoomsResponse>): GetPublicRoomsResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3publicrooms">matrix spec</a>
     */
    suspend fun getPublicRoomsWithFilter(context: MatrixEndpointContext<GetPublicRoomsWithFilter, GetPublicRoomsWithFilter.Request, GetPublicRoomsResponse>): GetPublicRoomsResponse

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3useruseridroomsroomidtags">matrix spec</a>
     */
    suspend fun getTags(context: MatrixEndpointContext<GetRoomTags, Unit, TagEventContent>): TagEventContent

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#put_matrixclientv3useruseridroomsroomidtagstag">matrix spec</a>
     */
    suspend fun setTag(context: MatrixEndpointContext<SetRoomTag, TagEventContent.Tag, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#delete_matrixclientv3useruseridroomsroomidtagstag">matrix spec</a>
     */
    suspend fun deleteTag(context: MatrixEndpointContext<DeleteRoomTag, Unit, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv3roomsroomidcontexteventid">matrix spec</a>
     */
    suspend fun getEventContext(context: MatrixEndpointContext<GetEventContext, Unit, GetEventContext.Response>): GetEventContext.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidreporteventid">matrix spec</a>
     */
    suspend fun reportEvent(context: MatrixEndpointContext<ReportEvent, ReportEvent.Request, Unit>)

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#post_matrixclientv3roomsroomidupgrade">matrix spec</a>
     */
    suspend fun upgradeRoom(context: MatrixEndpointContext<UpgradeRoom, UpgradeRoom.Request, UpgradeRoom.Response>): UpgradeRoom.Response

    /**
     * @see <a href="https://spec.matrix.org/v1.2/client-server-api/#get_matrixclientv1roomsroomidhierarchy">matrix spec</a>
     */
    suspend fun getHierarchy(context: MatrixEndpointContext<GetHierarchy, Unit, GetHierarchy.Response>): GetHierarchy.Response
}
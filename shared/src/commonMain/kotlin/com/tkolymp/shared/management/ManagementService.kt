package com.tkolymp.shared.management

import com.tkolymp.shared.Logger
import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes

/**
 * Trainer / administrator content management: creating, editing and deleting club events
 * and announcements. Mutating calls throw on failure (including server-side permission
 * errors) so view models can show the server's message.
 */
interface IManagementService {
    suspend fun getPermissions(forceRefresh: Boolean = false): UserPermissions
    suspend fun getOptions(forceRefresh: Boolean = false): ManagementOptions

    suspend fun loadEventDraft(instanceId: Long): EventDraft?
    /** Creates a single-instance event and returns the new instance id. */
    suspend fun createEvent(draft: EventDraft): Long
    suspend fun updateEvent(instanceId: Long, draft: EventDraft)
    suspend fun setEventCancelled(instanceId: Long, cancelled: Boolean)
    suspend fun deleteEvent(instanceId: Long)

    suspend fun loadAnnouncementDraft(id: Long): AnnouncementDraft?
    /** Creates an announcement and returns its id. */
    suspend fun createAnnouncement(draft: AnnouncementDraft): Long
    suspend fun updateAnnouncement(id: Long, draft: AnnouncementDraft)
    suspend fun deleteAnnouncement(id: Long)
}

class ManagementService(
    private val client: IGraphQlClient,
    private val cache: CacheService,
) : IManagementService {

    override suspend fun getPermissions(forceRefresh: Boolean): UserPermissions {
        if (!forceRefresh) {
            try { cache.get<UserPermissions>(PERMISSIONS_KEY)?.let { return it } } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        val user = try {
            client.post(ManagementGraphQl.CURRENT_USER_QUERY, null).dataObject()?.obj("getCurrentUser")
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("ManagementService", "getPermissions: user query failed: ${ex.message}")
            return UserPermissions.NONE
        } ?: return UserPermissions.NONE

        // Staff lists are a fallback for backends where the user flags are not populated.
        val tenant = try {
            client.post(ManagementGraphQl.STAFF_QUERY, null).dataObject()?.obj("getCurrentTenant")
        } catch (e: CancellationException) { throw e } catch (ex: Exception) {
            Logger.d("ManagementService", "getPermissions: staff query failed: ${ex.message}")
            null
        }
        val permissions = ManagementGraphQl.parsePermissions(user, tenant)
        try { cache.put(PERMISSIONS_KEY, permissions, ttl = 10.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return permissions
    }

    override suspend fun getOptions(forceRefresh: Boolean): ManagementOptions {
        if (!forceRefresh) {
            try { cache.get<ManagementOptions>(OPTIONS_KEY)?.let { return it } } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        val tenant = client.post(ManagementGraphQl.OPTIONS_QUERY, null).dataObject()?.obj("getCurrentTenant")
            ?: return ManagementOptions()
        val options = ManagementGraphQl.parseOptions(tenant)
        try { cache.put(OPTIONS_KEY, options, ttl = 10.minutes) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        return options
    }

    override suspend fun loadEventDraft(instanceId: Long): EventDraft? {
        val vars = buildJsonObject { put("id", JsonPrimitive(instanceId.toString())) }
        val inst = client.post(ManagementGraphQl.EVENT_DRAFT_QUERY, vars).dataObject()?.obj("eventInstance") ?: return null
        return ManagementGraphQl.parseEventDraft(inst)
    }

    override suspend fun createEvent(draft: EventDraft): Long {
        val resp = client.post(ManagementGraphQl.SAVE_EVENTS_MUTATION, ManagementGraphQl.saveEventsVariables(draft))
        val ids = (resp.dataObject()?.obj("saveEvents")?.get("eventInstances") as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.str("id")?.toLongOrNull() }
            .orEmpty()
        val id = ids.firstOrNull() ?: throw IllegalStateException("saveEvents returned no event instance")
        // saveEvents has no summary/description fields; set them on the created instance.
        if (draft.summary.isNotBlank() || draft.description.isNotBlank()) {
            client.post(ManagementGraphQl.UPDATE_EVENT_INSTANCE_MUTATION, ManagementGraphQl.updateEventVariables(id, ManagementGraphQl.textPatch(draft)))
        }
        invalidateEvents(id)
        return id
    }

    override suspend fun updateEvent(instanceId: Long, draft: EventDraft) {
        client.post(ManagementGraphQl.UPDATE_EVENT_INSTANCE_MUTATION, ManagementGraphQl.updateEventVariables(instanceId, ManagementGraphQl.eventPatch(draft)))
        invalidateEvents(instanceId)
    }

    override suspend fun setEventCancelled(instanceId: Long, cancelled: Boolean) {
        val patch = buildJsonObject { put("isCancelled", cancelled) }
        client.post(ManagementGraphQl.UPDATE_EVENT_INSTANCE_MUTATION, ManagementGraphQl.updateEventVariables(instanceId, patch))
        invalidateEvents(instanceId)
    }

    override suspend fun deleteEvent(instanceId: Long) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("id", instanceId.toString()) }) }
        client.post(ManagementGraphQl.DELETE_EVENT_INSTANCE_MUTATION, vars)
        invalidateEvents(instanceId)
    }

    override suspend fun loadAnnouncementDraft(id: Long): AnnouncementDraft? {
        val vars = buildJsonObject { put("id", JsonPrimitive(id.toString())) }
        val a = client.post(ManagementGraphQl.ANNOUNCEMENT_DRAFT_QUERY, vars).dataObject()?.obj("announcement") ?: return null
        return ManagementGraphQl.parseAnnouncementDraft(a)
    }

    override suspend fun createAnnouncement(draft: AnnouncementDraft): Long {
        val resp = client.post(ManagementGraphQl.UPSERT_ANNOUNCEMENT_MUTATION, ManagementGraphQl.upsertAnnouncementVariables(draft))
        val id = resp.dataObject()?.obj("upsertAnnouncement")?.obj("announcement")?.str("id")?.toLongOrNull()
            ?: throw IllegalStateException("upsertAnnouncement returned no announcement")
        invalidateAnnouncements(id)
        return id
    }

    override suspend fun updateAnnouncement(id: Long, draft: AnnouncementDraft) {
        client.post(ManagementGraphQl.UPDATE_ANNOUNCEMENT_MUTATION, ManagementGraphQl.updateAnnouncementVariables(id, draft))
        invalidateAnnouncements(id)
    }

    override suspend fun deleteAnnouncement(id: Long) {
        val vars = buildJsonObject { put("input", buildJsonObject { put("id", id.toString()) }) }
        client.post(ManagementGraphQl.DELETE_ANNOUNCEMENT_MUTATION, vars)
        invalidateAnnouncements(id)
    }

    private suspend fun invalidateEvents(instanceId: Long) {
        // Event lists are cached under several namespaces (calendar, overview, events tab, widgets).
        for (prefix in listOf("events", "calendar", "cal_", "overview", "camps", "timeline", "free_lessons", "widget")) {
            try { cache.invalidatePrefix(prefix) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        }
        try { cache.invalidate("event_$instanceId") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    private suspend fun invalidateAnnouncements(id: Long) {
        try { cache.invalidatePrefix("announcements") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        try { cache.invalidatePrefix("overview") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
        try { cache.invalidate("announcement_$id") } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    private companion object {
        const val PERMISSIONS_KEY = "management_permissions"
        const val OPTIONS_KEY = "management_options"
    }
}

/** GraphQL documents plus pure request builders / response parsers (unit-tested). */
internal object ManagementGraphQl {
    const val CURRENT_USER_QUERY =
        "query ManagementCurrentUser { getCurrentUser { id userProxiesList { person { id isAdmin isTrainer } } } }"

    const val STAFF_QUERY =
        "query ManagementStaff { getCurrentTenant { tenantTrainersList { personId status } tenantAdministratorsList { personId status } } }"

    const val OPTIONS_QUERY = """
        query ManagementOptions {
          getCurrentTenant {
            tenantTrainersList { personId status person { id firstName lastName } }
            tenantLocationsList { id name }
            cohortsList(orderBy: [NAME_ASC]) { id name colorRgb isVisible isArchived }
          }
        }
    """

    const val EVENT_DRAFT_QUERY = """
        query ManagementEventDraft(${'$'}id: BigInt!) {
          eventInstance(id: ${'$'}id) {
            id name type since until locationId locationText capacity
            isVisible isPublic isLocked enableNotes isCancelled summary description
            eventInstanceTrainersByInstanceIdList { personId }
            targetCohortsList { cohortId }
          }
        }
    """

    const val ANNOUNCEMENT_DRAFT_QUERY = """
        query ManagementAnnouncementDraft(${'$'}id: BigInt!) {
          announcement(id: ${'$'}id) {
            id title body isSticky status authorId
            announcementAudiences { nodes { cohortId } }
          }
        }
    """

    const val SAVE_EVENTS_MUTATION = """
        mutation ManagementSaveEvents(${'$'}input: SaveEventsInput!) {
          saveEvents(input: ${'$'}input) { eventInstances { id } }
        }
    """

    const val UPDATE_EVENT_INSTANCE_MUTATION = """
        mutation ManagementUpdateEventInstance(${'$'}input: UpdateEventInstanceInput!) {
          updateEventInstance(input: ${'$'}input) { eventInstance { id } }
        }
    """

    const val DELETE_EVENT_INSTANCE_MUTATION = """
        mutation ManagementDeleteEventInstance(${'$'}input: DeleteEventInstanceInput!) {
          deleteEventInstance(input: ${'$'}input) { eventInstance { id } }
        }
    """

    const val UPSERT_ANNOUNCEMENT_MUTATION = """
        mutation ManagementUpsertAnnouncement(${'$'}input: UpsertAnnouncementInput!) {
          upsertAnnouncement(input: ${'$'}input) { announcement { id } }
        }
    """

    const val UPDATE_ANNOUNCEMENT_MUTATION = """
        mutation ManagementUpdateAnnouncement(${'$'}input: UpdateAnnouncementInput!) {
          updateAnnouncement(input: ${'$'}input) { announcement { id } }
        }
    """

    const val DELETE_ANNOUNCEMENT_MUTATION = """
        mutation ManagementDeleteAnnouncement(${'$'}input: DeleteAnnouncementInput!) {
          deleteAnnouncement(input: ${'$'}input) { announcement { id } }
        }
    """

    fun parsePermissions(user: JsonObject, tenant: JsonObject?): UserPermissions {
        val persons = (user["userProxiesList"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.obj("person") }
            .orEmpty()
        val personIds = persons.mapNotNull { it.str("id") }.toSet()
        fun activeStaff(key: String): Set<String> = (tenant?.get(key) as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.filter { it.str("status").let { s -> s == null || s == "ACTIVE" } }
            ?.mapNotNull { it.str("personId") }
            ?.toSet()
            .orEmpty()
        val isAdmin = persons.any { it.bool("isAdmin") == true } || activeStaff("tenantAdministratorsList").any { it in personIds }
        val isTrainer = persons.any { it.bool("isTrainer") == true } || activeStaff("tenantTrainersList").any { it in personIds }
        return UserPermissions(
            userId = user.str("id"),
            personIds = personIds,
            isTrainer = isTrainer,
            isAdmin = isAdmin,
        )
    }

    fun parseOptions(tenant: JsonObject): ManagementOptions {
        val trainers = (tenant["tenantTrainersList"] as? JsonArray).objects()
            .filter { it.str("status").let { s -> s == null || s == "ACTIVE" } }
            .mapNotNull { t ->
                val person = t.obj("person")
                val id = t.str("personId") ?: person?.str("id") ?: return@mapNotNull null
                val name = listOfNotNull(person?.str("firstName"), person?.str("lastName")).joinToString(" ").trim()
                ManagementOption(id, name.ifBlank { id })
            }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
        val locations = (tenant["tenantLocationsList"] as? JsonArray).objects()
            .mapNotNull { l -> ManagementOption(l.str("id") ?: return@mapNotNull null, l.str("name").orEmpty()) }
            .sortedBy { it.name.lowercase() }
        val cohorts = (tenant["cohortsList"] as? JsonArray).objects()
            .filter { it.bool("isArchived") != true }
            .mapNotNull { c -> ManagementOption(c.str("id") ?: return@mapNotNull null, c.str("name").orEmpty(), c.str("colorRgb")) }
        return ManagementOptions(trainers = trainers, cohorts = cohorts, locations = locations)
    }

    fun parseEventDraft(inst: JsonObject): EventDraft = EventDraft(
        name = inst.str("name").orEmpty(),
        type = inst.str("type")?.let { t -> ManagedEventType.entries.firstOrNull { it.name == t } } ?: ManagedEventType.LESSON,
        sinceIso = inst.str("since").orEmpty(),
        untilIso = inst.str("until").orEmpty(),
        locationId = inst.str("locationId"),
        locationText = inst.str("locationText").orEmpty(),
        capacity = (inst["capacity"] as? JsonPrimitive)?.intOrNull,
        isVisible = inst.bool("isVisible") ?: true,
        isPublic = inst.bool("isPublic") ?: false,
        isLocked = inst.bool("isLocked") ?: false,
        enableNotes = inst.bool("enableNotes") ?: false,
        isCancelled = inst.bool("isCancelled") ?: false,
        summary = inst.str("summary").orEmpty(),
        description = inst.str("description").orEmpty(),
        trainerPersonIds = (inst["eventInstanceTrainersByInstanceIdList"] as? JsonArray).objects().mapNotNull { it.str("personId") },
        cohortIds = (inst["targetCohortsList"] as? JsonArray).objects().mapNotNull { it.str("cohortId") },
    )

    fun parseAnnouncementDraft(a: JsonObject): AnnouncementDraft = AnnouncementDraft(
        title = a.str("title").orEmpty(),
        body = a.str("body").orEmpty(),
        isSticky = a.bool("isSticky") ?: false,
        status = a.str("status")?.let { s -> ManagedAnnouncementStatus.entries.firstOrNull { it.name == s } } ?: ManagedAnnouncementStatus.PUBLISHED,
        cohortIds = (a.obj("announcementAudiences")?.get("nodes") as? JsonArray).objects().mapNotNull { it.str("cohortId") },
    )

    fun saveEventsVariables(draft: EventDraft): JsonObject = buildJsonObject {
        put("input", buildJsonObject {
            put("details", buildJsonObject {
                put("name", draft.name.trim())
                put("type", draft.type.name)
                if (draft.locationId != null) put("locationId", draft.locationId)
                put("locationText", draft.locationText.trim())
                if (draft.capacity != null) put("capacity", draft.capacity)
                put("capacityUnit", "PEOPLE")
                put("isVisible", draft.isVisible)
                put("isPublic", draft.isPublic)
                put("isLocked", draft.isLocked)
                put("enableNotes", draft.enableNotes)
            })
            put("events", buildJsonArray {
                add(buildJsonObject {
                    put("since", draft.sinceIso)
                    put("until", draft.untilIso)
                    put("isCancelled", draft.isCancelled)
                    put("registrations", JsonArray(emptyList()))
                })
            })
            put("trainers", buildJsonArray {
                draft.trainerPersonIds.distinct().forEach { pid ->
                    add(buildJsonObject { put("personId", pid) })
                }
            })
            put("cohortIds", buildJsonArray { draft.cohortIds.distinct().forEach { add(JsonPrimitive(it)) } })
        })
    }

    /** Patch for editing an existing instance; trainers and cohorts are not part of it. */
    fun eventPatch(draft: EventDraft): JsonObject = buildJsonObject {
        put("name", draft.name.trim())
        put("type", draft.type.name)
        put("since", draft.sinceIso)
        put("until", draft.untilIso)
        put("locationId", draft.locationId?.let { JsonPrimitive(it) } ?: JsonNull)
        put("locationText", draft.locationText.trim())
        put("capacity", draft.capacity?.let { JsonPrimitive(it) } ?: JsonNull)
        put("isVisible", draft.isVisible)
        put("isPublic", draft.isPublic)
        put("isLocked", draft.isLocked)
        put("enableNotes", draft.enableNotes)
        put("isCancelled", draft.isCancelled)
        put("summary", draft.summary)
        put("description", draft.description)
    }

    fun textPatch(draft: EventDraft): JsonObject = buildJsonObject {
        put("summary", draft.summary)
        put("description", draft.description)
    }

    fun updateEventVariables(instanceId: Long, patch: JsonObject): JsonObject = buildJsonObject {
        put("input", buildJsonObject {
            put("id", instanceId.toString())
            put("patch", patch)
        })
    }

    fun upsertAnnouncementVariables(draft: AnnouncementDraft): JsonObject = buildJsonObject {
        put("input", buildJsonObject {
            put("info", buildJsonObject {
                put("title", draft.title.trim())
                put("body", draft.body)
                put("isSticky", draft.isSticky)
                put("status", draft.status.name)
            })
            put("audiences", buildJsonArray {
                draft.cohortIds.distinct().forEach { cid -> add(buildJsonObject { put("cohortId", cid) }) }
            })
            put("attachments", JsonArray(emptyList()))
        })
    }

    /** Edits only the announcement's own columns; audiences are left untouched. */
    fun updateAnnouncementVariables(id: Long, draft: AnnouncementDraft): JsonObject = buildJsonObject {
        put("input", buildJsonObject {
            put("id", id.toString())
            put("patch", buildJsonObject {
                put("title", draft.title.trim())
                put("body", draft.body)
                put("isSticky", draft.isSticky)
                put("status", draft.status.name)
            })
        })
    }
}

private fun JsonElement.dataObject(): JsonObject? = (this as? JsonObject)?.get("data") as? JsonObject
private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.let { it.booleanOrNull ?: it.contentOrNull?.toBooleanStrictOrNull() }
private fun JsonArray?.objects(): List<JsonObject> = this?.mapNotNull { it as? JsonObject }.orEmpty()

package com.tkolymp.tkolympapp

import com.tkolymp.shared.cache.CacheService
import com.tkolymp.shared.management.AnnouncementDraft
import com.tkolymp.shared.management.EventDraft
import com.tkolymp.shared.management.ManagedEventType
import com.tkolymp.shared.management.ManagementGraphQl
import com.tkolymp.shared.management.ManagementService
import com.tkolymp.shared.management.RichTextBody
import com.tkolymp.shared.management.UserPermissions
import com.tkolymp.shared.network.GraphQlException
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Answers each request with the first response whose key occurs in the query text. */
private class RoutingGraphQlClient(private val routes: Map<String, String>) : IGraphQlClient {
    val calls = mutableListOf<Pair<String, JsonObject?>>()
    override suspend fun post(query: String, variables: JsonObject?): JsonElement {
        calls += query to variables
        val body = routes.entries.firstOrNull { query.contains(it.key) }?.value
            ?: throw GraphQlException("unexpected query: $query")
        if (body.startsWith("ERROR:")) throw GraphQlException(body.removePrefix("ERROR:"))
        return Json.parseToJsonElement(body)
    }
}

class ManagementServiceTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    // ── permissions ────────────────────────────────────────────────────────────

    @Test
    fun permissions_fromUserFlags() {
        val p = ManagementGraphQl.parsePermissions(
            obj("""{"id":"7","userProxiesList":[{"person":{"id":"42","isAdmin":false,"isTrainer":true}}]}"""),
            null,
        )
        assertEquals(UserPermissions(userId = "7", personIds = setOf("42"), isTrainer = true, isAdmin = false), p)
        assertTrue(p.canManage)
    }

    @Test
    fun permissions_fallBackToActiveStaffLists() {
        val p = ManagementGraphQl.parsePermissions(
            obj("""{"id":"7","userProxiesList":[{"person":{"id":"42","isAdmin":null,"isTrainer":null}}]}"""),
            obj(
                """{"tenantTrainersList":[{"personId":"42","status":"EXPIRED"}],
                   "tenantAdministratorsList":[{"personId":"42","status":"ACTIVE"}]}"""
            ),
        )
        assertFalse(p.isTrainer, "expired trainer contract must not grant trainer mode")
        assertTrue(p.isAdmin)
    }

    @Test
    fun permissions_regularMemberCannotManage() {
        val p = ManagementGraphQl.parsePermissions(
            obj("""{"id":"7","userProxiesList":[{"person":{"id":"42","isAdmin":false,"isTrainer":false}}]}"""),
            obj("""{"tenantTrainersList":[{"personId":"1","status":"ACTIVE"}],"tenantAdministratorsList":[]}"""),
        )
        assertFalse(p.canManage)
        assertFalse(p.canEditEvent(listOf("42"), emptyList()))
        assertFalse(p.canEditAnnouncement("7"))
    }

    @Test
    fun trainerEditsOnlyOwnContent_adminEditsAll() {
        val trainer = UserPermissions(userId = "7", personIds = setOf("42"), isTrainer = true)
        assertTrue(trainer.canEditEvent(listOf("1", "42"), emptyList()))
        assertTrue(trainer.canEditEvent(emptyList(), listOf("42")))
        assertFalse(trainer.canEditEvent(listOf("1"), listOf("2")))
        assertTrue(trainer.canEditAnnouncement("7"))
        assertFalse(trainer.canEditAnnouncement("8"))
        assertFalse(trainer.canEditAnnouncement(null))

        val admin = UserPermissions(userId = "9", isAdmin = true)
        assertTrue(admin.canEditEvent(listOf("1"), emptyList()))
        assertTrue(admin.canEditAnnouncement("8"))
    }

    @Test
    fun getPermissions_isCachedAndDegradesToNoneOnFailure() = runTest {
        val client = RoutingGraphQlClient(
            mapOf(
                "ManagementCurrentUser" to """{"data":{"getCurrentUser":{"id":"7","userProxiesList":[{"person":{"id":"42","isAdmin":true,"isTrainer":false}}]}}}""",
                "ManagementStaff" to "ERROR:permission denied",
            )
        )
        val service = ManagementService(client, CacheService())
        assertTrue(service.getPermissions().isAdmin)
        assertTrue(service.getPermissions().isAdmin)
        assertEquals(2, client.calls.size, "second call must be served from cache")

        val failing = ManagementService(RoutingGraphQlClient(mapOf("ManagementCurrentUser" to "ERROR:offline")), CacheService())
        assertEquals(UserPermissions.NONE, failing.getPermissions())
    }

    // ── events ─────────────────────────────────────────────────────────────────

    @Test
    fun saveEventsVariables_matchSchema() {
        val draft = EventDraft(
            name = "  Latin  ",
            type = ManagedEventType.GROUP,
            sinceIso = "2026-10-01T16:00:00Z",
            untilIso = "2026-10-01T17:30:00Z",
            locationId = "3",
            capacity = 12,
            trainerPersonIds = listOf("42", "42"),
            cohortIds = listOf("5"),
        )
        val input = ManagementGraphQl.saveEventsVariables(draft)["input"]!!.jsonObject
        val details = input["details"]!!.jsonObject
        assertEquals("Latin", details["name"]!!.jsonPrimitive.content)
        assertEquals("GROUP", details["type"]!!.jsonPrimitive.content)
        assertEquals("3", details["locationId"]!!.jsonPrimitive.content)
        assertEquals(12, details["capacity"]!!.jsonPrimitive.content.toInt())
        val event = input["events"]!!.jsonArray.single().jsonObject
        assertEquals("2026-10-01T16:00:00Z", event["since"]!!.jsonPrimitive.content)
        assertEquals(JsonArray(emptyList()), event["registrations"])
        assertEquals(listOf("42"), input["trainers"]!!.jsonArray.map { it.jsonObject["personId"]!!.jsonPrimitive.content })
        assertEquals(listOf(JsonPrimitive("5")), input["cohortIds"]!!.jsonArray.toList())
    }

    @Test
    fun createEvent_setsDescriptionOnCreatedInstance() = runTest {
        val client = RoutingGraphQlClient(
            mapOf(
                "ManagementSaveEvents" to """{"data":{"saveEvents":{"eventInstances":[{"id":"555"}]}}}""",
                "ManagementUpdateEventInstance" to """{"data":{"updateEventInstance":{"eventInstance":{"id":"555"}}}}""",
            )
        )
        val id = ManagementService(client, CacheService()).createEvent(
            EventDraft(name = "Lekce", sinceIso = "2026-10-01T16:00:00Z", untilIso = "2026-10-01T17:00:00Z", description = "<p>Hi</p>")
        )
        assertEquals(555L, id)
        assertEquals(2, client.calls.size)
        val update = client.calls[1].second!!["input"]!!.jsonObject
        assertEquals("555", update["id"]!!.jsonPrimitive.content)
        assertEquals("<p>Hi</p>", update["patch"]!!.jsonObject["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun createEvent_withoutDescription_isSingleCall() = runTest {
        val client = RoutingGraphQlClient(
            mapOf("ManagementSaveEvents" to """{"data":{"saveEvents":{"eventInstances":[{"id":"1"}]}}}""")
        )
        ManagementService(client, CacheService()).createEvent(EventDraft(name = "x", sinceIso = "a", untilIso = "b"))
        assertEquals(1, client.calls.size)
    }

    @Test
    fun deleteEvent_propagatesServerPermissionError() = runTest {
        val client = RoutingGraphQlClient(mapOf("ManagementDeleteEventInstance" to "ERROR:No values were deleted"))
        val ex = assertFailsWith<GraphQlException> { ManagementService(client, CacheService()).deleteEvent(9) }
        assertEquals("No values were deleted", ex.message)
    }

    @Test
    fun deleteEvent_invalidatesCachedEventLists() = runTest {
        val cache = CacheService()
        cache.put("calendar_week", "stale")
        cache.put("event_9", "stale")
        cache.put("people_all", "kept")
        val client = RoutingGraphQlClient(mapOf("ManagementDeleteEventInstance" to """{"data":{"deleteEventInstance":{"eventInstance":{"id":"9"}}}}"""))
        ManagementService(client, cache).deleteEvent(9)
        assertEquals(null, cache.get<String>("calendar_week"))
        assertEquals(null, cache.get<String>("event_9"))
        assertEquals("kept", cache.get<String>("people_all"))
    }

    @Test
    fun parseEventDraft_readsTrainersAndCohorts() {
        val draft = ManagementGraphQl.parseEventDraft(
            obj(
                """{"id":"1","name":"Camp","type":"CAMP","since":"s","until":"u","locationId":null,"capacity":20,
                   "isVisible":true,"isPublic":false,"isLocked":true,"enableNotes":false,"isCancelled":false,
                   "summary":null,"description":"<p>d</p>",
                   "eventInstanceTrainersByInstanceIdList":[{"personId":"42"}],"targetCohortsList":[{"cohortId":"5"}]}"""
            )
        )
        assertEquals(ManagedEventType.CAMP, draft.type)
        assertEquals(20, draft.capacity)
        assertEquals(null, draft.locationId)
        assertTrue(draft.isLocked)
        assertEquals(listOf("42"), draft.trainerPersonIds)
        assertEquals(listOf("5"), draft.cohortIds)
    }

    // ── announcements ──────────────────────────────────────────────────────────

    @Test
    fun createAnnouncement_sendsAudiencesAndReturnsId() = runTest {
        val client = RoutingGraphQlClient(
            mapOf("ManagementUpsertAnnouncement" to """{"data":{"upsertAnnouncement":{"announcement":{"id":"77"}}}}""")
        )
        val id = ManagementService(client, CacheService()).createAnnouncement(
            AnnouncementDraft(title = " Hi ", body = "<p>x</p>", isSticky = true, cohortIds = listOf("5"))
        )
        assertEquals(77L, id)
        val input = client.calls.single().second!!["input"]!!.jsonObject
        val info = input["info"]!!.jsonObject
        assertEquals("Hi", info["title"]!!.jsonPrimitive.content)
        assertEquals("PUBLISHED", info["status"]!!.jsonPrimitive.content)
        assertEquals("true", info["isSticky"]!!.jsonPrimitive.content)
        assertEquals("5", input["audiences"]!!.jsonArray.single().jsonObject["cohortId"]!!.jsonPrimitive.content)
    }

    @Test
    fun updateAnnouncement_patchesOnlyOwnColumns() {
        val input = ManagementGraphQl.updateAnnouncementVariables(3, AnnouncementDraft(title = "T", body = "B"))["input"]!!.jsonObject
        assertEquals("3", input["id"]!!.jsonPrimitive.content)
        assertEquals(setOf("title", "body", "isSticky", "status"), input["patch"]!!.jsonObject.keys)
    }

    // ── body formatting ────────────────────────────────────────────────────────

    @Test
    fun richText_legacyPlainTextKeepsLineBreaks() {
        assertEquals(
            "<p>Ahoj &amp; ostatní<br>druhý řádek</p><p>Nový odstavec</p>",
            RichTextBody.toEditorHtml("Ahoj & ostatní\ndruhý řádek\n\nNový odstavec"),
        )
        assertEquals("<p><b>x</b></p>", RichTextBody.toEditorHtml("<p><b>x</b></p>"))
        assertEquals("", RichTextBody.toEditorHtml(null))
    }

    @Test
    fun richText_editorCompatibility() {
        assertTrue(RichTextBody.isEditorCompatible("<h2>T</h2><p><b>a</b> <a href=\"https://x.cz\">l</a></p><ul><li>1</li></ul>"))
        assertFalse(RichTextBody.isEditorCompatible("<p><img src=\"a.png\"></p>"))
        assertFalse(RichTextBody.isEditorCompatible("<table><tr><td>1</td></tr></table>"))
    }

    @Test
    fun richText_emptyEditorIsStoredAsEmpty() {
        assertEquals("", RichTextBody.normalizeForStorage("<p></p>", "  "))
        assertEquals("<p>a</p>", RichTextBody.normalizeForStorage(" <p>a</p> ", "a"))
    }

    @Test
    fun richText_normalizeUrl() {
        assertEquals("https://tkolymp.cz", RichTextBody.normalizeUrl(" tkolymp.cz "))
        assertEquals("http://a.cz/x", RichTextBody.normalizeUrl("http://a.cz/x"))
        assertEquals("mailto:info@tkolymp.cz", RichTextBody.normalizeUrl("info@tkolymp.cz"))
        assertEquals(null, RichTextBody.normalizeUrl("javascript:alert(1)"))
        assertEquals(null, RichTextBody.normalizeUrl("not a url"))
        assertEquals(null, RichTextBody.normalizeUrl(""))
    }
}

package com.tkolymp.shared.user

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.storage.UserStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

class UserService(private val client: com.tkolymp.shared.network.IGraphQlClient = ServiceLocator.graphQlClient,
                  private val storage: UserStorage = ServiceLocator.userStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastApiError: String? = null

    private fun checkAndSetErrors(resp: JsonElement) {
        try {
            val errors = resp.jsonObject["errors"]
            lastApiError = errors?.toString()
        } catch (_: Throwable) {
            lastApiError = null
        }
    }

    suspend fun initializeAfterLogin(versionId: String = "") {
        mutex.withLock {
            fetchAndStorePersonId()
            fetchAndStoreActiveCouples()
            if (versionId.isNotEmpty()) fetchAndStoreCurrentUser(versionId)
        }
    }

    suspend fun fetchAndStorePersonId(): String? {
        val query = "query MyQuery { userProxiesList { person { id } } }"
        val resp = try { client.post(query, null) } catch (ex: Throwable) { lastApiError = ex.message; return null }
        checkAndSetErrors(resp)
        val personId = try {
            val arr = resp.jsonObject["data"]?.jsonObject?.get("userProxiesList")?.jsonArray
            arr?.firstOrNull()?.jsonObject?.get("person")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        } catch (_: Throwable) { null }

        if (personId != null) storage.savePersonId(personId)
        return personId
    }

    suspend fun fetchAndStoreCurrentUser(versionId: String): JsonObject? {
        val query = "query MyQuery(\$versionId: String!) { getCurrentUser(versionId: \$versionId) { uEmail uJmeno uLogin createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }"
        val variables = buildJsonObject { put("versionId", JsonPrimitive(versionId)) }
        val resp = try { client.post(query, variables) } catch (ex: Throwable) { lastApiError = ex.message; return null }
        checkAndSetErrors(resp)
        val data = resp.jsonObject["data"]?.jsonObject?.get("getCurrentUser")?.jsonObject
        if (data != null) storage.saveCurrentUserJson(data.toString())
        return data
    }

    suspend fun fetchAndStoreActiveCouples(): List<String> {
        val query = "query kveri { users { nodes { userProxiesList { person { activeCouplesList { id man { firstName lastName } woman { firstName lastName } } cohortMembershipsList { cohort { id colorRgb name } } } } } } }"
        val resp = try { client.post(query, null) } catch (ex: Throwable) { lastApiError = ex.message; return emptyList() }
        checkAndSetErrors(resp)
        val ids = mutableListOf<String>()
        try {
            val users = resp.jsonObject["data"]?.jsonObject?.get("users")?.jsonObject?.get("nodes")?.jsonArray
            users?.forEach { node ->
                val proxies = node.jsonObject["userProxiesList"]?.jsonArray
                proxies?.forEach { proxy ->
                    val active = proxy.jsonObject["person"]?.jsonObject?.get("activeCouplesList")?.jsonArray
                    active?.forEach { c ->
                        c.jsonObject["id"]?.jsonPrimitive?.contentOrNull?.let { ids.add(it) }
                    }
                }
            }
        } catch (_: Throwable) {}

        storage.saveCoupleIds(ids)
        return ids
    }

    suspend fun fetchAndStorePersonDetails(personId: String): JsonObject? {
        // Try sending id as numeric BigInt when possible (server expects BigInt)
        val idLong = personId.toLongOrNull()
        val baseSelection = "person(id: \$id) { bio birthDate lastName firstName phone prefixTitle wdsfId cstsId gender isTrainer address { city conscriptionNumber district orientationNumber postalCode region street } }"

        var query: String
        var variables: JsonObject
        var resp: JsonElement

        if (idLong != null) {
            query = "query MyQuery(\$id: BigInt!) { $baseSelection }"
            variables = buildJsonObject { put("id", JsonPrimitive(idLong)) }
            resp = try { client.post(query, variables) } catch (ex: Throwable) { lastApiError = ex.message; return null }
            checkAndSetErrors(resp)
        } else {
            // fallback to string variable if id is not numeric
            query = "query MyQuery(\$id: String!) { $baseSelection }"
            variables = buildJsonObject { put("id", JsonPrimitive(personId)) }
            resp = try { client.post(query, variables) } catch (ex: Throwable) { lastApiError = ex.message; return null }
            checkAndSetErrors(resp)
        }

        val data = resp.jsonObject["data"]?.jsonObject?.get("person")?.jsonObject
        if (data != null) storage.savePersonDetailsJson(data.toString())
        return data
    }

    suspend fun getCachedPersonId(): String? = storage.getPersonId()
    suspend fun getCachedCoupleIds(): List<String> = storage.getCoupleIds()
    suspend fun getCachedCurrentUserJson(): String? = storage.getCurrentUserJson()
    suspend fun getCachedPersonDetailsJson(): String? = storage.getPersonDetailsJson()

    suspend fun getLastApiError(): String? = lastApiError

    suspend fun clear() { storage.clear() }
}

package com.tkolymp.shared.user

import com.tkolymp.shared.Logger
import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.people.ActiveCouple
import com.tkolymp.shared.people.AddressDetails
import com.tkolymp.shared.people.Cohort
import com.tkolymp.shared.people.CohortMembership
import com.tkolymp.shared.people.CoupleMember
import com.tkolymp.shared.people.PersonDetails
import com.tkolymp.shared.storage.UserStorage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*

data class CurrentUser(
    val id: String?,
    val uEmail: String?,
    val uJmeno: String?,
    val uLogin: String?,
    val uPrijmeni: String?
)

class UserService(private val client: com.tkolymp.shared.network.IGraphQlClient = ServiceLocator.graphQlClient,
                  private val storage: UserStorage = ServiceLocator.userStorage) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var lastApiError: String? = null

    private fun checkAndSetErrors(resp: JsonElement) {
        try {
            val errors = resp.jsonObject["errors"]
            lastApiError = errors?.toString()
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
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
        val resp = try { client.post(query, null) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return null }
        checkAndSetErrors(resp)
        val personId = try {
            val arr = resp.jsonObject["data"]?.jsonObject?.get("userProxiesList")?.jsonArray
            arr?.firstOrNull()?.jsonObject?.get("person")?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
        } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

        if (personId != null) storage.savePersonId(personId)
        return personId
    }

    suspend fun fetchAndStoreCurrentUser(versionId: String): JsonObject? {
        val query = "query MyQuery(\$versionId: String!) { getCurrentUser(versionId: \$versionId) { uEmail uJmeno uLogin createdAt id lastActiveAt lastLogin tenantId uPrijmeni updatedAt } }"
        val variables = buildJsonObject { put("versionId", JsonPrimitive(versionId)) }
        val resp = try { client.post(query, variables) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return null }
        checkAndSetErrors(resp)
        val data = resp.jsonObject["data"]?.jsonObject?.get("getCurrentUser")?.jsonObject
        if (data != null) storage.saveCurrentUserJson(data.toString())
        return data
    }

    suspend fun fetchAndStoreActiveCouples(): List<String> {
        val query = "query kveri { users { nodes { userProxiesList { person { activeCouplesList { id man { firstName lastName } woman { firstName lastName } } cohortMembershipsList { cohort { id colorRgb name } } } } } } }"
        val resp = try { client.post(query, null) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return emptyList() }
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
        } catch (e: CancellationException) { throw e } catch (_: Exception) {}

        storage.saveCoupleIds(ids)
        return ids
    }

    suspend fun fetchAndStorePersonDetails(personId: String): JsonObject? {
        // Try sending id as numeric BigInt when possible (server expects BigInt)
        val idLong = personId.toLongOrNull()
        val baseSelection = "person(id: \$id) { bio birthDate lastName firstName email phone prefixTitle wdsfId cstsId gender isTrainer nationality nationalIdNumber address { city conscriptionNumber district orientationNumber postalCode region street } activeCouplesList { id man { firstName lastName } woman { firstName lastName } } cohortMembershipsList { cohort { id colorRgb name isVisible } since until } }"

        var query: String
        var variables: JsonObject
        var resp: JsonElement

        if (idLong != null) {
            query = "query MyQuery(\$id: BigInt!) { $baseSelection }"
            variables = buildJsonObject { put("id", JsonPrimitive(idLong)) }
            resp = try { client.post(query, variables) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return null }
            checkAndSetErrors(resp)
        } else {
            // fallback to string variable if id is not numeric
            query = "query MyQuery(\$id: String!) { $baseSelection }"
            variables = buildJsonObject { put("id", JsonPrimitive(personId)) }
            resp = try { client.post(query, variables) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return null }
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

    suspend fun getCachedPersonDetails(): PersonDetails? {
        val json = storage.getPersonDetailsJson() ?: return null
        return parseStoredPersonDetails(json)
    }

    suspend fun getCachedCurrentUser(): CurrentUser? {
        val json = storage.getCurrentUserJson() ?: return null
        return parseStoredCurrentUser(json)
    }

    private fun parseStoredPersonDetails(jsonStr: String): PersonDetails? = try {
        val p = Json.parseToJsonElement(jsonStr).jsonObject
        val id = p["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val couplesArr = (p["activeCouplesList"] as? JsonArray)?.mapNotNull { cEl ->
            val cObj = cEl as? JsonObject ?: return@mapNotNull null
            val cid = cObj["id"]?.jsonPrimitive?.contentOrNull
            val manObj = cObj["man"] as? JsonObject
            val womanObj = cObj["woman"] as? JsonObject
            ActiveCouple(cid, CoupleMember(manObj?.get("firstName")?.jsonPrimitive?.contentOrNull, manObj?.get("lastName")?.jsonPrimitive?.contentOrNull), CoupleMember(womanObj?.get("firstName")?.jsonPrimitive?.contentOrNull, womanObj?.get("lastName")?.jsonPrimitive?.contentOrNull))
        } ?: emptyList()
        val memberships = (p["cohortMembershipsList"] as? JsonArray)?.mapNotNull { mEl ->
            val mObj = mEl as? JsonObject ?: return@mapNotNull null
            val cohortObj = mObj["cohort"] as? JsonObject
            CohortMembership(Cohort(cohortObj?.get("id")?.jsonPrimitive?.contentOrNull, cohortObj?.get("name")?.jsonPrimitive?.contentOrNull, cohortObj?.get("colorRgb")?.jsonPrimitive?.contentOrNull, cohortObj?.get("isVisible")?.jsonPrimitive?.contentOrNull?.let { it == "true" }), mObj["since"]?.jsonPrimitive?.contentOrNull, mObj["until"]?.jsonPrimitive?.contentOrNull)
        } ?: emptyList()
        val addrObj = p["address"] as? JsonObject
        val address = addrObj?.let { a ->
            AddressDetails(street = a["street"]?.jsonPrimitive?.contentOrNull, city = a["city"]?.jsonPrimitive?.contentOrNull, postalCode = a["postalCode"]?.jsonPrimitive?.contentOrNull, region = a["region"]?.jsonPrimitive?.contentOrNull, district = a["district"]?.jsonPrimitive?.contentOrNull, conscriptionNumber = a["conscriptionNumber"]?.jsonPrimitive?.contentOrNull, orientationNumber = a["orientationNumber"]?.jsonPrimitive?.contentOrNull)
        }
        PersonDetails(id = id, firstName = p["firstName"]?.jsonPrimitive?.contentOrNull, lastName = p["lastName"]?.jsonPrimitive?.contentOrNull, prefixTitle = p["prefixTitle"]?.jsonPrimitive?.contentOrNull, suffixTitle = p["suffixTitle"]?.jsonPrimitive?.contentOrNull, birthDate = p["birthDate"]?.jsonPrimitive?.contentOrNull, bio = p["bio"]?.jsonPrimitive?.contentOrNull, cstsId = p["cstsId"]?.jsonPrimitive?.contentOrNull, email = p["email"]?.jsonPrimitive?.contentOrNull, gender = p["gender"]?.jsonPrimitive?.contentOrNull, isTrainer = p["isTrainer"]?.jsonPrimitive?.contentOrNull?.let { it == "true" }, phone = p["phone"]?.jsonPrimitive?.contentOrNull, wdsfId = p["wdsfId"]?.jsonPrimitive?.contentOrNull, activeCouplesList = couplesArr, cohortMembershipsList = memberships, rawResponse = Json.parseToJsonElement(jsonStr), address = address, nationality = p["nationality"]?.jsonPrimitive?.contentOrNull, nationalIdNumber = p["nationalIdNumber"]?.jsonPrimitive?.contentOrNull)
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    private fun parseStoredCurrentUser(jsonStr: String): CurrentUser? = try {
        val u = Json.parseToJsonElement(jsonStr).jsonObject
        CurrentUser(id = u["id"]?.jsonPrimitive?.contentOrNull, uEmail = u["uEmail"]?.jsonPrimitive?.contentOrNull, uJmeno = u["uJmeno"]?.jsonPrimitive?.contentOrNull, uLogin = u["uLogin"]?.jsonPrimitive?.contentOrNull, uPrijmeni = u["uPrijmeni"]?.jsonPrimitive?.contentOrNull)
    } catch (e: CancellationException) { throw e } catch (_: Exception) { null }

    suspend fun getLastApiError(): String? = lastApiError

    suspend fun clear() { storage.clear() }

    suspend fun changePassword(newPass: String): Boolean {
        // Insert the password directly into the mutation (backend expects a simple string).
        val esc = newPass.replace("\\", "\\\\").replace("\"", "\\\"")
        val query = "mutation { changePassword(input: {newPass: \"$esc\"}) { clientMutationId } }"
        val resp = try { client.post(query, null) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return false }
        checkAndSetErrors(resp)
        val errors = try { resp.jsonObject["errors"] } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (errors != null) return false
        val data = try { resp.jsonObject["data"]?.jsonObject?.get("changePassword") } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        return data != null
    }

    suspend fun updatePerson(personId: String, request: PersonUpdateRequest): Boolean {

        // build patch object with only provided fields
        val patch = buildJsonObject {
            fun addStringField(name: String, value: String?) {
                // send empty string when value is null or blank (avoid JsonNull)
                if (value == null) {
                    put(name, JsonPrimitive(""))
                    return
                }
                if (value.isBlank()) put(name, JsonPrimitive(""))
                else put(name, JsonPrimitive(value))
            }

            addStringField("bio", request.bio)
            addStringField("cstsId", request.cstsId)
            addStringField("email", request.email)
            addStringField("firstName", request.firstName)
            addStringField("lastName", request.lastName)
            addStringField("nationalIdNumber", request.nationalIdNumber)
            addStringField("nationality", request.nationality)
            addStringField("phone", request.phone)
            addStringField("wdsfId", request.wdsfId)
            addStringField("prefixTitle", request.prefixTitle)
            addStringField("suffixTitle", request.suffixTitle)
            addStringField("gender", request.gender)

            if (request.birthDateSet) {
                if (request.birthDate.isNullOrBlank()) put("birthDate", JsonNull)
                else put("birthDate", JsonPrimitive(request.birthDate))
            }

            request.address?.let { a ->
                val addr = buildJsonObject {
                    fun addAddrField(n: String, v: String?) {
                        // send empty string when address subfield is null or blank
                        if (v == null) { put(n, JsonPrimitive("")); return }
                        if (v.isBlank()) put(n, JsonPrimitive("")) else put(n, JsonPrimitive(v))
                    }
                    addAddrField("street", a.street)
                    addAddrField("city", a.city)
                    addAddrField("postalCode", a.postalCode)
                    addAddrField("region", a.region)
                    addAddrField("district", a.district)
                    addAddrField("conscriptionNumber", a.conscriptionNumber)
                    addAddrField("orientationNumber", a.orientationNumber)
                }
                if (addr.isNotEmpty()) put("address", addr)
            }
        }

        val idLong = personId.toLongOrNull()
        val variables = if (idLong != null) {
            buildJsonObject { put("id", JsonPrimitive(idLong)); put("patch", patch) }
        } else {
            buildJsonObject { put("id", JsonPrimitive(personId)); put("patch", patch) }
        }

        val query = if (idLong != null)
            "mutation UpdatePerson(\$id: BigInt!, \$patch: PersonPatch!) { updatePerson(input: {id: \$id, patch: \$patch}) { clientMutationId } }"
        else
            "mutation UpdatePerson(\$id: String!, \$patch: PersonPatch!) { updatePerson(input: {id: \$id, patch: \$patch}) { clientMutationId } }"

        Logger.d("UserService", "UpdatePerson query: $query")
        Logger.d("UserService", "UpdatePerson variables: $variables")

        val resp = try { client.post(query, variables) } catch (e: CancellationException) { throw e } catch (ex: Exception) { lastApiError = ex.message; return false }
        checkAndSetErrors(resp)
        val errors = try { resp.jsonObject["errors"] } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (errors != null) return false
        val data = try { resp.jsonObject["data"]?.jsonObject?.get("updatePerson") } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
        if (data != null) {
            // refresh cached person details so UI reads the updated data
            try {
                fetchAndStorePersonDetails(personId)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        return data != null
    }
}

data class PersonUpdateRequest(
    val bio: String? = null,
    val cstsId: String? = null,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val nationalIdNumber: String? = null,
    val nationality: String? = null,
    val phone: String? = null,
    val wdsfId: String? = null,
    val prefixTitle: String? = null,
    val suffixTitle: String? = null,
    val gender: String? = null,
    val birthDateSet: Boolean = false,
    val birthDate: String? = null,
    val address: AddressUpdate? = null
)

data class AddressUpdate(
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val region: String? = null,
    val district: String? = null,
    val conscriptionNumber: String? = null,
    val orientationNumber: String? = null
)

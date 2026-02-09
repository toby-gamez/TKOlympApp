package com.tkolymp.shared.club

import com.tkolymp.shared.ServiceLocator
import com.tkolymp.shared.network.IGraphQlClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class Location(val name: String?)

data class Money(val amount: Double?, val currency: String?)

data class TrainerPerson(val id: String?, val firstName: String?, val lastName: String?, val prefixTitle: String?, val suffixTitle: String?)

data class Trainer(
    val person: TrainerPerson?,
    val guestPrice45Min: Money?,
    val guestPayout45Min: Money?,
    val isVisible: Boolean?
)

data class Cohort(val colorRgb: String?, val name: String?, val description: String?, val location: String?)

data class ClubData(val locations: List<Location>, val trainers: List<Trainer>, val cohorts: List<Cohort>, val raw: JsonElement?)

class ClubService(private val client: IGraphQlClient = ServiceLocator.graphQlClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchClubData(): ClubData {
        val query = """
            query Query {
              tenantLocationsList { name }
              tenantTrainersList {
                person { id firstName lastName prefixTitle suffixTitle }
                guestPrice45Min { amount currency }
                guestPayout45Min { amount currency }
                isVisible
              }
              getCurrentTenant {
                id
                cohortsList(condition: { isVisible: true }, orderBy: [NAME_ASC]) {
                  colorRgb
                  name
                  description
                  location
                }
              }
            }
        """.trimIndent()

        val el: JsonElement = try { client.post(query, null) } catch (ex: Exception) {
            return ClubData(emptyList(), emptyList(), emptyList(), null)
        }

        val root = (el as? JsonObject)?.get("data") as? JsonObject
        if (root == null) return ClubData(emptyList(), emptyList(), emptyList(), el)

        val locationsArr = (root["tenantLocationsList"] as? JsonArray) ?: JsonArray(emptyList())
        val trainersArr = (root["tenantTrainersList"] as? JsonArray) ?: JsonArray(emptyList())
        val tenantObj = root["getCurrentTenant"] as? JsonObject
        val cohortsArr = (tenantObj?.get("cohortsList") as? JsonArray) ?: JsonArray(emptyList())

        val locations = locationsArr.mapNotNull { it as? JsonObject }.map { obj ->
            Location(obj["name"]?.jsonPrimitive?.contentOrNull)
        }

        val trainers = trainersArr.mapNotNull { it as? JsonObject }.map { obj ->
            val personObj = obj["person"] as? JsonObject
            val person = TrainerPerson(
                id = personObj?.get("id")?.jsonPrimitive?.contentOrNull,
                firstName = personObj?.get("firstName")?.jsonPrimitive?.contentOrNull,
                lastName = personObj?.get("lastName")?.jsonPrimitive?.contentOrNull,
                prefixTitle = personObj?.get("prefixTitle")?.jsonPrimitive?.contentOrNull,
                suffixTitle = personObj?.get("suffixTitle")?.jsonPrimitive?.contentOrNull
            )

            fun parseMoney(key: String): Money? {
                val mObj = obj[key] as? JsonObject ?: return null
                val amt = mObj["amount"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
                val cur = mObj["currency"]?.jsonPrimitive?.contentOrNull
                return Money(amt, cur)
            }

            val guestPrice = parseMoney("guestPrice45Min")
            val guestPayout = parseMoney("guestPayout45Min")
            val visible = obj["isVisible"]?.jsonPrimitive?.contentOrNull?.let { it == "true" }

            Trainer(person, guestPrice, guestPayout, visible)
        }

        val cohorts = cohortsArr.mapNotNull { it as? JsonObject }.map { obj ->
            Cohort(
                colorRgb = obj["colorRgb"]?.jsonPrimitive?.contentOrNull,
                name = obj["name"]?.jsonPrimitive?.contentOrNull,
                description = obj["description"]?.jsonPrimitive?.contentOrNull,
                location = obj["location"]?.jsonPrimitive?.contentOrNull
            )
        }

        return ClubData(locations, trainers, cohorts, el)
    }
}

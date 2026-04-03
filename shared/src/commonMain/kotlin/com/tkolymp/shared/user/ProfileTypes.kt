package com.tkolymp.shared.user

import com.tkolymp.shared.people.PersonDetails
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

data class CohortDisplay(val name: String, val colorRgb: String?, val since: String?, val until: String?)

data class ProfileDerivedState(
    val titleText: String?,
    val bioText: String?,
    val addrText: String?,
    val emailText: String?,
    val activeCoupleNames: List<String>,
    val cohortItems: List<CohortDisplay>,
    val personFields: List<Pair<String, String>>,
    val currentUserFields: List<Pair<String, String>>,
    val addressFields: List<Pair<String, String>>,
    val personalList: List<Pair<String, String>>,
    val contactList: List<Pair<String, String>>,
    val externalList: List<Pair<String, String>>,
    val otherList: List<Pair<String, String>>
)

fun fmtProfileDate(s: String?): String? {
    if (s.isNullOrBlank()) return null
    try {
        val ld = LocalDate.parse(s)
        return "${ld.dayOfMonth.toString().padStart(2,'0')}.${ld.monthNumber.toString().padStart(2,'0')}.${ld.year}"
    } catch (_: Exception) {}
    try {
        val inst = Instant.parse(s)
        val ld = inst.toLocalDateTime(TimeZone.UTC).date
        return "${ld.dayOfMonth.toString().padStart(2,'0')}.${ld.monthNumber.toString().padStart(2,'0')}.${ld.year}"
    } catch (_: Exception) {}
    val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").find(s)
    if (m != null) return "${m.groupValues[3]}.${m.groupValues[2]}.${m.groupValues[1]}"
    return s
}

fun buildPersonFieldList(person: PersonDetails?): List<Pair<String, String>> {
    person ?: return emptyList()
    return buildList {
        person.birthDate?.let { add("birthDate" to it) }
        person.gender?.let { add("gender" to it) }
        person.nationality?.let { add("nationality" to it) }
        person.nationalIdNumber?.let { add("nationalIdNumber" to it) }
        person.phone?.let { add("phone" to it) }
        person.wdsfId?.let { add("wdsfId" to it) }
        person.cstsId?.let { add("cstsId" to it) }
        person.isTrainer?.let { add("isTrainer" to it.toString()) }
    }
}

fun buildCurrentUserFieldList(currentUser: CurrentUser?): List<Pair<String, String>> {
    currentUser ?: return emptyList()
    return buildList {
        currentUser.uLogin?.let { add("username" to it) }
        currentUser.uEmail?.let { add("email" to it) }
    }
}

fun deriveProfileState(person: PersonDetails?, currentUser: CurrentUser?, coupleIds: List<String>): ProfileDerivedState {
    val activeCoupleNames = person?.activeCouplesList?.mapNotNull { couple ->
        val man = listOfNotNull(couple.man?.firstName, couple.man?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
        val woman = listOfNotNull(couple.woman?.firstName, couple.woman?.lastName).joinToString(" ").takeIf { it.isNotBlank() }
        listOfNotNull(man, woman).joinToString(" - ").takeIf { it.isNotBlank() }
    }?.takeIf { it.isNotEmpty() } ?: coupleIds.map { "#$it" }

    val cohortItems = person?.cohortMembershipsList?.mapNotNull { m ->
        val cohort = m.cohort ?: return@mapNotNull null
        val name = cohort.name ?: return@mapNotNull null
        CohortDisplay(name, cohort.colorRgb, fmtProfileDate(m.since), fmtProfileDate(m.until))
    } ?: emptyList()

    val titleText = listOfNotNull(person?.prefixTitle, person?.firstName, person?.lastName)
        .joinToString(" ").takeIf { it.isNotBlank() }
    val bioText = person?.bio?.takeIf { it.isNotBlank() }
    val emailText = person?.email?.takeIf { it.isNotBlank() } ?: currentUser?.uEmail?.takeIf { it.isNotBlank() }

    val addressFields = person?.address?.let { a ->
        buildList {
            a.street?.let { add("street" to it) }
            a.city?.let { add("city" to it) }
            a.postalCode?.let { add("postalCode" to it) }
            a.region?.let { add("region" to it) }
            a.district?.let { add("district" to it) }
            a.conscriptionNumber?.let { add("conscriptionNumber" to it) }
            a.orientationNumber?.let { add("orientationNumber" to it) }
        }
    } ?: emptyList()

    val addrText = person?.address?.let { a ->
        listOfNotNull(a.street, a.city, a.postalCode).joinToString(", ").takeIf { it.isNotBlank() }
    }

    val personFields = buildPersonFieldList(person)
    val currentUserFields = buildCurrentUserFieldList(currentUser)

    // Merge fields, excluding keys shown elsewhere
    val excluded = setOf("prefixTitle", "firstName", "lastName", "bio", "address",
        "activeCouplesList", "cohortMembershipsList")
    val mergedFields = linkedMapOf<String, String>()
    personFields.forEach { (k: String, v: String) -> if (k !in excluded && v.isNotBlank()) mergedFields[k] = v }
    currentUserFields.forEach { (k: String, v: String) -> if (k !in excluded && !mergedFields.containsKey(k) && v.isNotBlank()) mergedFields[k] = v }

    val personalKeys = setOf("birthDate", "gender", "nationality", "maritalStatus", "placeOfBirth")
    val contactKeys = setOf("email", "mobilePhone", "phone", "workPhone")
    val externalKeys = setOf("personalId", "idNumber", "passportNumber", "externalId", "ico", "dic", "nationalIdNumber", "wdsfId", "cstsId")
    val personalList = mutableListOf<Pair<String, String>>()
    val contactList = mutableListOf<Pair<String, String>>()
    val externalList = mutableListOf<Pair<String, String>>()
    val otherList = mutableListOf<Pair<String, String>>()
    mergedFields.forEach { (k: String, v: String) ->
        when {
            k in personalKeys -> personalList.add(k to v)
            k in contactKeys -> contactList.add(k to v)
            k in externalKeys -> externalList.add(k to v)
            else -> otherList.add(k to v)
        }
    }

    return ProfileDerivedState(
        titleText = titleText,
        bioText = bioText,
        addrText = addrText,
        emailText = emailText,
        activeCoupleNames = activeCoupleNames,
        cohortItems = cohortItems,
        personFields = personFields,
        currentUserFields = currentUserFields,
        addressFields = addressFields,
        personalList = personalList,
        contactList = contactList,
        externalList = externalList,
        otherList = otherList
    )
}

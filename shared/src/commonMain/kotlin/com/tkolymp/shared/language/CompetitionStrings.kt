package com.tkolymp.shared.language

data class CompetitionStrings(
    val competitions: String = "Soutěže",
    val upcoming: String = "Nadcházející",
    val results: String = "Výsledky",
    val nearestCompetition: String = "Nejbližší soutěž",
    val noUpcoming: String = "Žádné nadcházející soutěže",
    val noUpcomingMine: String = "Žádné tvoje nadcházející soutěže",
    val noResults: String = "Žádné výsledky",
    val checkIn: String = "Čas prezence",
    val participants: String = "Účastníci",
    val ranking: String = "Pořadí",
    val pointGain: String = "Body",
    val pointsSuffix: String = "b",
    val isFinal: String = "Finále",
    val category: String = "Kategorie",
    val ageGroupAdult: String = "Dospělí",
    val ageGroupJunior: String = "Junior",
    val ageGroupJuvenile: String = "Děti",
    val ageGroupSenior: String = "Senior",
    val ageGroupYouth: String = "Mládež",
    val openInBrowser: String = "Otevřít propozice v prohlížeči",
    val finals: String = "Finálí",
    val cstsProgress: String = "Výsledky ČSTS",
    val noCstsProgress: String = "Žádné výsledky ČSTS",
    val wdsfProfile: String = "WDSF profil",
    val wdsfProgress: String = "Výsledky WDSF",
    val wdsfRankings: String = "Žebříčky",
    val wdsfLicenses: String = "Licence",
    val recentCompetitions: String = "Poslední soutěže",
    val medicalCheckup: String = "Lékařská prohlídka",
    val validUntil: String = "platí do",
    val ageGroupLabel: String = "Věková skupina",
    val foreignFinalsLabel: String = "zahr. finále",
    val asOf: String = "k",
    val showAll: String = "Zobrazit vše ({n})",
    val showLess: String = "Zobrazit méně",
    val terms: Map<String, String> = DEFAULT_TERMS,
    val loadingCompetitions: String = "Načítám soutěže…",
    val loadingSlowNote: String = "Omlouváme se, pomalá architektura",
) {
    /** Translates a server enum value (license kind, discipline, status, competition type, age group); unknown values are prettified. */
    fun term(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val key = raw.trim().uppercase().replace(' ', '_')
        terms[key]?.let { return it }
        return when (key) {
            "ADULT" -> ageGroupAdult
            "YOUTH" -> ageGroupYouth
            "JUNIOR" -> ageGroupJunior
            "JUVENILE" -> ageGroupJuvenile
            "SENIOR" -> ageGroupSenior
            else -> raw.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
        }
    }

    fun formatType(raw: String): String = raw
        .replace("DanceForAll", "TPV")
        .removePrefix("DanceSport ")
        .replace("_", " ")
        .replace("Standard", "STT")
        .replace("Latin", "LAT")
        .replace("STT LAT", "STT+LAT")
        .replace("Juvenile", ageGroupJuvenile)
        .replace("Adult", ageGroupAdult)
        .replace("Youth", ageGroupYouth)
        .replace("Junior", ageGroupJunior)
        .replace("Senior", ageGroupSenior)
        .replace(" S ", " M ")
        .replace("Under 21", "U21")
}

private val DEFAULT_TERMS: Map<String, String> = mapOf(
    "ATHLETE" to "Sportovec",
    "TRAINER" to "Trenér",
    "ADJUDICATOR" to "Porotce",
    "CHAIRPERSON" to "Předseda poroty",
    "INVIGILATOR" to "Dozor",
    "SCRUTINEER" to "Sčitatel",
    "LEAD_SCRUTINEER" to "Hlavní sčitatel",
    "EXAMINER" to "Zkoušející",
    "DJ" to "DJ",
    "HEAD_JUDGE" to "Hlavní rozhodčí",
    "OFFICIAL" to "Funkcionář",
    "STANDARD" to "Standardní tance",
    "LATIN" to "Latinskoamerické tance",
    "TEN_DANCE" to "Desetitanec",
    "SMOOTH" to "Smooth",
    "SHOW_DANCE_STANDARD" to "Show dance standard",
    "SHOW_DANCE_LATIN" to "Show dance latina",
    "FORMATION_STANDARD" to "Formace standard",
    "FORMATION_LATIN" to "Formace latina",
    "GENERAL" to "Obecné",
    "ACTIVE" to "Aktivní",
    "EXPIRED" to "Vypršela",
    "REVOKED" to "Odebrána",
    "RESTING" to "Nečinná",
    "RETIRED" to "Ukončena",
    "ASPIRING" to "Uchazeč",
    "SUSPENDED" to "Pozastavena",
    "CUP" to "Pohár",
    "RANKING" to "Žebříček",
    "LEAGUE" to "Liga",
    "CHAMPIONSHIP" to "Mistrovství",
    "TOP_LEVEL" to "Nejvyšší úroveň",
    "SUPER_LEAGUE" to "Superliga",
    "G_CUP" to "G Cup",
)

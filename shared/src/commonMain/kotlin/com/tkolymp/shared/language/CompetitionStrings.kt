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
) {
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

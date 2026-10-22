package com.tkolymp.shared.language

/** "Taneční organizace" - federation competition data (CSTS, WDSF, SZTS). Defaults are Czech. */
data class DanceOrgStrings(
    val title: String = "Taneční organizace",
    val dataTitle: String = "Soutěžní data",
    val past: String = "Minulé",
    val upcoming: String = "Nadcházející",
    val offlineMessage: String = "Offline – zobrazují se dříve stažená data.",
    val typeTopLevel: String = "Vrcholová",
    val typeCup: String = "Pohárová",
    val typeRanking: String = "Postupová",
    val typeLeague: String = "Taneční liga",
    val typeChampionship: String = "Mistrovství",
    val typeSuperLeague: String = "Super taneční liga",
    val allYear: String = "Celý rok",
    val eventsSuffix: String = "událostí",
    val map: String = "Mapa",
    val list: String = "Seznam",
    val searchHint: String = "Název nebo místo…",
    val withResults: String = "S výsledky",
    val date: String = "Datum",
    val event: String = "Soutěž",
    val place: String = "Místo",
    val country: String = "Země",
    val competitionsCount: String = "Soutěží",
    val noEvents: String = "Žádné události",
    val noCompetitions: String = "Zatím žádné soutěže",
    val noResultsYet: String = "Výsledky zatím nejsou",
    val competitionsHeader: String = "Soutěže",
    val organizer: String = "Pořadatel",
    val contact: String = "Kontakt",
    val address: String = "Adresa",
    val website: String = "Web",
    val statusPlanned: String = "Plánováno",
    val statusInProgress: String = "Probíhá",
    val statusCompleted: String = "Dokončeno",
    val statusPostponed: String = "Odloženo",
    val statusCancelled: String = "Zrušeno",
    val mapCzechia: String = "Česko",
    val mapSlovakia: String = "Slovensko",
    val mapWorld: String = "Svět",
    val zoomIn: String = "Přiblížit",
    val zoomOut: String = "Oddálit",
    val resetMap: String = "Obnovit pohled",
    val noMapEvents: String = "Žádné události se souřadnicemi",
    val noDataYet: String = "Data zatím nejsou k dispozici",
    val months: String = "led,úno,bře,dub,kvě,čvn,čvc,srp,zář,říj,lis,pro",
) {
    /** Short month name for [month] in 1..12. */
    fun monthShort(month: Int): String = months.split(",").getOrElse(month - 1) { "" }

    /** Label for a CompetitionType name; G Cup has no translation and keeps its name. */
    fun competitionType(raw: String): String = when (raw) {
        "CUP" -> typeCup
        "RANKING" -> typeRanking
        "LEAGUE" -> typeLeague
        "CHAMPIONSHIP" -> typeChampionship
        "TOP_LEVEL" -> typeTopLevel
        "SUPER_LEAGUE" -> typeSuperLeague
        "G_CUP" -> "G Cup"
        else -> ""
    }

    fun status(raw: String): String = when (raw) {
        "PLANNED" -> statusPlanned
        "IN_PROGRESS" -> statusInProgress
        "COMPLETED" -> statusCompleted
        "POSTPONED" -> statusPostponed
        "CANCELLED" -> statusCancelled
        else -> ""
    }
}

package com.tkolymp.shared.language

data class FreeLessonsStrings(
    val title: String = "Volné lekce",
    val infoTitle: String = "Jak se hodnotí",
    val scoringHeader: String = "Základní pravidla:",
    val scoringBase: String = "Základ: +100 bodů",
    val scoringDay: String = "Každý den od dneška: −5 bodů (až −70 za 14 dní)",
    val scoringNoTraining: String = "Pokud nemáš ten den žádný trénink: +30 bodů",
    val scoringSameLocation: String = "Stejná lokace jako tvůj trénink ten den: +20 bodů",
    val scoringNote: String = "Poznámka: lekce, které se kryjí s tvým tréninkem, nejsou v ‚Nejlepší nálezy‘.",
    val scoringOk: String = "Rozumím",

    val bestLabel: String = "Nejlepší nálezy",
    val otherLabel: String = "Ostatní",
    val replaceCancelledLabel: String = "Nahradit za zrušené",
    val noneFound: String = "Žádné volné lekce v příštích 2 týdnech.",
    val findButton: String = "Najít volné lekce",
    val dontBother: String = "Neobtěžuj mě pro tuto lekci",

    val cancelledLabel: String = "Zrušená lekce",
    val noReplacements: String = "Žádné náhradní lekce nalezeny.",
    val showReplacements: String = "Zobrazit náhrady",
    val hideReplacements: String = "Skrýt náhrady",

    // Tips / computed phrases (use {0} for name placeholders)
    val conflictTip: String = "Kryje se s {0}",
    val trainingFallback: String = "tréninkem",
    val differentHallNoTime: String = "Je to na jiném sále, nemáš dostatečný čas na přesun",
    val differentHallHasTime: String = "Je to na jiném sále, ale máš čas na přesun",
    val sameHallLongWait: String = "Mezi {0} a tímto je dlouhé čekání",
    val alsoGoodChoice: String = "Taky dobrá volba",
    val bestChoiceFallback: String = "Toto je nejlepší volba",
)

package com.tkolymp.shared.language

data class FreeLessonsStrings(
    val title: String = "Volné lekce",
    val screenSubtitle: String = "Volné lekce v příštích 2 týdnech, seřazené podle shody s tvým rozvrhem.",
    val topPickLabel: String = "Nejlepší shoda",
    val infoTitle: String = "Jak vybíráme lekce",
    val scoringHeader: String = "Doporučujeme lekce, které:",
    val scoringBase: String = "• se konají co nejdříve",
    val scoringDay: String = "• jsou v den, kdy nemáš jiný trénink",
    val scoringNoTraining: String = "• jsou na stejném místě jako tvůj trénink",
    val scoringSameLocation: String = "",
    val scoringNote: String = "Lekce, které se kryjí s tvým tréninkem, jsou v sekci 'Další možnosti'.",
    val scoringOk: String = "Rozumím",

    val bestLabel: String = "Doporučeno pro tebe",
    val otherLabel: String = "Další možnosti",
    val replaceCancelledLabel: String = "Nahradit za zrušené",
    val noneFound: String = "Žádné volné lekce v příštích 2 týdnech.",
    val findButton: String = "Najít volné lekce",
    val dontBother: String = "Přeskočit",

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

package com.tkolymp.tkolympapp

import com.tkolymp.shared.payments.AmountOption
import com.tkolymp.shared.payments.Deposit
import com.tkolymp.shared.payments.remainderAmount
import com.tkolymp.shared.payments.MissingField
import com.tkolymp.shared.payments.ParsedPayment
import com.tkolymp.shared.payments.PaymentProfile
import com.tkolymp.shared.payments.PriceAudience
import com.tkolymp.shared.payments.PriceHint
import com.tkolymp.shared.payments.applicableOptions
import com.tkolymp.shared.payments.isPastDue
import com.tkolymp.shared.payments.preselectedAmount
import com.tkolymp.shared.payments.programsDiffer
import com.tkolymp.shared.payments.PriceProgram
import com.tkolymp.shared.payments.expiredPricesNote
import com.tkolymp.shared.payments.needsUserChoice
import com.tkolymp.shared.payments.amountOptionLabel
import com.tkolymp.shared.payments.VsSource
import com.tkolymp.shared.payments.buildSpdPayload
import com.tkolymp.shared.payments.formatCzk
import com.tkolymp.shared.payments.htmlToPlainText
import com.tkolymp.shared.payments.isValidAccountChecksum
import com.tkolymp.shared.payments.parsePayment
import com.tkolymp.shared.payments.priceHint
import com.tkolymp.shared.payments.priceHintMessage
import com.tkolymp.shared.payments.resolveVariableSymbol
import com.tkolymp.shared.payments.selectAmount
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentTextParserTest {
    private val today = LocalDate(2026, 5, 1)
    private val profile = PaymentProfile("060101/1234", "Jan", "Novák")
    private val noBirthNumber = PaymentProfile(null, "Jan", "Novák")

    private fun d(y: Int, m: Int, day: Int) = LocalDate(y, m, day)
    private fun prog(vararg options: AmountOption, title: String? = null) = listOf(PriceProgram(title, options.toList()))

    @Test
    fun sample1_priceWithPerPersonSuffixAndBirthNumberLabelInParentheses() {
        val text = """
            Cena: 649 Kč/osoba
            Číslo učtu: 1806875329/0800
            SS: 20263120
            VS: Rodné číslo (toto navíc musí aplikace zjistit a doplnit, máme ho, jen to najít)
        """.trimIndent()
        assertEquals(
            ParsedPayment(
                account = "1806875329/0800",
                programs = prog(AmountOption(64900)),
                ss = "20263120",
                vs = VsSource.BirthNumberFromProfile
            ),
            parsePayment(text, profile, today)
        )
    }

    @Test
    fun sample2_labelWithoutColonAndTypo() {
        val text = "Cena 450 Kč\nÚčet: 1806875329/0800\nSS: 20260711\nVS rodní číslo"
        assertEquals(
            ParsedPayment(
                account = "1806875329/0800",
                programs = prog(AmountOption(45000)),
                ss = "20260711",
                vs = VsSource.BirthNumberFromProfile
            ),
            parsePayment(text, profile, today)
        )
    }

    private val sample3 = """
        č.ú.: 1806875329/0800
        částka: 4 490 Kč
        VS: 15102026
        Poznámka: ProAm kurz JMÉNO a PŘÍJMENÍ (nutno doplnit)
    """.trimIndent()

    @Test
    fun sample3_placeholderWithoutProfileAsksForName() {
        assertEquals(
            ParsedPayment(
                account = "1806875329/0800",
                programs = prog(AmountOption(449000)),
                vs = VsSource.Literal("15102026"),
                message = "ProAm kurz",
                messageTemplate = "ProAm kurz {NAME}",
                missing = listOf(MissingField.NAME)
            ),
            parsePayment(sample3, null, today)
        )
    }

    @Test
    fun sample3_placeholderIsReplacedByProfileName() {
        val parsed = parsePayment(sample3, profile, today)
        assertEquals("ProAm kurz Jan Novák", parsed.message)
        assertTrue(parsed.missing.isEmpty())
    }

    @Test
    fun applicationClosingIsTheDeadline() {
        for (label in listOf("Uzavírka přihlášek: 15. 4. 2026", "uzavírka přihlášek 15. 4. 2026", "Cena 450 Kč, uzavírka přihlášek 15. 4. 2026")) {
            val text = "Účet: 1806875329/0800\n" + (if (label.startsWith("Cena")) label else "Cena 450 Kč\n$label")
            assertEquals(d(2026, 4, 15), parsePayment(text, profile, today).dueDate, label)
        }
    }

    @Test
    fun remainderBlockTakesItsOwnTerminAndSs() {
        val text = "Záloha 2000 Kč do 30. 6. 2024\nÚčet: 1806875329/0800\nDoplatek:\ndle uskutečněné rezervace na účet: 1806875329/0800\nVS: 123\nSS: 456\nTermín: 1. 11. 2024"
        val parsed = parsePayment(text, profile, d(2024, 5, 1))
        assertEquals(d(2024, 11, 1), parsed.deposit?.remainderUntil)
        assertEquals("456", parsed.deposit?.remainderSs)
    }

    @Test
    fun remainderBlockWithoutDepositUsesTerminAsDeadline() {
        val text = "Cena 5000 Kč\nDoplatek:\ndle uskutečněné rezervace na účet: 1806875329/0800\nVS: 123\nSS: 456\nTermín: 1. 11. 2024"
        assertEquals(d(2024, 11, 1), parsePayment(text, profile, d(2024, 5, 1)).dueDate)
    }

    @Test
    fun perLessonPricesAreNotAmounts() {
        val text = "Účet: 1806875329/0800\nCena: 3000 Kč\nTrenér 450 Kč / 45 min\nSkupinová 300 Kč/45 min"
        assertEquals(listOf(300000L), parsePayment(text, profile, today).amounts.map { it.amountMinor })
    }

    @Test
    fun noteInstructionLabelBecomesMessageWithName() {
        val text = "Účet: 1806875329/0800\nCena: 3000 Kč\ndo poznámky napište: Camp LAT 2026 + Jméno a Příjmení"
        val parsed = parsePayment(text, profile, today)
        assertEquals("Camp LAT 2026 + Jan Novák", parsed.message)
        assertEquals("Camp LAT 2026 + {NAME}", parsed.messageTemplate)
        assertTrue(parsed.missing.isEmpty())
    }

    @Test
    fun sample4_dueDateLineIsNotAPriceLevel() {
        val text = """
            3390 Kč
            Termín: do 31.7. 2026
            Platba na účet klubu: 1806875329/0800
            VS: rodné číslo
            SS: 20262408
        """.trimIndent()
        assertEquals(
            ParsedPayment(
                account = "1806875329/0800",
                programs = prog(AmountOption(339000)),
                ss = "20262408",
                vs = VsSource.BirthNumberFromProfile,
                dueDate = d(2026, 7, 31)
            ),
            parsePayment(text, profile, today)
        )
    }

    private val sample5 = """
        Cena:
        4890 Kč přihláška do 31. 5. 2026
        5190 Kč přihláška do 22. 6. 2026
        5490 Kč přihláška po 22. 6. 2026
        Platební údaje:
        SS: 20262707
        VS: rodné číslo
        Číslo účtu: [1806875329](tel:1806875329)/0800
    """.trimIndent()

    @Test
    fun sample5_threeLevelsAndMarkdownLink() {
        val tiers = listOf(
            AmountOption(489000, until = d(2026, 5, 31)),
            AmountOption(519000, until = d(2026, 6, 22)),
            AmountOption(549000, after = d(2026, 6, 22))
        )
        assertEquals(
            ParsedPayment(
                account = "1806875329/0800",
                programs = prog(*tiers.toTypedArray()),
                ss = "20262707",
                vs = VsSource.BirthNumberFromProfile
            ),
            parsePayment(sample5, profile, today)
        )
    }

    @Test
    fun sample5_levelSelectionBoundariesAreInclusive() {
        val parsed = parsePayment(sample5, profile, today)
        fun pick(y: Int, m: Int, day: Int) = selectAmount(parsed, d(y, m, day))?.amountMinor
        assertEquals(489000L, pick(2026, 5, 31))
        assertEquals(519000L, pick(2026, 6, 1))
        assertEquals(519000L, pick(2026, 6, 22))
        assertEquals(549000L, pick(2026, 6, 23))
    }

    @Test
    fun sample6_peopleChooseTheirOwnAndPaymentDatesLineIsIgnored() {
        val text = """
            Cena:
            4990 Kč přihláška do 31. 5. 2026
            5290 Kč přihláška do 22. 6. 2026
            5490 Kč přihláška po 22. 6. 2026


            Platební údaje:
            Číslo učtu: 1806875329/0800
            SS: 20263117
            VS: každý svůj (v sekci Profil)
            Termíny plateb: viz výše
        """.trimIndent()
        assertEquals(
            ParsedPayment(
                account = "1806875329/0800",
                programs = prog(
                    AmountOption(499000, until = d(2026, 5, 31)),
                    AmountOption(529000, until = d(2026, 6, 22)),
                    AmountOption(549000, after = d(2026, 6, 22))
                ),
                ss = "20263117",
                vs = VsSource.BirthNumberFromProfile
            ),
            parsePayment(text, profile, today)
        )
    }

    @Test
    fun missingBirthNumberInProfileAsksForVs() {
        val parsed = parsePayment("VS: rodné číslo\nCena 450 Kč\nÚčet: 1806875329/0800", noBirthNumber, today)
        assertEquals(VsSource.BirthNumberFromProfile, parsed.vs)
        assertEquals(listOf(MissingField.VS), parsed.missing)
        assertNull(resolveVariableSymbol(parsed.vs, noBirthNumber))
    }

    @Test
    fun birthNumberIsUsedWithoutSlashAndLimitedToTenDigits() {
        assertEquals("0601011234", resolveVariableSymbol(VsSource.BirthNumberFromProfile, profile))
        assertEquals("1234567890", resolveVariableSymbol(VsSource.BirthNumberFromProfile, PaymentProfile("12345678901234", null, null)))
    }

    @Test
    fun unrecognisedVsTextIsMissing() {
        val parsed = parsePayment("VS: dle domluvy", profile, today)
        assertEquals(VsSource.BirthNumberFromProfile, parsed.vs)
        assertFalse(MissingField.VS in parsed.missing)
    }

    @Test
    fun fieldsSeparatedByCommasOnOneLine() {
        for (text in listOf(
            "Cena: 450 Kč\nÚčet: 1806875329/0800, VS: 12345, SS: 20260711",
            "Cena: 450 Kč\nÚčet: 1806875329/0800; VS: 12345; SS: 20260711",
            "Cena: 450 Kč\nÚčet: 1806875329/0800 , variabilní symbol: 12345 , specifický symbol: 20260711"
        )) {
            val parsed = parsePayment(text, profile, today)
            assertEquals("1806875329/0800", parsed.account, text)
            assertEquals(VsSource.Literal("12345"), parsed.vs, text)
            assertEquals("20260711", parsed.ss, text)
            assertEquals(45000, selectAmount(parsed, today)?.amountMinor, text)
        }
    }

    @Test
    fun fieldsSeparatedByCommasWithoutColon() {
        val parsed = parsePayment("Cena 450 Kč\nÚčet 1806875329/0800, VS 12345, SS 20260711", profile, today)
        assertEquals("1806875329/0800", parsed.account)
        assertEquals(VsSource.Literal("12345"), parsed.vs)
        assertEquals("20260711", parsed.ss)
        assertEquals(listOf(AmountOption(45000)), parsed.amounts)
        val priced = parsePayment("Cena 450 Kč, do 31. 5., VS 12", profile, today)
        assertEquals(listOf(AmountOption(45000, until = d(2026, 5, 31))), priced.amounts)
        assertEquals(VsSource.Literal("12"), priced.vs)
    }

    @Test
    fun deadlineMakesPaymentTooLate() {
        val text = "Cena 450 Kč\nÚčet: 1806875329/0800\nTermín: 15. 4. 2026"
        assertTrue(isPastDue(parsePayment(text, profile, today), today))
        assertFalse(isPastDue(parsePayment(text, profile, today), d(2026, 4, 15)))
        assertEquals(d(2026, 4, 15), parsePayment(text, profile, today).dueDate)
        for (label in listOf("Splatnost do 15. 4. 2026", "Platba do 15. 4. 2026", "Zaplatit do 15.4.2026", "Termín platby: 15. 4. 2026", "Cena 450 Kč, termín 15. 4. 2026")) {
            assertEquals(d(2026, 4, 15), parsePayment("Cena 450 Kč\n$label", profile, today).dueDate, label)
        }
        assertFalse(isPastDue(parsePayment("Cena 450 Kč", profile, today), today))
        // without a year a deadline just passed is still this year, not next
        assertTrue(isPastDue(parsePayment("Termín: 20. 4.", profile, today), today))
        assertFalse(isPastDue(parsePayment("Termín: 20. 5.", profile, today), today))
    }

    @Test
    fun perDayPriceNextToFullPriceNeedsUserChoice() {
        for (text in listOf(
            "Cena: 5000 Kč\nJednotlivý den: 800 Kč / den",
            "800 Kč/den\nCelý kurz: 5000 Kč",
            "Cena 5000 Kč, případně 800 Kč za den",
            "Denní cena: 800 Kč, celý kurz 5000 Kč",
            "Celý kurz 5000 Kč\n800 Kč denně"
        )) {
            val parsed = parsePayment("$text\nÚčet: 1806875329/0800", profile, today)
            val options = parsed.programs.single().options
            assertEquals(listOf(500000L), options.filterNot { it.perDay }.map { it.amountMinor }, text)
            assertEquals(listOf(80000L), options.filter { it.perDay }.map { it.amountMinor }, text)
            assertTrue(needsUserChoice(parsed), text)
            assertEquals(500000, selectAmount(parsed, today)?.amountMinor, text)
            assertNull(priceHint(parsed, today), text)
        }
        assertEquals("800 Kč / den", amountOptionLabel(AmountOption(80000, perDay = true)))
    }

    @Test
    fun fullAndPerDayPriceIsNeverPreselected() {
        val full = AmountOption(500000)
        val day = AmountOption(80000, perDay = true)
        assertNull(preselectedAmount(listOf(full, day), today))
        assertNull(preselectedAmount(listOf(day, full), today))
        assertEquals(full, preselectedAmount(listOf(full), today))
        assertEquals(day, preselectedAmount(listOf(day), today))
        assertEquals(500000, preselectedAmount(listOf(full, AmountOption(600000, audience = PriceAudience.OTHER)), today)?.amountMinor)
    }

    @Test
    fun perDayBlockIsNotAProgram() {
        for (text in listOf(
            "Celý kurz\n5000 Kč\n\nJednotlivé dny\n800 Kč / den",
            "Jednotlivé dny\n800 Kč / den\n\nCelý kurz\n5000 Kč"
        )) {
            val parsed = parsePayment("$text\nÚčet: 1806875329/0800", profile, today)
            assertEquals(1, parsed.programs.size, text)
            assertEquals(setOf(500000L to false, 80000L to true), parsed.amounts.map { it.amountMinor to it.perDay }.toSet(), text)
            assertFalse(programsDiffer(parsed), text)
            assertNull(preselectedAmount(parsed, today), text)
        }
        val two = parsePayment("A\n5000 Kč\n\nB\n3000 Kč\n\nDenně\n300 Kč / den", profile, today)
        assertEquals(2, two.programs.size)
        assertEquals(listOf(false, true), two.programs[1].options.map { it.perDay })
        assertTrue(programsDiffer(two))
    }

    @Test
    fun clubPriceAfterAPeriodOnOneLine() {
        val parsed = parsePayment("Cena: 5990 Kč. Členové Olympu: 4800 Kč\nÚčet: 1806875329/0800", profile, today)
        assertEquals(
            listOf(AmountOption(599000), AmountOption(480000, audience = PriceAudience.CLUB)),
            parsed.programs.single().options
        )
        assertEquals(480000, selectAmount(parsed, today)?.amountMinor)
    }

    @Test
    fun depositNextToProgramsWithRemainder() {
        val text = """
            Tréninkové programy:
            1) Výkonnostní - soutěžní páry CBAM (zlatá, žlutá)
            trenéři: Kučera, Kučerová, Hojdan, D. Odstrčil, Bohak, Harvan Landsfeldová a kluboví trenéři
            - denně: 2x vedená, practise, pohybová průprava, rozcvička, strečink;
            5200 Kč

            2) Základní a dětský- páry ED (červená, modrá, fialová, oranžová)
            trenéři: Hýžová, Hýžová ml., Grepl, Pecha, Šišková.
            - denně 2x vedená, practise, pohybová průprava, 1 individuální lekce denně v ceně, rozcvička, strečink
            4600 Kč

            Přihlašování:
            Do 30. 6. 2024
            Platba: záloha 2000 Kč do 30. 6.
            Číslo účtu: 1806875329/0800, VS: rodné číslo
            SS: 20243006 Možné zaplatit celou částku
            Doplatek na účet klubu do 15.7., SS: 20241507
        """.trimIndent()
        val parsed = parsePayment(text, profile, today)
        assertEquals(listOf(520000L, 460000L), parsed.programs.map { it.options.single().amountMinor })
        assertTrue(parsed.programs.all { p -> p.options.none { it.perDay } })
        assertTrue(programsDiffer(parsed))
        assertEquals(Deposit(200000, until = d(2026, 6, 30), remainderUntil = d(2026, 7, 15), remainderSs = "20241507"), parsed.deposit)
        assertEquals("20243006", parsed.ss)
        assertEquals("1806875329/0800", parsed.account)
        assertEquals(VsSource.BirthNumberFromProfile, parsed.vs)
        assertTrue(parsed.missing.isEmpty())
        assertTrue(needsUserChoice(parsed))
        assertEquals(320000, remainderAmount(520000, parsed.deposit!!))
        assertNull(remainderAmount(null, parsed.deposit!!))
        assertNull(remainderAmount(150000, parsed.deposit!!))
    }

    @Test
    fun yearlessDatesFollowTheEvent() {
        val text = "Cena 5000 Kč do 20. 12.\nPlatba: záloha 2000 Kč do 31. 12.\nÚčet: 1806875329/0800\nDoplatek do 15. 12., SS: 5"
        // event on 1. 1. 2026: 31. 12. would be after it ended -> the year before, never 2027
        val newYear = parsePayment(text, profile, d(2026, 5, 1), eventEnd = d(2026, 1, 1))
        assertEquals(d(2025, 12, 31), newYear.deposit?.until)
        assertEquals(d(2025, 12, 15), newYear.deposit?.remainderUntil)
        assertEquals(d(2025, 12, 20), newYear.amounts.single().until)
        // summer camp: dates before it stay in the event's year, whatever today is
        val camp = parsePayment("Záloha 2000 Kč do 30. 6.\nDoplatek do 15. 7., SS: 5\nÚčet: 1806875329/0800", profile, d(2026, 11, 20), eventEnd = d(2024, 8, 2))
        assertEquals(d(2024, 6, 30), camp.deposit?.until)
        assertEquals(d(2024, 7, 15), camp.deposit?.remainderUntil)
        // an explicit year wins, and no event keeps the old behaviour
        assertEquals(d(2024, 6, 30), parsePayment("Záloha 2000 Kč do 30. 6. 2024", profile, today, eventEnd = d(2026, 8, 2)).deposit?.until)
        assertEquals(d(2026, 6, 30), parsePayment("Záloha 2000 Kč do 30. 6.", profile, today).deposit?.until)
        // deadline too
        assertEquals(d(2025, 12, 31), parsePayment("Termín: 31. 12.", profile, today, eventEnd = d(2026, 1, 1)).dueDate)
    }

    @Test
    fun depositAloneIsEnoughAmount() {
        val parsed = parsePayment("Záloha 2000 Kč do 20. 5.\nÚčet: 1806875329/0800", profile, today)
        assertEquals(Deposit(200000, until = d(2026, 5, 20)), parsed.deposit)
        assertTrue(parsed.programs.isEmpty())
        assertFalse(MissingField.AMOUNT in parsed.missing)
        assertNull(parsePayment("Cena 450 Kč", profile, today).deposit)
    }

    @Test
    fun plainSecondPriceIsNotPerDay() {
        val parsed = parsePayment("Cena 5000 Kč do 31. 5.\nCena 5500 Kč po 31. 5.", profile, today)
        assertTrue(parsed.amounts.none { it.perDay })
        assertTrue(parsePayment("Cena: 450 Kč za 3 dny", profile, today).amounts.none { it.perDay })
    }

    @Test
    fun commaInsideAmountOrMessageDoesNotSplit() {
        val parsed = parsePayment("Cena: 449,50 Kč, účet: 1806875329/0800, poznámka: Novák, Jan, VS: 7", profile, today)
        assertEquals(44950, selectAmount(parsed, today)?.amountMinor)
        assertEquals("1806875329/0800", parsed.account)
        assertEquals("Novák, Jan", parsed.message)
        assertEquals(VsSource.Literal("7"), parsed.vs)
    }

    @Test
    fun missingVsDefaultsToBirthNumber() {
        val text = "Cena 450 Kč\nÚčet: 1806875329/0800"
        val withBn = parsePayment(text, profile, today)
        assertEquals(VsSource.BirthNumberFromProfile, withBn.vs)
        assertEquals("0601011234", resolveVariableSymbol(withBn.vs, profile))
        assertTrue(withBn.missing.isEmpty())
        assertEquals(listOf(MissingField.VS), parsePayment(text, noBirthNumber, today).missing)
        assertEquals(VsSource.Literal("12"), parsePayment("$text\nVS: 12", profile, today).vs)
    }

    @Test
    fun clubPriceBeatsOthersWhenLabelPrecedesAmount() {
        val parsed = parsePayment("Olymp: 5 000 Kč\nOstatní: 6 000 Kč\nÚčet: 1806875329/0800", profile, today)
        assertEquals(
            listOf(AmountOption(500000, audience = PriceAudience.CLUB), AmountOption(600000, audience = PriceAudience.OTHER)),
            parsed.programs.single().options
        )
        assertEquals(listOf(AmountOption(500000, audience = PriceAudience.CLUB)), parsed.amounts)
        assertEquals(500000, selectAmount(parsed, today)?.amountMinor)
        assertFalse(needsUserChoice(parsed))
    }

    @Test
    fun audienceSynonymsAndSingleLine() {
        for (text in listOf(
            "Klubová cena: 5000 Kč, ostatní cena: 6000 Kč",
            "Cena pro členy klubu 5000 Kč / pro nečleny 6000 Kč",
            "5000 Kč klubová, 6000 Kč ostatní",
            "Cena 5000 Kč (Olymp), 6000 Kč (externí)"
        )) {
            val parsed = parsePayment(text, profile, today)
            assertEquals(500000, selectAmount(parsed, today)?.amountMinor, text)
            assertEquals(1, parsed.amounts.size, text)
            assertFalse(needsUserChoice(parsed), text)
        }
    }

    @Test
    fun clubPriceKeepsItsDateTiers() {
        val text = "Klubová do 31. 5.: 5000 Kč\nKlubová po 31. 5.: 5500 Kč\nOstatní: 7000 Kč"
        val parsed = parsePayment(text, profile, today)
        assertEquals(listOf(500000L, 550000L), parsed.amounts.map { it.amountMinor })
        assertEquals(500000, selectAmount(parsed, today)?.amountMinor)
        assertEquals(550000, selectAmount(parsed, d(2026, 6, 15))?.amountMinor)
    }

    @Test
    fun othersOnlyAndUnlabeledPricesKeepWorking() {
        val others = listOf(AmountOption(600000, audience = PriceAudience.OTHER))
        assertEquals(others, applicableOptions(others))
        val mixed = listOf(AmountOption(450000), AmountOption(600000, audience = PriceAudience.OTHER))
        assertEquals(listOf(AmountOption(450000)), applicableOptions(mixed))
        assertNull(parsePayment("Cena 450 Kč", profile, today).amounts.single().audience)
    }

    @Test
    fun textWithoutAnyPaymentDataGivesEmptyResult() {
        val parsed = parsePayment("Ahoj, v sobotu se sejdeme v 9:00 na sále. Vezměte si boty!", profile, today)
        assertEquals(
            ParsedPayment(vs = VsSource.BirthNumberFromProfile, missing = listOf(MissingField.ACCOUNT, MissingField.AMOUNT)),
            parsed
        )
        assertEquals(
            ParsedPayment(vs = VsSource.BirthNumberFromProfile, missing = listOf(MissingField.ACCOUNT, MissingField.AMOUNT, MissingField.VS)),
            parsePayment("", null, today)
        )
    }

    @Test
    fun ibanInTextIsAlternativeToAccount() {
        val parsed = parsePayment("IBAN: CZ41 0800 0000 0018 0687 5329\nCena: 450 Kč", profile, today)
        assertNull(parsed.account)
        assertEquals("CZ4108000000001806875329", parsed.iban)
        assertFalse(MissingField.ACCOUNT in parsed.missing)
    }

    @Test
    fun nonBreakingSpacesAndDecimalAmountsWork() {
        val parsed = parsePayment("Částka: 1 250,50 Kč", null, today)
        assertEquals(listOf(AmountOption(125050)), parsed.amounts)
    }

    @Test
    fun dateWithoutYearUsesNearestFutureOccurrence() {
        val parsed = parsePayment("Cena 100 Kč do 31. 5.\nCena 200 Kč po 31. 5.", null, LocalDate(2026, 6, 10))
        assertEquals(d(2027, 5, 31), parsed.amounts[0].until)
        assertEquals(d(2027, 5, 31), parsed.amounts[1].after)
    }

    @Test
    fun htmlIsConvertedToLines() {
        val html = "<p>Cena: 450&nbsp;Kč</p><p>Účet: <a href=\"tel:1806875329\">1806875329</a>/0800<br>VS: 12</p>"
        val parsed = parsePayment(htmlToPlainText(html), null, today)
        assertEquals("1806875329/0800", parsed.account)
        assertEquals(listOf(AmountOption(45000)), parsed.amounts)
        assertEquals(VsSource.Literal("12"), parsed.vs)
    }

    // --- account, IBAN, SPD --------------------------------------------------------------

    @Test
    fun accountChecksum() {
        assertTrue(isValidAccountChecksum("1806875329/0800"))
        assertTrue(isValidAccountChecksum("19-2000145399/0800"))
        assertFalse(isValidAccountChecksum("1806875328/0800"))
        assertFalse(isValidAccountChecksum("18-2000145399/0800"))
    }

    @Test
    fun invalidChecksumIsKeptNotRejected() {
        assertEquals("1806875328/0800", parsePayment("Účet: 1806875328/0800", null, today).account)
    }

    @Test
    fun spdPayloadFromParsedPayment() {
        val parsed = parsePayment("Cena 450 Kč\nÚčet: 1806875329/0800\nSS: 20260711\nVS: 15102026", null, today)
        val spd = buildSpdPayload(parsed.account!!, parsed.amounts.single().amountMinor, "CZK", "15102026", parsed.ss, null)
        assertEquals("SPD*1.0*ACC:CZ4108000000001806875329*AM:450.00*CC:CZK*X-SS:20260711*X-VS:15102026", spd)
    }

    @Test
    fun spdMessageIsAsciiUppercaseWithoutStarAndCutToSixtyChars() {
        val long = "Žluťoučký kůň * úpěl ďábelské ódy ".repeat(4)
        val spd = buildSpdPayload("1806875329/0800", null, "CZK", null, null, long)!!
        val msg = spd.substringAfter("*MSG:")
        assertEquals(60, msg.length)
        assertTrue(msg.startsWith("ZLUTOUCKY KUN  UPEL DABELSKE ODY"))
        assertFalse('*' in msg)
    }

    // --- price hint ----------------------------------------------------------------------

    private val tiered = parsePayment(sample5, profile, today)

    @Test
    fun priceHintThreeDaysLeft() {
        val hint = priceHint(tiered, d(2026, 6, 19))
        assertEquals(PriceHint(519000, d(2026, 6, 22), 549000, 3), hint)
        assertEquals("Cena 5 190 Kč platí ještě do 22. 6. 2026 (zbývají 3 dny), poté 5 490 Kč.", priceHintMessage(hint!!))
    }

    @Test
    fun priceHintLastDayAndTomorrowAndDeclension() {
        assertEquals(0, priceHint(tiered, d(2026, 6, 22))!!.daysLeft)
        assertTrue("platí dnes naposledy" in priceHintMessage(priceHint(tiered, d(2026, 6, 22))!!))
        assertTrue("platí ještě zítra" in priceHintMessage(priceHint(tiered, d(2026, 6, 21))!!))
        assertTrue("zbývá 5 dní" in priceHintMessage(priceHint(tiered, d(2026, 6, 17))!!))
    }

    @Test
    fun noPriceHintOnLastLevelFarAheadPastOrSinglePrice() {
        assertNull(priceHint(tiered, d(2026, 6, 23)))
        assertNull(priceHint(tiered, d(2026, 6, 10)))
        assertNull(priceHint(parsePayment("Cena 450 Kč do 31. 5. 2026", null, today), d(2026, 5, 30)))
        // the user picks the first level after its date has passed
        assertNull(priceHint(tiered.amounts, tiered.amounts[0], d(2026, 6, 1)))
    }

    @Test
    fun czkFormatting() {
        assertEquals("5 190 Kč", formatCzk(519000))
        assertEquals("449,50 Kč", formatCzk(44950))
        assertNotNull(selectAmount(tiered, today))
    }

    // --- several programs, expired levels ------------------------------------------------

    private val twoPrograms = """
        Program A
        Cena: 345 Kč přihláška do 31. 5. 2026
        395 Kč přihláška po 31. 5. 2026

        Program B
        Cena: 500 Kč přihláška do 31. 5. 2026
        560 Kč přihláška po 31. 5. 2026
        Účet: 1806875329/0800
        VS: rodné číslo
    """.trimIndent()

    @Test
    fun severalPriceBlocksBecomeSeparatePrograms() {
        val parsed = parsePayment(twoPrograms, profile, today)
        assertEquals(
            listOf(
                PriceProgram("Program A", listOf(AmountOption(34500, until = d(2026, 5, 31)), AmountOption(39500, after = d(2026, 5, 31)))),
                PriceProgram("Program B", listOf(AmountOption(50000, until = d(2026, 5, 31)), AmountOption(56000, after = d(2026, 5, 31))))
            ),
            parsed.programs
        )
        assertTrue(needsUserChoice(parsed))
    }

    @Test
    fun datedLevelsNeedNoChoiceButUndatedOnesDo() {
        assertFalse(needsUserChoice(tiered))
        assertFalse(needsUserChoice(parsePayment("Cena 450 Kč", null, today)))
        assertTrue(needsUserChoice(parsePayment("Cena:\n450 Kč dospělí\n300 Kč děti", null, today)))
    }

    @Test
    fun expiredLevelsAreExplainedAndLabelled() {
        assertEquals(
            "Cena 4 890 Kč platila do 31. 5. 2026 a už není k dispozici.",
            expiredPricesNote(tiered.amounts, d(2026, 6, 1))
        )
        assertNull(expiredPricesNote(tiered.amounts, d(2026, 5, 31)))
        assertEquals("5 190 Kč · do 22. 6. 2026", amountOptionLabel(tiered.amounts[1]))
        assertEquals("5 490 Kč · po 22. 6. 2026", amountOptionLabel(tiered.amounts[2]))
    }

    @Test
    fun realTrainerTextWithTwoNumberedProgramsAndStorno() {
        val text = """
            Tréninkové programy:
            1) Hlavní - soutěžní páry CBAM
            trenéři LAT: Lukáš Hojdan, Filip Karásek
            kondiční specialista: Pavel Grepl
            - denně: 2x vedená, practise, pohybová průprava, rozcvička, strečink;

            Cena: 
            5490 Kč přihláška do 31. 5. 2026 včetně platby
            5690 Kč přihláška do 22. 6. 2026 včetně platby
            5990 Kč přihláška po 22. 6. 2026 včetně platby

            2) Basic - modrá, fialová, zelená a oranžová skupina. Začátečníci, páry EDC a sólo dívky.
            trenéři: Žaneta Brizgalová, Pavel Grepl
            - denně 2x vedená, practise, 1 individuální lekce denně v ceně, rozcvička, strečink

            Cena: 
            4890 Kč přihláška do 31. 5. 2026
            5190 Kč přihláška do 22. 6. 2026
            5490 Kč přihláška po 22. 6. 2026

            Platební údaje: 
            SS: 20262707
            VS: rodné číslo
            Číslo účtu: 1806875329/0800 

            Storno podmínky 
            100 % vrácení částky při odhlášení alespoň 5 dní předem (do 20. 7. vč.)
            50 % vrácení částky při odhlášení alespoň 2 dny předem (do 25. 7. vč. ). 
            0 % částky při pozdějším odhlášení. 
        """.trimIndent()
        val parsed = parsePayment(text, profile, today)
        assertEquals(
            listOf(
                PriceProgram(
                    "Hlavní - soutěžní páry CBAM",
                    listOf(
                        AmountOption(549000, until = d(2026, 5, 31)),
                        AmountOption(569000, until = d(2026, 6, 22)),
                        AmountOption(599000, after = d(2026, 6, 22))
                    )
                ),
                PriceProgram(
                    "Basic - modrá, fialová, zelená a oranžová skupina. Začátečníci, páry EDC a sólo dívky.",
                    listOf(
                        AmountOption(489000, until = d(2026, 5, 31)),
                        AmountOption(519000, until = d(2026, 6, 22)),
                        AmountOption(549000, after = d(2026, 6, 22))
                    )
                )
            ),
            parsed.programs
        )
        assertEquals("1806875329/0800", parsed.account)
        assertEquals("20262707", parsed.ss)
        assertEquals(VsSource.BirthNumberFromProfile, parsed.vs)
        assertEquals(
            listOf(
                "100 % vrácení částky při odhlášení alespoň 5 dní předem (do 20. 7. vč.)",
                "50 % vrácení částky při odhlášení alespoň 2 dny předem (do 25. 7. vč. ).",
                "0 % částky při pozdějším odhlášení."
            ),
            parsed.storno
        )
        assertTrue(needsUserChoice(parsed))
    }

    @Test
    fun stornoLabelVariants() {
        assertEquals(listOf("do 20. 7. zdarma"), parsePayment("Storno: do 20. 7. zdarma", null, today).storno)
        assertEquals(listOf("do 20. 7. zdarma"), parsePayment("Storno do 20. 7. zdarma", null, today).storno)
        assertEquals(listOf("a", "b"), parsePayment("Stornopodmínky:\n- a\n• b\n\nVS: 1", null, today).storno)
    }

    @Test
    fun programsWithIdenticalPriceLevelsNeedNoChoice() {
        val same = parsePayment(
            "1) A\nCena:\n100 Kč do 31. 5. 2026\n150 Kč po 31. 5. 2026\n\n2) B\nCena:\n100 Kč do 31. 5. 2026\n150 Kč po 31. 5. 2026",
            null, today
        )
        assertEquals(2, same.programs.size)
        assertFalse(needsUserChoice(same))
        assertTrue(needsUserChoice(parsePayment(twoPrograms, profile, today)))
    }
}

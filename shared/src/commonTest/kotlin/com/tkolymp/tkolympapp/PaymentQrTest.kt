package com.tkolymp.tkolympapp

import com.tkolymp.shared.payments.buildSpdPayload
import com.tkolymp.shared.payments.findPaymentQrs
import com.tkolymp.shared.payments.isClubAccount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentQrTest {
    @Test
    fun convertsCzechAccountWithPrefixToIban() {
        val spd = buildSpdPayload("19-2000145399/0800", 45000, "CZK", null, null, null)
        assertEquals("SPD*1.0*ACC:CZ6508000000192000145399*AM:450.00*CC:CZK", spd)
    }

    @Test
    fun buildsFullPayloadWithSymbolsAndSafeMessage() {
        val spd = buildSpdPayload("2000145399/2010", 123456, "CZK", "12345", "99", "Jan Novák*Čeština")
        assertEquals(
            "SPD*1.0*ACC:CZ9320100000002000145399*AM:1234.56*CC:CZK*MSG:JAN NOVAKCESTINA*X-SS:99*X-VS:12345",
            spd
        )
    }

    @Test
    fun returnsNullForUnparsableAccount() {
        assertNull(buildSpdPayload("not-an-account", 100, "CZK", null, null, null))
    }

    @Test
    fun findsPayliboQrInEventHtmlAndAddsBirthNumberAsVs() {
        val html = """<p><img src="https://api.paylibo.com/paylibo/generator/czech/image?compress=false&amp;size=440&amp;accountNumber=1806875329&amp;bankCode=0800&amp;amount=450&amp;currency=CZK&amp;ss=20260711&amp;message=Junior+camp+listopad+2026%3A+jm%C3%A9no" alt="QR"></p>"""
        val found = findPaymentQrs(html, "060101/1234", "Jan Novák")
        assertEquals(1, found.size)
        val p = found.single()
        assertEquals("1806875329/0800", p.account)
        assertEquals(45000L, p.amountMinor)
        assertEquals("20260711", p.specificSymbol)
        assertEquals("0601011234", p.variableSymbol)
        assertEquals("Junior camp listopad 2026: Jan Novák", p.message)
    }

    @Test
    fun ignoresHtmlWithoutPayliboImage() {
        assertEquals(emptyList(), findPaymentQrs("<p>nothing <img src=\"https://example.com/a.png\"></p>", "1"))
    }

    @Test
    fun namePlaceholderInQrMessageIsReplacedOrDropped() {
        val html = """<img src="https://api.paylibo.com/paylibo/generator/czech/image?accountNumber=1806875329&amp;bankCode=0800&amp;amount=450&amp;message=Camp+JM%C3%89NO+a+P%C5%98%C3%8DJMEN%C3%8D+%28nutno+doplnit%29">"""
        assertEquals("Camp Jan Novák", findPaymentQrs(html, null, "Jan Novák").single().message)
        assertEquals("Camp", findPaymentQrs(html, null, null).single().message)
    }

    @Test
    fun recognisesClubAccountInAnyFormat() {
        assertTrue(isClubAccount("1806875329/0800"))
        assertTrue(isClubAccount(" 1806875329 / 0800 "))
        assertTrue(isClubAccount("0000001806875329/0800".removePrefix("000000")))
    }

    @Test
    fun rejectsOtherAccounts() {
        assertFalse(isClubAccount("1806875328/0800"))
        assertFalse(isClubAccount("1806875329/0100"))
        assertFalse(isClubAccount("19-1806875329/0800"))
        assertFalse(isClubAccount("not an account"))
    }
}

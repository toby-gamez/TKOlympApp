package com.tkolymp.tkolympapp

import com.tkolymp.shared.payments.buildSpdPayload
import com.tkolymp.shared.payments.findPaymentQrs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
            "SPD*1.0*ACC:CZ9320100000002000145399*AM:1234.56*CC:CZK*X-VS:12345*X-SS:99*MSG:JAN NOVAKCESTINA",
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
        val found = findPaymentQrs(html, "060101/1234")
        assertEquals(1, found.size)
        val p = found.single()
        assertEquals("1806875329/0800", p.account)
        assertEquals(45000L, p.amountMinor)
        assertEquals("20260711", p.specificSymbol)
        assertEquals("0601011234", p.variableSymbol)
        assertEquals("Junior camp listopad 2026: jméno", p.message)
    }

    @Test
    fun ignoresHtmlWithoutPayliboImage() {
        assertEquals(emptyList(), findPaymentQrs("<p>nothing <img src=\"https://example.com/a.png\"></p>", "1"))
    }
}

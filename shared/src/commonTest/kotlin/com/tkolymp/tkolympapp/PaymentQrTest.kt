package com.tkolymp.tkolympapp

import com.tkolymp.shared.payments.buildSpdPayload
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
}

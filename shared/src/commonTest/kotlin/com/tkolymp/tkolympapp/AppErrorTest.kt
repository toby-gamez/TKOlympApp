package com.tkolymp.tkolympapp

import com.tkolymp.shared.viewmodels.AppError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AppErrorTest {

    @Test
    fun `generic factory with message`() {
        val err = AppError.generic("Something went wrong")
        assertIs<AppError.Generic>(err)
        assertEquals("Something went wrong", err.message)
    }

    @Test
    fun `generic factory with null uses default message`() {
        val err = AppError.generic(null)
        assertIs<AppError.Generic>(err)
        assertEquals("Neznámá chyba", err.message)
    }

    @Test
    fun `network factory`() {
        val err = AppError.network("Timeout")
        assertIs<AppError.Network>(err)
        assertEquals("Timeout", err.message)
    }

    @Test
    fun `network factory with null uses default message`() {
        val err = AppError.network(null)
        assertEquals("Chyba sítě", err.message)
    }

    @Test
    fun `notFound factory`() {
        val err = AppError.notFound("Event not found")
        assertIs<AppError.NotFound>(err)
        assertEquals("Event not found", err.message)
    }

    @Test
    fun `sealed subtype equality`() {
        val a = AppError.Generic("oops")
        val b = AppError.Generic("oops")
        assertEquals(a, b)
    }

    @Test
    fun `message property accessible via base type`() {
        val errors: List<AppError> = listOf(
            AppError.Generic("a"),
            AppError.Network("b"),
            AppError.NotFound("c")
        )
        val messages = errors.map { it.message }
        assertEquals(listOf("a", "b", "c"), messages)
    }
}

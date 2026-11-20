package com.tkolymp.tkolympapp

import com.tkolymp.shared.errorreporting.ErrorReporter
import com.tkolymp.shared.viewmodels.AppError
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
        val err = AppError.network("Bad gateway")
        assertIs<AppError.Network>(err)
        assertEquals("Bad gateway", err.message)
    }

    @Test
    fun `network factory shows offline notice for connectivity failures`() {
        val err = AppError.network("Unable to resolve host \"tkolymp.cz\"", RuntimeException("Unable to resolve host"))
        assertEquals("Jste offline", err.message)
        assertEquals("Jste offline", AppError.network("Timeout").message)
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

    @Test
    fun `cancellation is detected directly and in the cause chain`() = with(ErrorReporter) {
        assertTrue(CancellationException("The coroutine scope left the composition").isCancellation())
        assertTrue(RuntimeException("wrapped", CancellationException("x")).isCancellation())
        assertFalse(RuntimeException("boom").isCancellation())
        assertFalse((null as Throwable?).isCancellation())
    }

    @Test
    fun `generic factory still returns an error for a cancellation cause`() {
        val err = AppError.generic("The coroutine scope left the composition", CancellationException("x"))
        assertIs<AppError.Generic>(err)
    }
}

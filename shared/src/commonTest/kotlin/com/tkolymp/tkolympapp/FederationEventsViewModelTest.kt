package com.tkolymp.tkolympapp

import com.tkolymp.shared.competitivedata.*
import com.tkolymp.shared.viewmodels.EventsViewMode
import com.tkolymp.shared.viewmodels.FederationEventsViewModel
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private fun ev(id: Long, date: String, name: String, status: String = "PLANNED", lat: Double? = 50.0, results: Boolean = false, types: Set<String> = emptySet()) =
    FederationEventSummary(id, name, date, null, "Praha", null, "Czechia", lat, lat?.let { 14.0 }, status, 3, results, types)

private class FakeCompetitiveData(private val events: List<FederationEventSummary>) : ICompetitiveDataService {
    override suspend fun getFederations() = listOf(DanceFederation("csts", "ČSTS"))
    override suspend fun getYears(federation: String) = events.map { it.startDate.take(4).toInt() }.distinct().sorted().map { FederationYear(it, 1, 1) }
    override suspend fun getEvents(federation: String, year: Int, forceRefresh: Boolean) = LoadedData(events.filter { it.startDate.startsWith("$year") })
    override suspend fun getEventDetail(eventId: Long): FederationEventDetail? = null
    override suspend fun getCompetitionResults(competitionId: Long): FederationCompetitionResults? = null
}

class FederationEventsViewModelTest {
    private val events = listOf(
        ev(1, "2026-01-10", "Jarní pohár", results = true, types = setOf("CUP", "RANKING")),
        ev(2, "2026-01-20", "Zimní cena", status = "CANCELLED"),
        ev(3, "2026-03-05", "Brno Open", lat = null, types = setOf("RANKING")),
    )

    private suspend fun vm(mode: EventsViewMode = EventsViewMode.YEAR) =
        FederationEventsViewModel(FakeCompetitiveData(events)).also { it.load("csts"); it.setViewMode(mode) }

    @Test fun cancelledEventsAreAlwaysHidden() = runTest {
        assertEquals(listOf(1L, 3L), vm().state.value.events.map { it.id })
    }

    @Test fun monthAndSearchFiltersCombine() = runTest {
        val vm = vm()
        vm.selectMonth(1)
        assertEquals(listOf(1L), vm.state.value.events.map { it.id })
        vm.setQuery("zimní") // the only match is cancelled
        assertEquals(emptyList(), vm.state.value.events.map { it.id })
        vm.setQuery("jarní")
        assertEquals(listOf(1L), vm.state.value.events.map { it.id })
    }

    @Test fun typeFilterKeepsEventsWithAnySelectedType() = runTest {
        val vm = vm()
        assertEquals(mapOf("CUP" to 1, "RANKING" to 2), vm.state.value.typeCounts)
        vm.toggleType("CUP")
        assertEquals(listOf(1L), vm.state.value.events.map { it.id })
        vm.toggleType("RANKING")
        assertEquals(listOf(1L, 3L), vm.state.value.events.map { it.id })
        vm.toggleType("CUP"); vm.toggleType("RANKING")
        assertEquals(listOf(1L, 3L), vm.state.value.events.map { it.id })
    }

    @Test fun searchIgnoresIds() = runTest {
        val vm = vm()
        vm.setQuery("3")
        assertEquals(emptyList(), vm.state.value.events.map { it.id })
    }

    @Test fun upcomingViewOnlyKeepsEventsFromTodayOn() = runTest {
        val future = listOf(ev(10, "2999-05-01", "Far away"), ev(11, "2000-01-01", "Long gone"))
        val vm = FederationEventsViewModel(FakeCompetitiveData(future)).also { it.load("csts") }
        // Default view: nothing is fetched for the 2999 year (only this and next year), and past events are dropped.
        assertEquals(emptyList(), vm.state.value.events.map { it.id })
    }

    @Test fun pastViewListsFinishedEventsNewestFirst() = runTest {
        val all = listOf(ev(20, "2000-01-01", "Oldest"), ev(21, "2010-06-01", "Newer"), ev(22, "2999-05-01", "Far future"))
        val vm = FederationEventsViewModel(FakeCompetitiveData(all)).also { it.load("csts"); it.setViewMode(EventsViewMode.PAST) }
        assertEquals(listOf(21L, 20L), vm.state.value.events.map { it.id })
    }

    @Test fun selectsCurrentYearAndLoads() = runTest {
        val s = vm().state.value
        assertEquals(2026, s.year)
        assertEquals("ČSTS", s.federationName)
        assertNull(s.error)
    }
}

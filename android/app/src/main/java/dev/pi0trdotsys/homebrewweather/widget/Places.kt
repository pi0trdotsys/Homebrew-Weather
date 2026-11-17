package dev.pi0trdotsys.homebrewweather.widget

import java.util.Locale
import kotlin.math.abs

/**
 * The places one widget can show, and which of them it shows right now.
 *
 * A widget used to be bound to exactly one city, so "home and work" meant two
 * widgets. It now holds a short list and the header cycles through it on tap
 * (see WeatherWidgetProvider.ACTION_NEXT_PLACE). One of the entries may be the
 * "follow my location" place ([WidgetCity.isLive]); there is never more than
 * one.
 *
 * Pure functions over an immutable [State] — the persistence lives in
 * [WidgetPrefs], the rules here, covered by PlacesTest.
 */
object Places {

    /** Enough for home, work and a couple of others; more makes a tap-to-cycle
     * header slower than opening the app. */
    const val MAX = 5

    data class State(val list: List<WidgetCity>, val index: Int) {
        val current: WidgetCity? get() = list.getOrNull(index)
        val size: Int get() = list.size
        val canCycle: Boolean get() = list.size >= 2

        companion object {
            val EMPTY = State(emptyList(), 0)
        }
    }

    /** Two places are the same if they're within ~1 km — the same rule the web
     * app's saved-cities list uses — or both follow the device's location. */
    fun same(a: WidgetCity, b: WidgetCity): Boolean =
        if (a.isLive || b.isLive) a.isLive && b.isLive
        else abs(a.lat - b.lat) < 0.01 && abs(a.lon - b.lon) < 0.01

    /** Stable key for a place's cached forecast. A live place's coordinates
     * change on every refresh, so it gets one fixed key instead of a new one
     * each time; a fixed place is keyed by its ~1 km cell. */
    fun cacheKey(c: WidgetCity): String =
        if (c.isLive) "live" else String.format(Locale.US, "%.2f_%.2f", c.lat, c.lon)

    /**
     * Swaps the current place for [city] — what "change city" always did. Any
     * *other* entry that is the same place is dropped, so replacing never
     * leaves the list showing one place twice.
     */
    fun replaceCurrent(state: State, city: WidgetCity): State {
        if (state.list.isEmpty()) return State(listOf(city), 0)
        val idx = state.index.coerceIn(0, state.list.lastIndex)
        val replaced = state.list.mapIndexed { i, c -> if (i == idx) city else c }
        val kept = replaced.filterIndexed { i, c -> i == idx || !same(c, city) }
        val newIndex = (0 until idx).count { !same(state.list[it], city) }
        return State(kept, newIndex)
    }

    /**
     * Adds [city] as another place and makes it the current one. A place that
     * is already in the list is selected (and refreshed) rather than added
     * twice. When the list is full the current place is replaced instead —
     * adding must never silently do nothing.
     */
    fun add(state: State, city: WidgetCity): State {
        val existing = state.list.indexOfFirst { same(it, city) }
        if (existing >= 0) {
            return State(state.list.mapIndexed { i, c -> if (i == existing) city else c }, existing)
        }
        if (state.list.size >= MAX) return replaceCurrent(state, city)
        return State(state.list + city, state.list.size)
    }

    fun remove(state: State, position: Int): State {
        if (position !in state.list.indices) return state
        val list = state.list.filterIndexed { i, _ -> i != position }
        if (list.isEmpty()) return State.EMPTY
        val index = when {
            position < state.index -> state.index - 1
            else -> state.index
        }
        return State(list, index.coerceIn(0, list.lastIndex))
    }

    /** The next place, wrapping around; unchanged with fewer than two. */
    fun next(state: State): State =
        if (!state.canCycle) state else State(state.list, (state.index + 1) % state.list.size)

    /** "2/3" for the header, or null when there is nothing to cycle through. */
    fun badge(state: State): String? =
        if (state.canCycle) "${state.index + 1}/${state.size}" else null
}

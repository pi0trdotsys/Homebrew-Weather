package dev.pi0trdotsys.homebrewweather.widget

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OutdoorTemperatureExportTest {

    // 2026-11-18 09:30 in a place at UTC+1 (08:30 UTC).
    private val now = 1_794_990_600_000L
    private val offset = 3600

    private fun localToday() = WeatherApi.localHourIso(now, offset).substringBefore('T')

    private fun day(date: String, max: Double, min: Double) =
        WeatherApi.DailyEntry(date, 1, max, min, 0)

    private fun hour(time: String, temp: Double) =
        WeatherApi.HourlyEntry(time, 0, 1, temperature = temp)

    private fun forecast(
        temp: Double = 15.0,
        daily: List<WeatherApi.DailyEntry> = emptyList(),
        hourly: List<WeatherApi.HourlyEntry> = emptyList(),
        offset: Int? = this.offset,
    ) = WeatherApi.WeatherData(
        isDay = true, currentWeatherCode = 1, currentTemperature = temp,
        daily = daily, hourly = hourly, utcOffsetSeconds = offset,
    )

    @Test
    fun `the rebased value is used, not the stale one from fetch time`() {
        val today = localToday()
        // Fetched at 23:00 the evening before, 2 degrees then; the hour that is now says 7.
        val w = forecast(
            temp = 2.0,
            daily = listOf(day("2026-11-17", 9.0, 1.0), day(today, 11.0, 4.0)),
            hourly = listOf(
                hour("${today.dropLast(2)}17T23:00", 2.0),
                hour("${today}T08:00", 5.0),
                hour("${today}T09:00", 7.0),
                hour("${today}T10:00", 9.0),
            ),
        )
        val row = OutdoorTemperatureExport.row(w, 123L, now)!!
        assertEquals(7.0, row.tempC, 0.0)
        assertEquals(123L, row.updatedAtMillis)
    }

    @Test
    fun `no current temperature gives no row`() {
        assertNull(OutdoorTemperatureExport.row(forecast(temp = Double.NaN), 1L, now))
        assertNull(OutdoorTemperatureExport.pick(listOf(OutdoorTemperatureExport.Candidate(forecast(temp = Double.NaN), 1L)), now))
    }

    @Test
    fun `no candidates give no row`() {
        assertNull(OutdoorTemperatureExport.pick(emptyList(), now))
    }

    @Test
    fun `the most recently written cache wins`() {
        val a = OutdoorTemperatureExport.Candidate(forecast(temp = 10.0), 1_000L)
        val b = OutdoorTemperatureExport.Candidate(forecast(temp = 20.0), 3_000L)
        val c = OutdoorTemperatureExport.Candidate(forecast(temp = 30.0), 2_000L)
        assertEquals(20.0, OutdoorTemperatureExport.pick(listOf(a, b, c), now)!!.tempC, 0.0)
        assertEquals(20.0, OutdoorTemperatureExport.pick(listOf(c, b, a), now)!!.tempC, 0.0)
    }

    @Test
    fun `a newer cache without a usable temperature does not shadow an older good one`() {
        val old = OutdoorTemperatureExport.Candidate(forecast(temp = 10.0), 1_000L)
        val bad = OutdoorTemperatureExport.Candidate(forecast(temp = Double.NaN), 9_000L)
        assertEquals(10.0, OutdoorTemperatureExport.pick(listOf(old, bad), now)!!.tempC, 0.0)
    }

    @Test
    fun `an undated cache is still used alone but loses to a dated one`() {
        val undated = OutdoorTemperatureExport.Candidate(forecast(temp = 10.0), 0L)
        val dated = OutdoorTemperatureExport.Candidate(forecast(temp = 20.0), 5L)
        assertEquals(10.0, OutdoorTemperatureExport.pick(listOf(undated), now)!!.tempC, 0.0)
        assertEquals(20.0, OutdoorTemperatureExport.pick(listOf(undated, dated), now)!!.tempC, 0.0)
        assertEquals(20.0, OutdoorTemperatureExport.pick(listOf(dated, undated), now)!!.tempC, 0.0)
    }

    @Test
    fun `min and max come from today's entry, not yesterday's or tomorrow's`() {
        val today = localToday()
        val w = forecast(
            daily = listOf(day("2026-11-17", 99.0, -99.0), day(today, 12.5, 3.5), day("2026-11-19", 20.0, 10.0)),
        )
        val row = OutdoorTemperatureExport.row(w, 1L, now)!!
        assertEquals(12.5, row.maxC!!, 0.0)
        assertEquals(3.5, row.minC!!, 0.0)
    }

    @Test
    fun `today is the place's date, not the device's`() {
        // 23:30 UTC is already the next day at UTC+2.
        val lateUtc = 1_794_958_200_000L // 2026-11-17 23:30 UTC
        val w = forecast(
            offset = 7200,
            daily = listOf(day("2026-11-17", 1.0, 0.0), day("2026-11-18", 8.0, 2.0)),
        )
        val row = OutdoorTemperatureExport.row(w, 1L, lateUtc)!!
        assertEquals(8.0, row.maxC!!, 0.0)
        assertEquals(2.0, row.minC!!, 0.0)
    }

    @Test
    fun `no entry for today leaves max and min unknown`() {
        val w = forecast(daily = listOf(day("2026-11-10", 5.0, 1.0)))
        val row = OutdoorTemperatureExport.row(w, 1L, now)!!
        assertNull(row.maxC)
        assertNull(row.minC)
    }

    @Test
    fun `the row has exactly four columns and no location`() {
        assertArrayEquals(arrayOf("temp_c", "max_c", "min_c", "updated_at"), OutdoorTemperatureExport.COLUMNS)
        val today = localToday()
        val row = OutdoorTemperatureExport.row(forecast(daily = listOf(day(today, 12.0, 3.0))), 77L, now)!!
        val cols = row.toColumns()
        assertEquals(4, cols.size)
        assertArrayEquals(arrayOf<Any?>(15.0, 12.0, 3.0, 77L), cols)
        // Only numbers: nothing a place name or a coordinate could ride in on.
        assertFalse(cols.any { it is String })
        assertFalse(OutdoorTemperatureExport.COLUMNS.any { c ->
            listOf("lat", "lon", "name", "city", "place", "loc").any { c.contains(it) }
        })
        assertNotNull(row)
    }
}

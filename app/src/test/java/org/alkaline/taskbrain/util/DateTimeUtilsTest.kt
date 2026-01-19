package org.alkaline.taskbrain.util

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DateTimeUtilsTest {

    // region combineDatePickerWithTime Tests

    @Test
    fun `combineDatePickerWithTime creates correct timestamp for given date and time`() {
        // Create DatePicker millis for January 19, 2026 (UTC midnight)
        val datePickerMillis = DateTimeUtils.createDatePickerMillis(2026, 1, 19)

        // Combine with time 7:44
        val result = DateTimeUtils.combineDatePickerWithTime(datePickerMillis, 7, 44)

        // Extract components from result
        val calendar = Calendar.getInstance()
        calendar.time = result.toDate()

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH))
        assertEquals(19, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(44, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }

    @Test
    fun `combineDatePickerWithTime handles midnight correctly`() {
        val datePickerMillis = DateTimeUtils.createDatePickerMillis(2026, 6, 15)

        val result = DateTimeUtils.combineDatePickerWithTime(datePickerMillis, 0, 0)

        val calendar = Calendar.getInstance()
        calendar.time = result.toDate()

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, calendar.get(Calendar.MONTH))
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun `combineDatePickerWithTime handles end of day correctly`() {
        val datePickerMillis = DateTimeUtils.createDatePickerMillis(2026, 12, 31)

        val result = DateTimeUtils.combineDatePickerWithTime(datePickerMillis, 23, 59)

        val calendar = Calendar.getInstance()
        calendar.time = result.toDate()

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, calendar.get(Calendar.MONTH))
        assertEquals(31, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun `combineDatePickerWithTime handles February correctly`() {
        val datePickerMillis = DateTimeUtils.createDatePickerMillis(2026, 2, 28)

        val result = DateTimeUtils.combineDatePickerWithTime(datePickerMillis, 12, 30)

        val calendar = Calendar.getInstance()
        calendar.time = result.toDate()

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH))
        assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(12, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun `combineDatePickerWithTime handles leap year February 29 correctly`() {
        // 2024 is a leap year
        val datePickerMillis = DateTimeUtils.createDatePickerMillis(2024, 2, 29)

        val result = DateTimeUtils.combineDatePickerWithTime(datePickerMillis, 15, 0)

        val calendar = Calendar.getInstance()
        calendar.time = result.toDate()

        assertEquals(2024, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, calendar.get(Calendar.MONTH))
        assertEquals(29, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(15, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
    }

    // endregion

    // region createDatePickerMillis Tests

    @Test
    fun `createDatePickerMillis returns UTC midnight`() {
        val millis = DateTimeUtils.createDatePickerMillis(2026, 1, 19)

        val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        utcCalendar.timeInMillis = millis

        assertEquals(2026, utcCalendar.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, utcCalendar.get(Calendar.MONTH))
        assertEquals(19, utcCalendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, utcCalendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, utcCalendar.get(Calendar.MINUTE))
        assertEquals(0, utcCalendar.get(Calendar.SECOND))
        assertEquals(0, utcCalendar.get(Calendar.MILLISECOND))
    }

    // endregion

    // region createLocalTimestamp Tests

    @Test
    fun `createLocalTimestamp creates correct timestamp`() {
        val timestamp = DateTimeUtils.createLocalTimestamp(2026, 3, 15, 10, 30)

        val calendar = Calendar.getInstance()
        calendar.time = timestamp.toDate()

        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.MARCH, calendar.get(Calendar.MONTH))
        assertEquals(15, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
        assertEquals(0, calendar.get(Calendar.SECOND))
    }

    @Test
    fun `createLocalTimestamp uses 1-based month`() {
        // Month 1 should be January
        val januaryTimestamp = DateTimeUtils.createLocalTimestamp(2026, 1, 1, 0, 0)
        val calendar = Calendar.getInstance()
        calendar.time = januaryTimestamp.toDate()
        assertEquals(Calendar.JANUARY, calendar.get(Calendar.MONTH))

        // Month 12 should be December
        val decemberTimestamp = DateTimeUtils.createLocalTimestamp(2026, 12, 1, 0, 0)
        calendar.time = decemberTimestamp.toDate()
        assertEquals(Calendar.DECEMBER, calendar.get(Calendar.MONTH))
    }

    // endregion

    // region Round-trip Tests

    @Test
    fun `round trip from DatePicker millis to timestamp and back preserves date`() {
        val originalMillis = DateTimeUtils.createDatePickerMillis(2026, 7, 4)

        val timestamp = DateTimeUtils.combineDatePickerWithTime(originalMillis, 14, 30)
        val roundTripMillis = DateTimeUtils.getDatePickerMillisFromTimestamp(timestamp)

        assertEquals(originalMillis, roundTripMillis)
    }

    @Test
    fun `round trip preserves date across different times`() {
        val dateMillis = DateTimeUtils.createDatePickerMillis(2026, 11, 15)

        // Test with morning time
        val morningTimestamp = DateTimeUtils.combineDatePickerWithTime(dateMillis, 6, 0)
        val morningRoundTrip = DateTimeUtils.getDatePickerMillisFromTimestamp(morningTimestamp)
        assertEquals(dateMillis, morningRoundTrip)

        // Test with evening time
        val eveningTimestamp = DateTimeUtils.combineDatePickerWithTime(dateMillis, 22, 45)
        val eveningRoundTrip = DateTimeUtils.getDatePickerMillisFromTimestamp(eveningTimestamp)
        assertEquals(dateMillis, eveningRoundTrip)
    }

    // endregion

    // region Edge Cases

    @Test
    fun `combineDatePickerWithTime handles year boundary correctly`() {
        // December 31, 2025 -> January 1, 2026 transition
        val dec31 = DateTimeUtils.createDatePickerMillis(2025, 12, 31)
        val jan1 = DateTimeUtils.createDatePickerMillis(2026, 1, 1)

        val dec31Timestamp = DateTimeUtils.combineDatePickerWithTime(dec31, 23, 59)
        val jan1Timestamp = DateTimeUtils.combineDatePickerWithTime(jan1, 0, 0)

        val dec31Calendar = Calendar.getInstance()
        dec31Calendar.time = dec31Timestamp.toDate()

        val jan1Calendar = Calendar.getInstance()
        jan1Calendar.time = jan1Timestamp.toDate()

        assertEquals(2025, dec31Calendar.get(Calendar.YEAR))
        assertEquals(Calendar.DECEMBER, dec31Calendar.get(Calendar.MONTH))
        assertEquals(31, dec31Calendar.get(Calendar.DAY_OF_MONTH))

        assertEquals(2026, jan1Calendar.get(Calendar.YEAR))
        assertEquals(Calendar.JANUARY, jan1Calendar.get(Calendar.MONTH))
        assertEquals(1, jan1Calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `seconds and milliseconds are always zero`() {
        val dateMillis = DateTimeUtils.createDatePickerMillis(2026, 5, 20)
        val timestamp = DateTimeUtils.combineDatePickerWithTime(dateMillis, 12, 30)

        val calendar = Calendar.getInstance()
        calendar.time = timestamp.toDate()

        assertEquals(0, calendar.get(Calendar.SECOND))
        assertEquals(0, calendar.get(Calendar.MILLISECOND))
    }

    // endregion
}

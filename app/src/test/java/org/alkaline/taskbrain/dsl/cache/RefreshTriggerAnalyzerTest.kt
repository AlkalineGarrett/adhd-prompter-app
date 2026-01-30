package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.language.Lexer
import org.alkaline.taskbrain.dsl.language.Parser
import org.alkaline.taskbrain.dsl.language.RefreshExpr
import org.alkaline.taskbrain.dsl.runtime.Environment
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

/**
 * Tests for RefreshTriggerAnalyzer.
 * Phase 3: Time-based refresh analysis.
 */
class RefreshTriggerAnalyzerTest {

    // region Basic time comparisons

    @Test
    fun `simple time gt comparison`() {
        val analysis = analyze("refresh[if(time.gt(\"12:00\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertTrue(analysis.triggers[0] is DailyTimeTrigger)
        assertEquals(LocalTime.of(12, 0), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    @Test
    fun `simple time lt comparison`() {
        val analysis = analyze("refresh[if(time.lt(\"17:00\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertTrue(analysis.triggers[0] is DailyTimeTrigger)
        assertEquals(LocalTime.of(17, 0), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    @Test
    fun `time eq comparison`() {
        val analysis = analyze("refresh[if(time.eq(\"09:30\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertEquals(LocalTime.of(9, 30), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    // endregion

    // region Time with offset

    @Test
    fun `time plus minutes gt comparison`() {
        // time.plus(minutes:10).gt("12:00") → trigger at 11:50
        val analysis = analyze("refresh[if(time.plus(minutes: 10).gt(\"12:00\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertTrue(analysis.triggers[0] is DailyTimeTrigger)
        assertEquals(LocalTime.of(11, 50), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    @Test
    fun `time plus hours gt comparison`() {
        // time.plus(hours:1).gt("14:00") → trigger at 13:00
        val analysis = analyze("refresh[if(time.plus(hours: 1).gt(\"14:00\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertEquals(LocalTime.of(13, 0), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    @Test
    fun `time plus hours and minutes`() {
        // time.plus(hours:1, minutes:30).gt("15:00") → trigger at 13:30
        val analysis = analyze("refresh[if(time.plus(hours: 1, minutes: 30).gt(\"15:00\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertEquals(LocalTime.of(13, 30), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    // endregion

    // region Multiple comparisons

    @Test
    fun `two time comparisons with and`() {
        // Working hours: time.gt("09:00").and(time.lt("17:00"))
        val analysis = analyze("refresh[if(time.gt(\"09:00\").and(time.lt(\"17:00\")), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(2, analysis.triggers.size)
        val times = analysis.triggers.map { (it as DailyTimeTrigger).triggerTime }.sorted()
        assertEquals(LocalTime.of(9, 0), times[0])
        assertEquals(LocalTime.of(17, 0), times[1])
    }

    @Test
    fun `three time comparisons`() {
        val analysis = analyze("""
            refresh[
                if(time.gt("08:00").and(time.lt("12:00")), "morning",
                if(time.gt("13:00").and(time.lt("17:00")), "afternoon", "off"))
            ]
        """.trimIndent())

        assertTrue(analysis.success)
        // Should find multiple triggers
        assertTrue(analysis.triggers.isNotEmpty())
    }

    // endregion

    // region Variable resolution

    @Test
    fun `variable holding time literal`() {
        val analysis = analyze("refresh[start: \"09:00\"; if(time.gt(start), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertEquals(LocalTime.of(9, 0), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    @Test
    fun `multiple variables`() {
        val analysis = analyze("""
            refresh[start: "09:00"; end: "17:00";
                if(time.gt(start).and(time.lt(end)), "working", "off")]
        """.trimIndent())

        assertTrue(analysis.success)
        assertEquals(2, analysis.triggers.size)
    }

    // endregion

    // region Error cases

    @Test
    fun `no time comparisons returns error`() {
        val analysis = analyze("refresh[42]")

        assertFalse(analysis.success)
        assertNotNull(analysis.error)
        assertTrue(analysis.error!!.contains("requires time comparisons"))
    }

    @Test
    fun `no time comparisons with just arithmetic`() {
        val analysis = analyze("refresh[add(1, 2)]")

        assertFalse(analysis.success)
    }

    // endregion

    // region Trigger types

    @Test
    fun `daily time trigger is recurring`() {
        val analysis = analyze("refresh[if(time.gt(\"12:00\"), 1, 0)]")

        assertTrue(analysis.triggers[0].isRecurring)
    }

    // endregion

    // region Time parsing formats

    @Test
    fun `time with seconds`() {
        val analysis = analyze("refresh[if(time.gt(\"12:30:45\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertEquals(LocalTime.of(12, 30, 45), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    @Test
    fun `time without seconds uses zero seconds`() {
        val analysis = analyze("refresh[if(time.gt(\"12:30\"), 1, 0)]")

        assertTrue(analysis.success)
        assertEquals(1, analysis.triggers.size)
        assertEquals(LocalTime.of(12, 30, 0), (analysis.triggers[0] as DailyTimeTrigger).triggerTime)
    }

    // endregion

    // region DailyTimeTrigger behavior

    @Test
    fun `nextTriggerAfter for daily trigger - before trigger time`() {
        val trigger = DailyTimeTrigger(LocalTime.of(12, 0))
        val now = java.time.LocalDateTime.of(2026, 1, 15, 10, 0)

        val next = trigger.nextTriggerAfter(now)

        assertNotNull(next)
        assertEquals(java.time.LocalDateTime.of(2026, 1, 15, 12, 0), next)
    }

    @Test
    fun `nextTriggerAfter for daily trigger - after trigger time`() {
        val trigger = DailyTimeTrigger(LocalTime.of(12, 0))
        val now = java.time.LocalDateTime.of(2026, 1, 15, 14, 0)

        val next = trigger.nextTriggerAfter(now)

        assertNotNull(next)
        assertEquals(java.time.LocalDateTime.of(2026, 1, 16, 12, 0), next)
    }

    // endregion

    // region RefreshAnalysis companion functions

    @Test
    fun `success creates valid analysis`() {
        val triggers = listOf(DailyTimeTrigger(LocalTime.of(12, 0)))
        val analysis = RefreshAnalysis.success(triggers)

        assertTrue(analysis.success)
        assertNull(analysis.error)
        assertEquals(1, analysis.triggers.size)
    }

    @Test
    fun `error creates failed analysis`() {
        val analysis = RefreshAnalysis.error("test error")

        assertFalse(analysis.success)
        assertEquals("test error", analysis.error)
        assertTrue(analysis.triggers.isEmpty())
    }

    // endregion

    // region Nested expressions

    @Test
    fun `time comparison inside lambda`() {
        // Lambdas aren't executed during analysis, but we should still find comparisons
        val analysis = analyze("refresh[f: [time.gt(\"12:00\")]; if(f(0), 1, 0)]")

        assertTrue(analysis.success)
        // Should find the comparison inside the lambda
        assertTrue(analysis.triggers.isNotEmpty())
    }

    // endregion

    private fun analyze(code: String): RefreshAnalysis {
        val fullCode = "[$code]"
        val tokens = Lexer(fullCode).tokenize()
        val directive = Parser(tokens, fullCode).parseDirective()

        // Extract the RefreshExpr from the directive
        val refreshExpr = directive.expression as? RefreshExpr
            ?: throw IllegalArgumentException("Expected refresh expression, got ${directive.expression::class.simpleName}")

        return RefreshTriggerAnalyzer.analyze(refreshExpr, Environment())
    }

}

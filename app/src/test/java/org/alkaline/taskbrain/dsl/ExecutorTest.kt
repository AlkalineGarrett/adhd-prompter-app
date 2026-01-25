package org.alkaline.taskbrain.dsl

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ExecutorTest {

    private lateinit var executor: Executor

    @Before
    fun setUp() {
        executor = Executor()
    }

    private fun execute(source: String): DslValue {
        val tokens = Lexer(source).tokenize()
        val directive = Parser(tokens, source).parseDirective()
        return executor.execute(directive)
    }

    // region Number evaluation

    @Test
    fun `evaluates integer to NumberVal`() {
        val result = execute("[42]")

        assertTrue(result is NumberVal)
        assertEquals(42.0, (result as NumberVal).value, 0.0)
    }

    @Test
    fun `evaluates decimal to NumberVal`() {
        val result = execute("[3.14]")

        assertTrue(result is NumberVal)
        assertEquals(3.14, (result as NumberVal).value, 0.0)
    }

    @Test
    fun `evaluates zero`() {
        val result = execute("[0]")

        assertTrue(result is NumberVal)
        assertEquals(0.0, (result as NumberVal).value, 0.0)
    }

    // endregion

    // region String evaluation

    @Test
    fun `evaluates string to StringVal`() {
        val result = execute("[\"hello\"]")

        assertTrue(result is StringVal)
        assertEquals("hello", (result as StringVal).value)
    }

    @Test
    fun `evaluates empty string`() {
        val result = execute("[\"\"]")

        assertTrue(result is StringVal)
        assertEquals("", (result as StringVal).value)
    }

    @Test
    fun `evaluates string with special characters`() {
        val result = execute("[\"hello-world_123\"]")

        assertTrue(result is StringVal)
        assertEquals("hello-world_123", (result as StringVal).value)
    }

    // endregion

    // region Display string

    @Test
    fun `integer displays without decimal`() {
        val result = execute("[42]")
        assertEquals("42", result.toDisplayString())
    }

    @Test
    fun `decimal displays with decimal point`() {
        val result = execute("[3.14]")
        assertEquals("3.14", result.toDisplayString())
    }

    @Test
    fun `string displays its value`() {
        val result = execute("[\"hello\"]")
        assertEquals("hello", result.toDisplayString())
    }

    // endregion

    // region Serialization round-trip

    @Test
    fun `serializes and deserializes NumberVal`() {
        val original = NumberVal(42.0)
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serializes and deserializes decimal NumberVal`() {
        val original = NumberVal(3.14159)
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serializes and deserializes StringVal`() {
        val original = StringVal("hello world")
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serializes and deserializes empty StringVal`() {
        val original = StringVal("")
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serialization includes type name`() {
        val number = NumberVal(42.0)
        val string = StringVal("hello")

        assertEquals("number", number.serialize()["type"])
        assertEquals("string", string.serialize()["type"])
    }

    @Test
    fun `throws on unknown type during deserialization`() {
        val badMap = mapOf("type" to "unknown", "value" to "test")

        assertThrows(IllegalArgumentException::class.java) {
            DslValue.deserialize(badMap)
        }
    }

    @Test
    fun `throws on missing type during deserialization`() {
        val badMap = mapOf("value" to "test")

        assertThrows(IllegalArgumentException::class.java) {
            DslValue.deserialize(badMap)
        }
    }

    // endregion

    // region End-to-end

    @Test
    fun `end-to-end number parse execute serialize deserialize`() {
        // Parse and execute
        val result = execute("[42]")

        // Serialize
        val serialized = result.serialize()

        // Deserialize
        val restored = DslValue.deserialize(serialized)

        // Verify
        assertTrue(restored is NumberVal)
        assertEquals(42.0, (restored as NumberVal).value, 0.0)
        assertEquals("42", restored.toDisplayString())
    }

    @Test
    fun `end-to-end string parse execute serialize deserialize`() {
        // Parse and execute
        val result = execute("[\"hello world\"]")

        // Serialize
        val serialized = result.serialize()

        // Deserialize
        val restored = DslValue.deserialize(serialized)

        // Verify
        assertTrue(restored is StringVal)
        assertEquals("hello world", (restored as StringVal).value)
        assertEquals("hello world", restored.toDisplayString())
    }

    // endregion

    // region Date functions (Milestone 2)

    @Test
    fun `date returns today's date`() {
        val result = execute("[date]")

        assertTrue(result is DateVal)
        assertEquals(LocalDate.now(), (result as DateVal).value)
    }

    @Test
    fun `datetime returns current datetime`() {
        val before = LocalDateTime.now()
        val result = execute("[datetime]")
        val after = LocalDateTime.now()

        assertTrue(result is DateTimeVal)
        val dt = (result as DateTimeVal).value
        assertTrue(dt >= before && dt <= after)
    }

    @Test
    fun `time returns current time`() {
        val before = LocalTime.now()
        val result = execute("[time]")
        val after = LocalTime.now()

        assertTrue(result is TimeVal)
        val t = (result as TimeVal).value
        // Allow for second rollover
        assertTrue(t >= before.minusSeconds(1) && t <= after.plusSeconds(1))
    }

    // endregion

    // region Character constants (Milestone 2)

    @Test
    fun `qt returns quote character`() {
        val result = execute("[qt]")

        assertTrue(result is StringVal)
        assertEquals("\"", (result as StringVal).value)
    }

    @Test
    fun `nl returns newline character`() {
        val result = execute("[nl]")

        assertTrue(result is StringVal)
        assertEquals("\n", (result as StringVal).value)
    }

    @Test
    fun `tab returns tab character`() {
        val result = execute("[tab]")

        assertTrue(result is StringVal)
        assertEquals("\t", (result as StringVal).value)
    }

    @Test
    fun `ret returns carriage return character`() {
        val result = execute("[ret]")

        assertTrue(result is StringVal)
        assertEquals("\r", (result as StringVal).value)
    }

    // endregion

    // region Error handling (Milestone 2)

    @Test
    fun `throws on unknown function`() {
        val exception = assertThrows(ExecutionException::class.java) {
            execute("[unknown_func]")
        }
        assertTrue(exception.message!!.contains("Unknown function"))
        assertTrue(exception.message!!.contains("unknown_func"))
    }

    @Test
    fun `throws when date called with arguments`() {
        val exception = assertThrows(ExecutionException::class.java) {
            execute("[date 42]")
        }
        assertTrue(exception.message!!.contains("takes no arguments"))
    }

    // endregion

    // region Date value serialization (Milestone 2)

    @Test
    fun `serializes and deserializes DateVal`() {
        val original = DateVal(LocalDate.of(2026, 1, 25))
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serializes and deserializes TimeVal`() {
        val original = TimeVal(LocalTime.of(14, 30, 0))
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `serializes and deserializes DateTimeVal`() {
        val original = DateTimeVal(LocalDateTime.of(2026, 1, 25, 14, 30, 0))
        val serialized = original.serialize()
        val deserialized = DslValue.deserialize(serialized)

        assertEquals(original, deserialized)
    }

    @Test
    fun `DateVal displays as ISO date`() {
        val date = DateVal(LocalDate.of(2026, 1, 25))
        assertEquals("2026-01-25", date.toDisplayString())
    }

    @Test
    fun `TimeVal displays as ISO time`() {
        val time = TimeVal(LocalTime.of(14, 30, 0))
        assertEquals("14:30:00", time.toDisplayString())
    }

    @Test
    fun `DateTimeVal displays with comma separator`() {
        val datetime = DateTimeVal(LocalDateTime.of(2026, 1, 25, 14, 30, 0))
        assertEquals("2026-01-25, 14:30:00", datetime.toDisplayString())
    }

    // endregion

    // region End-to-end function calls (Milestone 2)

    @Test
    fun `end-to-end datetime`() {
        // Parse and execute
        val before = LocalDateTime.now()
        val result = execute("[datetime]")
        val after = LocalDateTime.now()

        // Serialize
        val serialized = result.serialize()

        // Deserialize
        val restored = DslValue.deserialize(serialized)

        // Verify
        assertTrue(restored is DateTimeVal)
        val dt = (restored as DateTimeVal).value
        assertTrue(dt >= before && dt <= after)
    }

    // endregion

    // region Dynamic function classification (Milestone 2)

    @Test
    fun `date is classified as dynamic`() {
        assertTrue(BuiltinRegistry.isDynamic("date"))
    }

    @Test
    fun `datetime is classified as dynamic`() {
        assertTrue(BuiltinRegistry.isDynamic("datetime"))
    }

    @Test
    fun `time is classified as dynamic`() {
        assertTrue(BuiltinRegistry.isDynamic("time"))
    }

    @Test
    fun `qt is classified as static`() {
        assertFalse(BuiltinRegistry.isDynamic("qt"))
    }

    @Test
    fun `unknown function is classified as static`() {
        assertFalse(BuiltinRegistry.isDynamic("unknown_func"))
    }

    @Test
    fun `expression with date contains dynamic calls`() {
        val directive = parse("[date]")
        assertTrue(BuiltinRegistry.containsDynamicCalls(directive.expression))
    }

    @Test
    fun `expression with datetime contains dynamic calls`() {
        val directive = parse("[datetime]")
        assertTrue(BuiltinRegistry.containsDynamicCalls(directive.expression))
    }

    @Test
    fun `expression with only static functions has no dynamic calls`() {
        val directive = parse("[qt]")
        assertFalse(BuiltinRegistry.containsDynamicCalls(directive.expression))
    }

    @Test
    fun `literal expression has no dynamic calls`() {
        val directive = parse("[42]")
        assertFalse(BuiltinRegistry.containsDynamicCalls(directive.expression))
    }

    @Test
    fun `string literal has no dynamic calls`() {
        val directive = parse("[\"hello\"]")
        assertFalse(BuiltinRegistry.containsDynamicCalls(directive.expression))
    }

    private fun parse(source: String): Directive {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens, source).parseDirective()
    }

    // endregion
}

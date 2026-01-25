package org.alkaline.taskbrain.dsl

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

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
}

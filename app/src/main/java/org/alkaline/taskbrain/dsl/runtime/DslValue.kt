package org.alkaline.taskbrain.dsl.runtime

import android.util.Log
import com.google.firebase.Timestamp
import org.alkaline.taskbrain.data.Note
import org.alkaline.taskbrain.dsl.language.CharClass
import org.alkaline.taskbrain.dsl.language.CharClassType
import org.alkaline.taskbrain.dsl.language.PatternElement
import org.alkaline.taskbrain.dsl.language.PatternLiteral
import org.alkaline.taskbrain.dsl.language.Quantified
import org.alkaline.taskbrain.dsl.language.Quantifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Runtime values in the DSL.
 * Each subclass represents a different type of value that can result from evaluation.
 *
 * Milestone 2: Adds DateVal, TimeVal, DateTimeVal.
 * Milestone 4: Adds PatternVal for pattern matching, BooleanVal for matches().
 * Milestone 5: Adds NoteVal and ListVal for find() function.
 */
sealed class DslValue {
    /**
     * Type identifier for serialization.
     */
    abstract val typeName: String

    /**
     * Convert to a display string for rendering results.
     */
    abstract fun toDisplayString(): String

    /**
     * Serialize to a Map for Firestore storage.
     */
    fun serialize(): Map<String, Any?> = mapOf(
        "type" to typeName,
        "value" to serializeValue()
    )

    protected abstract fun serializeValue(): Any?

    companion object {
        /**
         * Deserialize a DslValue from a Firestore map.
         * @throws IllegalArgumentException for unknown types
         */
        fun deserialize(map: Map<String, Any?>): DslValue {
            val type = map["type"] as? String
                ?: throw IllegalArgumentException("Missing 'type' field in serialized DslValue")
            val value = map["value"]

            return when (type) {
                "undefined" -> UndefinedVal
                "number" -> NumberVal((value as Number).toDouble())
                "string" -> StringVal(value as String)
                "boolean" -> BooleanVal(value as Boolean)
                "date" -> DateVal(LocalDate.parse(value as String))
                "time" -> TimeVal(LocalTime.parse(value as String))
                "datetime" -> DateTimeVal(LocalDateTime.parse(value as String))
                "pattern" -> {
                    // Patterns are stored as regex string; reconstruct PatternVal
                    val regexString = value as String
                    PatternVal.fromRegexString(regexString)
                }
                "note" -> {
                    @Suppress("UNCHECKED_CAST")
                    val noteMap = value as Map<String, Any?>
                    NoteVal.deserialize(noteMap)
                }
                "list" -> {
                    @Suppress("UNCHECKED_CAST")
                    val items = (value as List<Map<String, Any?>>).map { deserialize(it) }
                    ListVal(items)
                }
                else -> throw IllegalArgumentException("Unknown DslValue type: $type")
            }
        }
    }
}

/**
 * Represents an undefined value.
 * Returned when accessing non-existent data (e.g., out of bounds, missing property, hierarchy beyond depth).
 * Following the design principle: graceful undefined access instead of errors.
 *
 * Milestone 7.
 */
object UndefinedVal : DslValue() {
    override val typeName: String = "undefined"

    override fun toDisplayString(): String = "undefined"

    override fun serializeValue(): Any? = null
}

/**
 * A numeric value.
 */
data class NumberVal(val value: Double) : DslValue() {
    override val typeName: String = "number"

    override fun toDisplayString(): String {
        // Display integers without decimal point
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }

    override fun serializeValue(): Any = value
}

/**
 * A string value.
 */
data class StringVal(val value: String) : DslValue() {
    override val typeName: String = "string"

    override fun toDisplayString(): String = value

    override fun serializeValue(): Any = value
}

/**
 * A boolean value.
 * Milestone 4: Added for matches() function result.
 */
data class BooleanVal(val value: Boolean) : DslValue() {
    override val typeName: String = "boolean"

    override fun toDisplayString(): String = if (value) "true" else "false"

    override fun serializeValue(): Any = value
}

/**
 * A date value (year, month, day only).
 */
data class DateVal(val value: LocalDate) : DslValue() {
    override val typeName: String = "date"

    override fun toDisplayString(): String = value.format(DateTimeFormatter.ISO_LOCAL_DATE)

    override fun serializeValue(): Any = value.format(DateTimeFormatter.ISO_LOCAL_DATE)
}

/**
 * A time value (hour, minute, second only).
 */
data class TimeVal(val value: LocalTime) : DslValue() {
    override val typeName: String = "time"

    override fun toDisplayString(): String = value.format(DISPLAY_TIME_FORMAT)

    override fun serializeValue(): Any = value.format(DateTimeFormatter.ISO_LOCAL_TIME)

    companion object {
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

/**
 * A datetime value (date + time).
 */
data class DateTimeVal(val value: LocalDateTime) : DslValue() {
    override val typeName: String = "datetime"

    override fun toDisplayString(): String = value.format(DISPLAY_DATETIME_FORMAT)

    override fun serializeValue(): Any = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    companion object {
        // Display as "2026-01-25, 14:30:00" instead of "2026-01-25T14:30:00"
        private val DISPLAY_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm:ss")
    }
}

/**
 * A pattern value for matching strings.
 * Created by the pattern(...) function.
 *
 * Milestone 4.
 *
 * @property elements The parsed pattern elements (for display and debugging)
 * @property compiledRegex The compiled regex for matching
 */
data class PatternVal(
    val elements: List<PatternElement>,
    val compiledRegex: Regex
) : DslValue() {
    override val typeName: String = "pattern"

    override fun toDisplayString(): String = "pattern(${compiledRegex.pattern})"

    override fun serializeValue(): Any = compiledRegex.pattern

    /**
     * Check if a string matches this pattern.
     */
    fun matches(input: String): Boolean = compiledRegex.matches(input)

    /**
     * Find all matches of this pattern in a string.
     */
    fun findAll(input: String): Sequence<MatchResult> = compiledRegex.findAll(input)

    companion object {
        /**
         * Compile pattern elements into a PatternVal.
         */
        fun compile(elements: List<PatternElement>): PatternVal {
            val regexPattern = elements.joinToString("") { elementToRegex(it) }
            return PatternVal(elements, Regex(regexPattern))
        }

        /**
         * Create a PatternVal from a regex string (for deserialization).
         * Note: We lose the original element structure, but retain matching functionality.
         */
        fun fromRegexString(regexString: String): PatternVal {
            return PatternVal(emptyList(), Regex(regexString))
        }

        /**
         * Convert a pattern element to its regex equivalent.
         */
        private fun elementToRegex(element: PatternElement): String = when (element) {
            is CharClass -> charClassToRegex(element.type)
            is PatternLiteral -> Regex.escape(element.value)
            is Quantified -> elementToRegex(element.element) + quantifierToRegex(element.quantifier)
        }

        /**
         * Convert a character class type to its regex equivalent.
         */
        private fun charClassToRegex(type: CharClassType): String = when (type) {
            CharClassType.DIGIT -> "\\d"
            CharClassType.LETTER -> "[a-zA-Z]"
            CharClassType.SPACE -> "\\s"
            CharClassType.PUNCT -> "[\\p{Punct}]"
            CharClassType.ANY -> "."
        }

        /**
         * Convert a quantifier to its regex equivalent.
         */
        private fun quantifierToRegex(quantifier: Quantifier): String = when (quantifier) {
            is Quantifier.Exact -> "{${quantifier.n}}"
            is Quantifier.Any -> "*"
            is Quantifier.Range -> {
                if (quantifier.max == null) {
                    "{${quantifier.min},}"
                } else {
                    "{${quantifier.min},${quantifier.max}}"
                }
            }
        }
    }
}

/**
 * A note value, wrapping a Note object from the data layer.
 * Created by the find() function or note references.
 *
 * Milestone 5: Basic NoteVal with serialization.
 * Milestone 6: Adds property access for [.path], [.created], etc.
 * Milestone 7: Adds property setting and method calls (.append).
 */
data class NoteVal(val note: Note) : DslValue() {
    override val typeName: String = "note"

    override fun toDisplayString(): String {
        // Display the path if set, otherwise the first line of content, otherwise the id
        return when {
            note.path.isNotEmpty() -> note.path
            note.content.isNotEmpty() -> note.content.lines().firstOrNull() ?: note.id
            else -> note.id
        }
    }

    override fun serializeValue(): Any = mapOf(
        "id" to note.id,
        "userId" to note.userId,
        "path" to note.path,
        "content" to note.content,
        "createdAt" to note.createdAt?.toDate()?.time,
        "updatedAt" to note.updatedAt?.toDate()?.time,
        "lastAccessedAt" to note.lastAccessedAt?.toDate()?.time
    )

    /**
     * Get a property value from the note.
     *
     * Supported properties:
     * - `id`: String - Firebase ID
     * - `path`: String - Unique path identifier
     * - `name`: String - First line of content
     * - `created`: DateTime - Creation timestamp
     * - `modified`: DateTime - Last modification timestamp
     * - `viewed`: DateTime - Last viewed timestamp
     * - `up`: Note - Parent note (0-arg form, returns parent; use callMethod for up(n))
     * - `root`: Note - Root ancestor (top-level note with no parent)
     *
     * Milestone 6: Basic properties.
     * Milestone 7: Added up and root for hierarchy navigation.
     */
    fun getProperty(property: String, env: Environment): DslValue {
        return when (property) {
            "id" -> StringVal(note.id)
            "path" -> StringVal(note.path)
            "name" -> StringVal(note.content.lines().firstOrNull() ?: "")
            "created" -> note.createdAt?.let {
                DateTimeVal(it.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            } ?: throw ExecutionException("Note has no created date")
            "modified" -> note.updatedAt?.let {
                DateTimeVal(it.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            } ?: throw ExecutionException("Note has no modified date")
            "viewed" -> note.lastAccessedAt?.let {
                DateTimeVal(it.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime())
            } ?: throw ExecutionException("Note has no viewed date")
            "up" -> getUp(1, env)
            "root" -> getRoot(env)
            else -> throw ExecutionException("Unknown property '$property' on note")
        }
    }

    /**
     * Navigate up the note hierarchy by the specified number of levels.
     * Returns UndefinedVal if the requested ancestor doesn't exist.
     *
     * Milestone 7.
     */
    private fun getUp(levels: Int, env: Environment): DslValue {
        if (levels == 0) return this
        val parent = env.getParentNote(note) ?: return UndefinedVal
        return if (levels == 1) {
            NoteVal(parent)
        } else {
            NoteVal(parent).getUp(levels - 1, env)
        }
    }

    /**
     * Navigate to the root ancestor (top-level note with no parent).
     * If this note has no parent, returns itself.
     *
     * Milestone 7.
     */
    private fun getRoot(env: Environment): NoteVal {
        val parent = env.getParentNote(note) ?: return this
        return NoteVal(parent).getRoot(env)
    }

    /**
     * Set a property value on the note.
     *
     * Writable properties:
     * - `path`: String - Unique path identifier
     * - `name`: String - First line of content (updates content)
     *
     * Note: This requires NoteOperations to be available in the environment.
     * The operation is performed synchronously using runBlocking.
     *
     * Milestone 7.
     */
    fun setProperty(property: String, value: DslValue, env: Environment) {
        val ops = env.getNoteOperations()
            ?: throw ExecutionException(
                "Cannot modify note properties: note operations not available"
            )

        when (property) {
            "path" -> {
                val newPath = (value as? StringVal)?.value
                    ?: throw ExecutionException("path must be a string")
                val updatedNote = kotlinx.coroutines.runBlocking {
                    ops.updatePath(note.id, newPath)
                }
                env.registerMutation(NoteMutation(note.id, updatedNote, MutationType.PATH_CHANGED))
            }
            "name" -> {
                val newName = (value as? StringVal)?.value
                    ?: throw ExecutionException("name must be a string")
                // Fetch fresh content from Firestore to avoid stale cache issues
                val freshNote = kotlinx.coroutines.runBlocking {
                    ops.getNoteById(note.id)
                } ?: throw ExecutionException("Note not found: ${note.id}")
                // Update the first line of content while preserving the rest
                val lines = freshNote.content.lines()
                val newContent = if (lines.isEmpty() || lines.size == 1) {
                    newName
                } else {
                    (listOf(newName) + lines.drop(1)).joinToString("\n")
                }
                val updatedNote = kotlinx.coroutines.runBlocking {
                    ops.updateContent(note.id, newContent)
                }
                env.registerMutation(NoteMutation(note.id, updatedNote, MutationType.CONTENT_CHANGED))
            }
            "id", "created", "modified", "viewed" -> {
                throw ExecutionException("Cannot set read-only property '$property' on note")
            }
            else -> throw ExecutionException("Unknown property '$property' on note")
        }
    }

    /**
     * Call a method on the note.
     *
     * Supported methods:
     * - `append(text)`: Append text to the note's content
     * - `up()`: Returns parent note (1 level up); same as .up property
     * - `up(n)`: Returns ancestor n levels up; returns undefined if exceeds hierarchy
     *
     * Milestone 7.
     */
    fun callMethod(
        methodName: String,
        args: Arguments,
        env: Environment,
        position: Int
    ): DslValue {
        return when (methodName) {
            "up" -> {
                // up() with no args defaults to 1 level (parent)
                // up(n) goes up n levels
                val levels = if (args.positional.isEmpty()) {
                    1
                } else {
                    val arg = args.require(0, "levels")
                    (arg as? NumberVal)?.value?.toInt()
                        ?: throw ExecutionException("'up' expects a number argument", position)
                }
                getUp(levels, env)
            }
            "append" -> {
                val text = args.require(0, "text")
                val textStr = (text as? StringVal)?.value
                    ?: throw ExecutionException("'append' expects a string argument", position)

                val ops = env.getNoteOperations()
                    ?: throw ExecutionException(
                        "Cannot append to note: note operations not available",
                        position
                    )

                val updatedNote = kotlinx.coroutines.runBlocking {
                    ops.appendToNote(note.id, textStr)
                }
                env.registerMutation(NoteMutation(note.id, updatedNote, MutationType.CONTENT_APPENDED))
                NoteVal(updatedNote)
            }
            else -> throw ExecutionException(
                "Unknown method '$methodName' on note",
                position
            )
        }
    }

    companion object {
        /**
         * Deserialize a NoteVal from a Firestore map.
         */
        fun deserialize(map: Map<String, Any?>): NoteVal {
            val createdAtMillis = map["createdAt"] as? Long
            val updatedAtMillis = map["updatedAt"] as? Long
            val lastAccessedAtMillis = map["lastAccessedAt"] as? Long

            val note = Note(
                id = map["id"] as? String ?: "",
                userId = map["userId"] as? String ?: "",
                path = map["path"] as? String ?: "",
                content = map["content"] as? String ?: "",
                createdAt = createdAtMillis?.let { Timestamp(java.util.Date(it)) },
                updatedAt = updatedAtMillis?.let { Timestamp(java.util.Date(it)) },
                lastAccessedAt = lastAccessedAtMillis?.let { Timestamp(java.util.Date(it)) }
            )
            return NoteVal(note)
        }
    }
}

/**
 * A list value containing other DslValues.
 * Created by the find() function, list() function, etc.
 *
 * Milestone 5.
 */
data class ListVal(val items: List<DslValue>) : DslValue() {
    override val typeName: String = "list"

    override fun toDisplayString(): String {
        if (items.isEmpty()) return "[]"
        return "[${items.joinToString(", ") { it.toDisplayString() }}]"
    }

    override fun serializeValue(): Any = items.map { it.serialize() }

    /** Number of items in the list. */
    val size: Int get() = items.size

    /** Check if the list is empty. */
    fun isEmpty(): Boolean = items.isEmpty()

    /** Check if the list is not empty. */
    fun isNotEmpty(): Boolean = items.isNotEmpty()

    /** Get an item by index, or null if out of bounds. */
    operator fun get(index: Int): DslValue? = items.getOrNull(index)
}

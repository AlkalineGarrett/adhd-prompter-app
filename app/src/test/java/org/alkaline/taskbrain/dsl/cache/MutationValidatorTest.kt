package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.dsl.language.*
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for MutationValidator.
 * Phase 9: Mutation handling for caching.
 *
 * Tests verify:
 * - Bare mutations (new, maybe_new, .append) are rejected at top level
 * - Mutations inside button/schedule are allowed
 * - Bare time values (date, time, datetime) are rejected at top level
 * - Time values inside once/refresh are allowed
 * - Multi-statement directives validate all statements
 */
class MutationValidatorTest {

    private fun parse(source: String): Expression {
        val tokens = Lexer(source).tokenize()
        return Parser(tokens, source).parseDirective().expression
    }

    private fun validate(source: String): MutationValidator.ValidationResult {
        return MutationValidator.validate(parse(source))
    }

    private fun isValid(source: String): Boolean {
        return validate(source).isValid()
    }

    // region Pure computations are valid

    @Test
    fun `number literal is valid`() {
        assertTrue(isValid("[42]"))
    }

    @Test
    fun `string literal is valid`() {
        assertTrue(isValid("[\"hello\"]"))
    }

    @Test
    fun `function call is valid`() {
        assertTrue(isValid("[add(1, 2)]"))
    }

    @Test
    fun `find function is valid`() {
        assertTrue(isValid("[find(path: \"inbox\")]"))
    }

    @Test
    fun `view function is valid`() {
        assertTrue(isValid("[view(find(path: \"inbox\"))]"))
    }

    @Test
    fun `property access is valid`() {
        assertTrue(isValid("[.path]"))
    }

    @Test
    fun `pattern is valid`() {
        assertTrue(isValid("[pattern(digit*4)]"))
    }

    @Test
    fun `variable assignment with pure value is valid`() {
        assertTrue(isValid("[x: 42]"))
    }

    @Test
    fun `statement list with pure computations is valid`() {
        assertTrue(isValid("[x: 5; y: 10; add(x, y)]"))
    }

    // endregion

    // region Bare mutations are rejected

    @Test
    fun `bare new is rejected`() {
        assertFalse(isValid("[new(path: \"inbox/todo\")]"))
        val result = validate("[new(path: \"inbox/todo\")]")
        assertTrue(result is MutationValidator.ValidationResult.BareMutation)
        assertTrue(result.errorMessage()?.contains("new()") == true)
        assertTrue(result.errorMessage()?.contains("button") == true)
    }

    @Test
    fun `bare maybe_new is rejected`() {
        assertFalse(isValid("[maybe_new(path: \"config\")]"))
        val result = validate("[maybe_new(path: \"config\")]")
        assertTrue(result is MutationValidator.ValidationResult.BareMutation)
        assertTrue(result.errorMessage()?.contains("maybe_new()") == true)
    }

    @Test
    fun `bare append is rejected`() {
        assertFalse(isValid("[.append(\"text\")]"))
        val result = validate("[.append(\"text\")]")
        assertTrue(result is MutationValidator.ValidationResult.BareMutation)
        assertTrue(result.errorMessage()?.contains(".append()") == true)
    }

    @Test
    fun `mutation in variable assignment is rejected`() {
        assertFalse(isValid("[note: new(path: \"test\")]"))
    }

    @Test
    fun `mutation inside once is rejected`() {
        assertFalse(isValid("[once[new(path: \"test\")]]"))
    }

    @Test
    fun `mutation inside refresh is rejected`() {
        assertFalse(isValid("[refresh[new(path: \"test\")]]"))
    }

    @Test
    fun `chained append is rejected`() {
        assertFalse(isValid("[.root.append(\"text\")]"))
    }

    // endregion

    // region Mutations inside button are allowed

    @Test
    fun `new inside button is valid`() {
        assertTrue(isValid("[button(\"Create\", [new(path: \"inbox/todo\")])]"))
    }

    @Test
    fun `maybe_new inside button is valid`() {
        assertTrue(isValid("[button(\"Setup\", [maybe_new(path: \"config\")])]"))
    }

    @Test
    fun `append inside button is valid`() {
        assertTrue(isValid("[button(\"Add\", [.append(\"text\")])]"))
    }

    @Test
    fun `complex mutation inside button is valid`() {
        assertTrue(isValid("[button(\"Setup\", [note: maybe_new(path: \"config\"); note.name])]"))
    }

    @Test
    fun `nested function with mutation inside button is valid`() {
        assertTrue(isValid("[button(\"Process\", [notes: find(path: \"inbox\"); first(notes).append(\"done\")])]"))
    }

    // endregion

    // region Mutations inside schedule are allowed

    @Test
    fun `new inside schedule is valid`() {
        assertTrue(isValid("[schedule(daily, [new(path: \"inbox/daily\")])]"))
    }

    @Test
    fun `maybe_new inside schedule is valid`() {
        assertTrue(isValid("[schedule(weekly, [maybe_new(path: \"reports/weekly\")])]"))
    }

    @Test
    fun `append inside schedule is valid`() {
        assertTrue(isValid("[schedule(hourly, [.append(date)])]"))
    }

    // endregion

    // region Bare time values are rejected

    @Test
    fun `bare date is rejected`() {
        assertFalse(isValid("[date]"))
        val result = validate("[date]")
        assertTrue(result is MutationValidator.ValidationResult.BareTimeValue)
        assertTrue(result.errorMessage()?.contains("date()") == true)
        assertTrue(result.errorMessage()?.contains("once") == true)
    }

    @Test
    fun `bare time is rejected`() {
        assertFalse(isValid("[time]"))
        val result = validate("[time]")
        assertTrue(result is MutationValidator.ValidationResult.BareTimeValue)
    }

    @Test
    fun `bare datetime is rejected`() {
        assertFalse(isValid("[datetime]"))
        val result = validate("[datetime]")
        assertTrue(result is MutationValidator.ValidationResult.BareTimeValue)
    }

    @Test
    fun `time value in assignment is rejected`() {
        assertFalse(isValid("[today: date]"))
    }

    @Test
    fun `time value in function argument is rejected`() {
        assertFalse(isValid("[find(where: [i.created.gt(date)])]"))
    }

    // endregion

    // region Time values inside once are allowed

    @Test
    fun `date inside once is valid`() {
        assertTrue(isValid("[once[date]]"))
    }

    @Test
    fun `time inside once is valid`() {
        assertTrue(isValid("[once[time]]"))
    }

    @Test
    fun `datetime inside once is valid`() {
        assertTrue(isValid("[once[datetime]]"))
    }

    @Test
    fun `complex expression with date inside once is valid`() {
        assertTrue(isValid("[once[today: date; find(where: [i.created.gt(today)])]]"))
    }

    // endregion

    // region Time values inside refresh are allowed

    @Test
    fun `date inside refresh is valid`() {
        assertTrue(isValid("[refresh[date]]"))
    }

    @Test
    fun `complex expression with date inside refresh is valid`() {
        assertTrue(isValid("[refresh[today: date; find(where: [i.created.gt(today)])]]"))
    }

    // endregion

    // region Time values inside button action are allowed

    @Test
    fun `date inside button action is valid`() {
        // Time values inside button are allowed because the action
        // is executed at button click time
        assertTrue(isValid("[button(\"Log\", [.append(date)])]"))
    }

    // endregion

    // region Multi-statement directive validation

    @Test
    fun `all valid statements makes directive valid`() {
        assertTrue(isValid("[x: 5; y: 10; add(x, y)]"))
    }

    @Test
    fun `mutation in any statement makes directive invalid`() {
        assertFalse(isValid("[x: 5; new(path: \"test\"); y: 10]"))
    }

    @Test
    fun `time value in any statement makes directive invalid`() {
        assertFalse(isValid("[today: date; find(path: \"inbox\")]"))
    }

    @Test
    fun `mutation in first statement makes whole directive invalid`() {
        assertFalse(isValid("[new(path: \"test\"); x: 5]"))
    }

    @Test
    fun `mutation in last statement makes whole directive invalid`() {
        assertFalse(isValid("[x: 5; new(path: \"test\")]"))
    }

    // endregion

    // region containsMutations helper

    @Test
    fun `containsMutations detects new`() {
        assertTrue(MutationValidator.containsMutations(parse("[new(path: \"test\")]")))
    }

    @Test
    fun `containsMutations detects maybe_new`() {
        assertTrue(MutationValidator.containsMutations(parse("[maybe_new(path: \"test\")]")))
    }

    @Test
    fun `containsMutations detects append`() {
        assertTrue(MutationValidator.containsMutations(parse("[.append(\"text\")]")))
    }

    @Test
    fun `containsMutations detects button wrapper`() {
        assertTrue(MutationValidator.containsMutations(parse("[button(\"Add\", [new(path: \"test\")])]")))
    }

    @Test
    fun `containsMutations returns false for pure computation`() {
        assertFalse(MutationValidator.containsMutations(parse("[add(1, 2)]")))
    }

    @Test
    fun `containsMutations returns false for find`() {
        assertFalse(MutationValidator.containsMutations(parse("[find(path: \"inbox\")]")))
    }

    // endregion

    // region containsUnwrappedTimeValues helper

    @Test
    fun `containsUnwrappedTimeValues detects bare date`() {
        assertTrue(MutationValidator.containsUnwrappedTimeValues(parse("[date]")))
    }

    @Test
    fun `containsUnwrappedTimeValues detects bare time`() {
        assertTrue(MutationValidator.containsUnwrappedTimeValues(parse("[time]")))
    }

    @Test
    fun `containsUnwrappedTimeValues returns false for date inside once`() {
        assertFalse(MutationValidator.containsUnwrappedTimeValues(parse("[once[date]]")))
    }

    @Test
    fun `containsUnwrappedTimeValues returns false for date inside refresh`() {
        assertFalse(MutationValidator.containsUnwrappedTimeValues(parse("[refresh[date]]")))
    }

    @Test
    fun `containsUnwrappedTimeValues returns false for pure computation`() {
        assertFalse(MutationValidator.containsUnwrappedTimeValues(parse("[add(1, 2)]")))
    }

    // endregion

    // region Integration scenarios from plan

    @Test
    fun `scenario - find result used with mutation requires button`() {
        // ERROR: bare mutation in second statement
        assertFalse(isValid("[notes: find(path: \"inbox\"); first(notes).append(\"done\")]"))
    }

    @Test
    fun `scenario - find result with mutation inside button is valid`() {
        // OK: mutation inside button
        assertTrue(isValid("[button(\"Process\", [notes: find(path: \"inbox\"); first(notes).append(\"done\")])]"))
    }

    @Test
    fun `scenario - bare time value in filter is rejected`() {
        // ERROR: bare time value in first statement
        assertFalse(isValid("[today: date; find(where: [i.created.gt(today)])]"))
    }

    @Test
    fun `scenario - time value in refresh wrapper is valid`() {
        // OK: time value inside refresh wrapper
        assertTrue(isValid("[refresh[today: date; find(where: [i.created.gt(today)])]]"))
    }

    @Test
    fun `scenario - cacheable directive with find is valid`() {
        // Pure computation - valid and cacheable
        assertTrue(isValid("[a: find(path: \"inbox\"); b: find(path: \"archive\"); list(a, b)]"))
    }

    // endregion
}

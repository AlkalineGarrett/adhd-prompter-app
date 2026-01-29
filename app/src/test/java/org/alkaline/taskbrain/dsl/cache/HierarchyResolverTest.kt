package org.alkaline.taskbrain.dsl.cache

import org.alkaline.taskbrain.data.Note
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for HierarchyResolver.
 * Phase 1: Data structures and hashing infrastructure.
 */
class HierarchyResolverTest {

    // region findParent

    @Test
    fun `findParent returns parent note`() {
        val parent = createNote("1", "inbox")
        val child = createNote("2", "inbox/tasks")
        val allNotes = listOf(parent, child)

        val result = HierarchyResolver.findParent(child, allNotes)

        assertEquals(parent, result)
    }

    @Test
    fun `findParent returns null for root note`() {
        val root = createNote("1", "inbox")
        val allNotes = listOf(root)

        val result = HierarchyResolver.findParent(root, allNotes)

        assertNull(result)
    }

    @Test
    fun `findParent returns null when parent not found`() {
        val orphan = createNote("1", "inbox/tasks")
        val allNotes = listOf(orphan) // No parent note exists

        val result = HierarchyResolver.findParent(orphan, allNotes)

        assertNull(result)
    }

    @Test
    fun `findParent handles deep nesting`() {
        val grandparent = createNote("1", "inbox")
        val parent = createNote("2", "inbox/tasks")
        val child = createNote("3", "inbox/tasks/urgent")
        val allNotes = listOf(grandparent, parent, child)

        val result = HierarchyResolver.findParent(child, allNotes)

        assertEquals(parent, result)
    }

    // endregion

    // region findAncestor

    @Test
    fun `findAncestor with levels=1 returns parent`() {
        val parent = createNote("1", "inbox")
        val child = createNote("2", "inbox/tasks")
        val allNotes = listOf(parent, child)

        val result = HierarchyResolver.findAncestor(child, 1, allNotes)

        assertEquals(parent, result)
    }

    @Test
    fun `findAncestor with levels=2 returns grandparent`() {
        val grandparent = createNote("1", "inbox")
        val parent = createNote("2", "inbox/tasks")
        val child = createNote("3", "inbox/tasks/urgent")
        val allNotes = listOf(grandparent, parent, child)

        val result = HierarchyResolver.findAncestor(child, 2, allNotes)

        assertEquals(grandparent, result)
    }

    @Test
    fun `findAncestor returns null when not enough levels`() {
        val parent = createNote("1", "inbox")
        val child = createNote("2", "inbox/tasks")
        val allNotes = listOf(parent, child)

        // Child only has 1 level of ancestry
        val result = HierarchyResolver.findAncestor(child, 3, allNotes)

        assertNull(result)
    }

    // endregion

    // region findRoot

    @Test
    fun `findRoot returns root ancestor`() {
        val root = createNote("1", "inbox")
        val child = createNote("2", "inbox/tasks")
        val grandchild = createNote("3", "inbox/tasks/urgent")
        val allNotes = listOf(root, child, grandchild)

        val result = HierarchyResolver.findRoot(grandchild, allNotes)

        assertEquals(root, result)
    }

    @Test
    fun `findRoot returns self when already at root`() {
        val root = createNote("1", "inbox")
        val allNotes = listOf(root)

        val result = HierarchyResolver.findRoot(root, allNotes)

        assertEquals(root, result)
    }

    @Test
    fun `findRoot returns null when root note not found`() {
        val orphan = createNote("1", "inbox/tasks/urgent")
        val allNotes = listOf(orphan) // No "inbox" note exists

        val result = HierarchyResolver.findRoot(orphan, allNotes)

        assertNull(result)
    }

    // endregion

    // region resolve

    @Test
    fun `resolve Up path`() {
        val parent = createNote("1", "inbox")
        val child = createNote("2", "inbox/tasks")
        val allNotes = listOf(parent, child)

        val result = HierarchyResolver.resolve(HierarchyPath.Up, child, allNotes)

        assertEquals(parent, result)
    }

    @Test
    fun `resolve UpN path`() {
        val grandparent = createNote("1", "inbox")
        val parent = createNote("2", "inbox/tasks")
        val child = createNote("3", "inbox/tasks/urgent")
        val allNotes = listOf(grandparent, parent, child)

        val result = HierarchyResolver.resolve(HierarchyPath.UpN(2), child, allNotes)

        assertEquals(grandparent, result)
    }

    @Test
    fun `resolve Root path`() {
        val root = createNote("1", "inbox")
        val child = createNote("2", "inbox/tasks")
        val grandchild = createNote("3", "inbox/tasks/urgent")
        val allNotes = listOf(root, child, grandchild)

        val result = HierarchyResolver.resolve(HierarchyPath.Root, grandchild, allNotes)

        assertEquals(root, result)
    }

    // endregion

    // region Edge cases

    @Test
    fun `handles empty path`() {
        val note = createNote("1", "")
        val allNotes = listOf(note)

        val parent = HierarchyResolver.findParent(note, allNotes)
        val root = HierarchyResolver.findRoot(note, allNotes)

        assertNull(parent)
        assertNull(root)
    }

    @Test
    fun `handles path with multiple slashes`() {
        val root = createNote("1", "a")
        val level1 = createNote("2", "a/b")
        val level2 = createNote("3", "a/b/c")
        val level3 = createNote("4", "a/b/c/d")
        val allNotes = listOf(root, level1, level2, level3)

        assertEquals(level2, HierarchyResolver.findParent(level3, allNotes))
        assertEquals(level1, HierarchyResolver.findAncestor(level3, 2, allNotes))
        assertEquals(root, HierarchyResolver.findRoot(level3, allNotes))
    }

    // endregion

    private fun createNote(id: String, path: String) = Note(
        id = id,
        path = path
    )
}

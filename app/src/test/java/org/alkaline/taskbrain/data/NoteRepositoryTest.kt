package org.alkaline.taskbrain.data

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NoteRepositoryTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockCollection: CollectionReference
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        mockFirestore = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockCollection = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockk { every { uid } returns USER_ID }
        every { mockFirestore.collection("notes") } returns mockCollection

        repository = NoteRepository(mockFirestore, mockAuth)
    }

    private fun signOut() {
        every { mockAuth.currentUser } returns null
    }

    private fun mockDocument(noteId: String, note: Note?): DocumentReference {
        val ref = mockk<DocumentReference>()
        val snapshot = mockk<DocumentSnapshot>()
        every { mockCollection.document(noteId) } returns ref
        every { ref.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns (note != null)
        every { snapshot.toObject(Note::class.java) } returns note
        return ref
    }

    private fun mockTransaction(
        parentRef: DocumentReference,
        existingChildIds: List<String> = emptyList()
    ) {
        every { mockFirestore.runTransaction<Map<Int, String>>(any()) } answers {
            val function = firstArg<Transaction.Function<Map<Int, String>>>()
            val transaction = mockk<Transaction>(relaxed = true)
            val snapshot = mockk<DocumentSnapshot>()
            every { transaction.get(parentRef) } returns snapshot
            every { snapshot.exists() } returns existingChildIds.isNotEmpty()
            every { snapshot.get("containedNotes") } returns existingChildIds
            Tasks.forResult(function.apply(transaction))
        }
    }

    // region Auth Tests

    @Test
    fun `all methods should fail when user is not signed in`() = runTest {
        signOut()

        val results = listOf(
            repository.loadNoteWithChildren("note_1"),
            repository.saveNoteWithChildren("note_1", listOf(NoteLine("Content", "note_1"))),
            repository.loadUserNotes(),
            repository.createNote(),
            repository.createMultiLineNote("Line 1\nLine 2")
        )

        results.forEach { result ->
            assertTrue(result.isFailure)
            assertEquals("User not signed in", result.exceptionOrNull()?.message)
        }
    }

    // endregion

    // region Load Tests

    @Test
    fun `loadNoteWithChildren returns empty line when document does not exist`() = runTest {
        mockDocument("note_1", null)

        val lines = repository.loadNoteWithChildren("note_1").getOrThrow()

        assertEquals(listOf(NoteLine("", "note_1")), lines)
    }

    @Test
    fun `loadNoteWithChildren returns parent content`() = runTest {
        mockDocument("note_1", Note(content = "Parent content", containedNotes = emptyList()))

        val lines = repository.loadNoteWithChildren("note_1").getOrThrow()

        assertEquals(1, lines.size)
        assertEquals("Parent content", lines[0].content)
        assertEquals("note_1", lines[0].noteId)
    }

    @Test
    fun `loadNoteWithChildren returns parent and children in order`() = runTest {
        mockDocument("parent", Note(content = "Parent", containedNotes = listOf("child_1", "child_2")))
        mockDocument("child_1", Note(content = "Child 1"))
        mockDocument("child_2", Note(content = "Child 2"))

        val lines = repository.loadNoteWithChildren("parent").getOrThrow()

        assertEquals(
            listOf(
                NoteLine("Parent", "parent"),
                NoteLine("Child 1", "child_1"),
                NoteLine("Child 2", "child_2")
            ),
            lines
        )
    }

    @Test
    fun `loadNoteWithChildren treats empty child IDs as spacers`() = runTest {
        mockDocument("parent", Note(content = "Parent", containedNotes = listOf("", "child_1", "")))
        mockDocument("child_1", Note(content = "Child"))

        val lines = repository.loadNoteWithChildren("parent").getOrThrow()

        assertEquals(4, lines.size)
        assertEquals("", lines[1].content)
        assertNull(lines[1].noteId)
        assertEquals("Child", lines[2].content)
        assertEquals("", lines[3].content)
    }

    @Test
    fun `loadUserNotes filters out children and deleted notes`() = runTest {
        val mockQuery = mockk<Query>()
        val docs = listOf(
            mockk<QueryDocumentSnapshot> {
                every { id } returns "note_1"
                every { toObject(Note::class.java) } returns Note(id = "note_1")
            },
            mockk<QueryDocumentSnapshot> {
                every { id } returns "note_2"
                every { toObject(Note::class.java) } returns Note(id = "note_2", state = "deleted")
            },
            mockk<QueryDocumentSnapshot> {
                every { id } returns "note_3"
                every { toObject(Note::class.java) } returns Note(id = "note_3", parentNoteId = "parent")
            }
        )
        every { mockCollection.whereEqualTo("userId", USER_ID) } returns mockQuery
        every { mockQuery.get() } returns Tasks.forResult(mockk {
            every { iterator() } returns docs.toMutableList().iterator()
        })

        val notes = repository.loadUserNotes().getOrThrow()

        assertEquals(1, notes.size)
        assertEquals("note_1", notes[0].id)
    }

    // endregion

    // region Save Tests

    @Test
    fun `saveNoteWithChildren saves parent content`() = runTest {
        val parentRef = mockDocument("note_1", null)
        mockTransaction(parentRef)

        val result = repository.saveNoteWithChildren("note_1", listOf(NoteLine("Content", "note_1")))

        assertTrue(result.isSuccess)
        verify { mockFirestore.runTransaction<Map<Int, String>>(any()) }
    }

    @Test
    fun `saveNoteWithChildren creates new child notes and returns their IDs`() = runTest {
        val parentRef = mockDocument("note_1", null)
        val childRef = mockk<DocumentReference> { every { id } returns "new_child" }
        every { mockCollection.document() } returns childRef
        mockTransaction(parentRef)

        val result = repository.saveNoteWithChildren(
            "note_1",
            listOf(NoteLine("Parent", "note_1"), NoteLine("New child", null))
        ).getOrThrow()

        assertEquals("new_child", result[1])
    }

    @Test
    fun `saveNoteWithChildren updates existing child notes`() = runTest {
        val parentRef = mockDocument("note_1", null)
        mockDocument("child_1", null)
        mockTransaction(parentRef, listOf("child_1"))

        val result = repository.saveNoteWithChildren(
            "note_1",
            listOf(NoteLine("Parent", "note_1"), NoteLine("Updated", "child_1"))
        )

        assertTrue(result.isSuccess)
    }

    @Test
    fun `saveNoteWithChildren soft-deletes removed children`() = runTest {
        val parentRef = mockDocument("note_1", null)
        mockDocument("old_child", null)
        mockTransaction(parentRef, listOf("old_child"))

        val result = repository.saveNoteWithChildren(
            "note_1",
            listOf(NoteLine("Parent only", "note_1"))
        )

        assertTrue(result.isSuccess)
    }

    // endregion

    // region Create Tests

    @Test
    fun `createNote returns new note ID`() = runTest {
        val newRef = mockk<DocumentReference> { every { id } returns "new_note_id" }
        every { mockCollection.add(any<Map<String, Any?>>()) } returns Tasks.forResult(newRef)

        val noteId = repository.createNote().getOrThrow()

        assertEquals("new_note_id", noteId)
    }

    @Test
    fun `createMultiLineNote creates parent with children`() = runTest {
        val refs = listOf("parent_id", "child_1", "child_2").map { id ->
            mockk<DocumentReference> { every { this@mockk.id } returns id }
        }
        every { mockCollection.document() } returnsMany refs
        val batch = mockk<WriteBatch>(relaxed = true) {
            every { commit() } returns Tasks.forResult(null)
        }
        every { mockFirestore.batch() } returns batch

        val parentId = repository.createMultiLineNote("Line 1\nLine 2\nLine 3").getOrThrow()

        assertEquals("parent_id", parentId)
        verify(exactly = 3) { batch.set(any(), any<Map<String, Any?>>()) }
    }

    @Test
    fun `createMultiLineNote treats blank lines as spacers`() = runTest {
        val refs = listOf("parent_id", "child_id").map { id ->
            mockk<DocumentReference> { every { this@mockk.id } returns id }
        }
        every { mockCollection.document() } returnsMany refs
        val batch = mockk<WriteBatch>(relaxed = true) {
            every { commit() } returns Tasks.forResult(null)
        }
        every { mockFirestore.batch() } returns batch

        repository.createMultiLineNote("Line 1\n\nLine 3").getOrThrow()

        verify(exactly = 2) { batch.set(any(), any<Map<String, Any?>>()) }
    }

    // endregion

    companion object {
        private const val USER_ID = "test_user_id"
    }
}

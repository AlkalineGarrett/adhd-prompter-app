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

/**
 * Unit tests for NoteRepository using MockK for Firebase mocking.
 */
class NoteRepositoryTest {

    private lateinit var mockFirestore: FirebaseFirestore
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockUser: FirebaseUser
    private lateinit var mockCollection: CollectionReference
    private lateinit var repository: NoteRepository

    @Before
    fun setUp() {
        mockFirestore = mockk(relaxed = true)
        mockAuth = mockk(relaxed = true)
        mockUser = mockk(relaxed = true)
        mockCollection = mockk(relaxed = true)

        every { mockAuth.currentUser } returns mockUser
        every { mockUser.uid } returns "test_user_id"
        every { mockFirestore.collection("notes") } returns mockCollection

        repository = NoteRepository(mockFirestore, mockAuth)
    }

    @Test
    fun `loadNoteWithChildren should return failure when user is not signed in`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = repository.loadNoteWithChildren("note_1")

        assertTrue(result.isFailure)
        assertEquals("User not signed in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `loadNoteWithChildren should return empty line when document does not exist`() = runTest {
        val noteId = "note_1"
        val mockDocRef = mockk<DocumentReference>()
        val mockSnapshot = mockk<DocumentSnapshot>()

        every { mockCollection.document(noteId) } returns mockDocRef
        every { mockDocRef.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.exists() } returns false

        val result = repository.loadNoteWithChildren(noteId)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()
        assertNotNull(lines)
        assertEquals(1, lines?.size)
        assertEquals("", lines?.get(0)?.content)
        assertEquals(noteId, lines?.get(0)?.noteId)
    }

    @Test
    fun `loadNoteWithChildren should load parent note with content`() = runTest {
        val noteId = "note_1"
        val noteContent = "Parent content"
        val mockDocRef = mockk<DocumentReference>()
        val mockSnapshot = mockk<DocumentSnapshot>()
        val note = Note(
            id = noteId,
            userId = "test_user_id",
            content = noteContent,
            containedNotes = emptyList()
        )

        every { mockCollection.document(noteId) } returns mockDocRef
        every { mockDocRef.get() } returns Tasks.forResult(mockSnapshot)
        every { mockSnapshot.exists() } returns true
        every { mockSnapshot.toObject(Note::class.java) } returns note

        val result = repository.loadNoteWithChildren(noteId)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()
        assertNotNull(lines)
        assertEquals(1, lines?.size)
        assertEquals(noteContent, lines?.get(0)?.content)
        assertEquals(noteId, lines?.get(0)?.noteId)
    }

    @Test
    fun `loadNoteWithChildren should load parent and child notes`() = runTest {
        val noteId = "note_1"
        val childId1 = "child_1"
        val childId2 = "child_2"

        val parentRef = mockk<DocumentReference>()
        val childRef1 = mockk<DocumentReference>()
        val childRef2 = mockk<DocumentReference>()
        val parentSnapshot = mockk<DocumentSnapshot>()
        val childSnapshot1 = mockk<DocumentSnapshot>()
        val childSnapshot2 = mockk<DocumentSnapshot>()

        val parentNote = Note(
            id = noteId,
            userId = "test_user_id",
            content = "Parent",
            containedNotes = listOf(childId1, childId2)
        )
        val childNote1 = Note(id = childId1, userId = "test_user_id", content = "Child 1")
        val childNote2 = Note(id = childId2, userId = "test_user_id", content = "Child 2")

        every { mockCollection.document(noteId) } returns parentRef
        every { mockCollection.document(childId1) } returns childRef1
        every { mockCollection.document(childId2) } returns childRef2
        every { parentRef.get() } returns Tasks.forResult(parentSnapshot)
        every { childRef1.get() } returns Tasks.forResult(childSnapshot1)
        every { childRef2.get() } returns Tasks.forResult(childSnapshot2)
        every { parentSnapshot.exists() } returns true
        every { childSnapshot1.exists() } returns true
        every { childSnapshot2.exists() } returns true
        every { parentSnapshot.toObject(Note::class.java) } returns parentNote
        every { childSnapshot1.toObject(Note::class.java) } returns childNote1
        every { childSnapshot2.toObject(Note::class.java) } returns childNote2

        val result = repository.loadNoteWithChildren(noteId)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()
        assertNotNull(lines)
        assertEquals(3, lines?.size)
        assertEquals("Parent", lines?.get(0)?.content)
        assertEquals("Child 1", lines?.get(1)?.content)
        assertEquals("Child 2", lines?.get(2)?.content)
        assertEquals(noteId, lines?.get(0)?.noteId)
        assertEquals(childId1, lines?.get(1)?.noteId)
        assertEquals(childId2, lines?.get(2)?.noteId)
    }

    @Test
    fun `loadNoteWithChildren should handle empty child IDs as empty lines`() = runTest {
        val noteId = "note_1"
        val parentRef = mockk<DocumentReference>()
        val childRef = mockk<DocumentReference>()
        val parentSnapshot = mockk<DocumentSnapshot>()
        val childSnapshot = mockk<DocumentSnapshot>()

        val parentNote = Note(
            id = noteId,
            userId = "test_user_id",
            content = "Parent",
            containedNotes = listOf("", "child_1", "") // Empty strings represent spacers
        )
        val childNote = Note(id = "child_1", userId = "test_user_id", content = "Child")

        every { mockCollection.document(noteId) } returns parentRef
        every { mockCollection.document("child_1") } returns childRef
        every { parentRef.get() } returns Tasks.forResult(parentSnapshot)
        every { childRef.get() } returns Tasks.forResult(childSnapshot)
        every { parentSnapshot.exists() } returns true
        every { childSnapshot.exists() } returns true
        every { parentSnapshot.toObject(Note::class.java) } returns parentNote
        every { childSnapshot.toObject(Note::class.java) } returns childNote

        val result = repository.loadNoteWithChildren(noteId)

        assertTrue(result.isSuccess)
        val lines = result.getOrNull()
        assertNotNull(lines)
        assertEquals(4, lines?.size) // Parent + 3 contained notes
        assertEquals("", lines?.get(1)?.content) // Empty line
        assertEquals("Child", lines?.get(2)?.content)
        assertEquals("", lines?.get(3)?.content) // Empty line
    }

    @Test
    fun `saveNoteWithChildren should return failure when user is not signed in`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = repository.saveNoteWithChildren("note_1", listOf(NoteLine("Content", "note_1")))

        assertTrue(result.isFailure)
        assertEquals("User not signed in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `saveNoteWithChildren should save parent note with content`() = runTest {
        val noteId = "note_1"
        val mockDocRef = mockk<DocumentReference>()
        val mockSnapshot = mockk<DocumentSnapshot>()

        every { mockCollection.document(noteId) } returns mockDocRef
        every { mockFirestore.runTransaction<Map<Int, String>>(any()) } answers {
            val function = firstArg<Transaction.Function<Map<Int, String>>>()
            val mockTransaction = mockk<Transaction>(relaxed = true)
            every { mockTransaction.get(mockDocRef) } returns mockSnapshot
            every { mockSnapshot.exists() } returns false
            Tasks.forResult(function.apply(mockTransaction))
        }

        val trackedLines = listOf(NoteLine("Parent content", noteId))
        val result = repository.saveNoteWithChildren(noteId, trackedLines)

        assertTrue(result.isSuccess)
        verify { mockFirestore.runTransaction<Map<Int, String>>(any()) }
    }

    @Test
    fun `saveNoteWithChildren should create new child notes for lines without IDs`() = runTest {
        val noteId = "note_1"
        val newChildId = "new_child_id"
        val mockDocRef = mockk<DocumentReference>()
        val mockChildRef = mockk<DocumentReference>()
        val mockSnapshot = mockk<DocumentSnapshot>()

        every { mockCollection.document(noteId) } returns mockDocRef
        every { mockCollection.document() } returns mockChildRef
        every { mockChildRef.id } returns newChildId
        every { mockFirestore.runTransaction<Map<Int, String>>(any()) } answers {
            val function = firstArg<Transaction.Function<Map<Int, String>>>()
            val mockTransaction = mockk<Transaction>(relaxed = true)
            every { mockTransaction.get(mockDocRef) } returns mockSnapshot
            every { mockSnapshot.exists() } returns false
            Tasks.forResult(function.apply(mockTransaction))
        }

        val trackedLines = listOf(
            NoteLine("Parent", noteId),
            NoteLine("New child", null) // No ID means new note
        )
        val result = repository.saveNoteWithChildren(noteId, trackedLines)

        assertTrue(result.isSuccess)
        val newIds = result.getOrNull()
        assertNotNull(newIds)
        assertTrue(newIds?.containsKey(1) == true)
        assertEquals(newChildId, newIds?.get(1))
    }

    @Test
    fun `saveNoteWithChildren should update existing child notes`() = runTest {
        val noteId = "note_1"
        val childId = "child_1"
        val mockDocRef = mockk<DocumentReference>()
        val mockChildRef = mockk<DocumentReference>()
        val mockSnapshot = mockk<DocumentSnapshot>()

        every { mockCollection.document(noteId) } returns mockDocRef
        every { mockCollection.document(childId) } returns mockChildRef
        every { mockFirestore.runTransaction<Map<Int, String>>(any()) } answers {
            val function = firstArg<Transaction.Function<Map<Int, String>>>()
            val mockTransaction = mockk<Transaction>(relaxed = true)
            every { mockTransaction.get(mockDocRef) } returns mockSnapshot
            every { mockSnapshot.exists() } returns true
            every { mockSnapshot.get("containedNotes") } returns listOf(childId)
            Tasks.forResult(function.apply(mockTransaction))
        }

        val trackedLines = listOf(
            NoteLine("Parent", noteId),
            NoteLine("Updated child", childId) // Has ID means existing note
        )
        val result = repository.saveNoteWithChildren(noteId, trackedLines)

        assertTrue(result.isSuccess)
        verify { mockFirestore.runTransaction<Map<Int, String>>(any()) }
    }

    @Test
    fun `saveNoteWithChildren should soft delete removed child notes`() = runTest {
        val noteId = "note_1"
        val oldChildId = "old_child_1"
        val mockDocRef = mockk<DocumentReference>()
        val mockOldChildRef = mockk<DocumentReference>()
        val mockSnapshot = mockk<DocumentSnapshot>()

        every { mockCollection.document(noteId) } returns mockDocRef
        every { mockCollection.document(oldChildId) } returns mockOldChildRef
        every { mockFirestore.runTransaction<Map<Int, String>>(any()) } answers {
            val function = firstArg<Transaction.Function<Map<Int, String>>>()
            val mockTransaction = mockk<Transaction>(relaxed = true)
            every { mockTransaction.get(mockDocRef) } returns mockSnapshot
            every { mockSnapshot.exists() } returns true
            every { mockSnapshot.get("containedNotes") } returns listOf(oldChildId)
            Tasks.forResult(function.apply(mockTransaction))
        }

        val trackedLines = listOf(NoteLine("Parent", noteId)) // No child, so old child should be deleted
        val result = repository.saveNoteWithChildren(noteId, trackedLines)

        assertTrue(result.isSuccess)
        verify { mockFirestore.runTransaction<Map<Int, String>>(any()) }
    }

    @Test
    fun `loadUserNotes should return failure when user is not signed in`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = repository.loadUserNotes()

        assertTrue(result.isFailure)
        assertEquals("User not signed in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `loadUserNotes should return filtered notes`() = runTest {
        val mockQuery = mockk<Query>()
        val mockQuerySnapshot = mockk<QuerySnapshot>()
        val mockDoc1 = mockk<QueryDocumentSnapshot>()
        val mockDoc2 = mockk<QueryDocumentSnapshot>()
        val mockDoc3 = mockk<QueryDocumentSnapshot>()

        val note1 = Note(id = "note_1", userId = "test_user_id", parentNoteId = null, state = null)
        val note2 = Note(id = "note_2", userId = "test_user_id", parentNoteId = null, state = "deleted")
        val note3 = Note(id = "note_3", userId = "test_user_id", parentNoteId = "parent_1", state = null)

        every { mockCollection.whereEqualTo("userId", "test_user_id") } returns mockQuery
        every { mockQuery.get() } returns Tasks.forResult(mockQuerySnapshot)
        every { mockQuerySnapshot.iterator() } returns mutableListOf(mockDoc1, mockDoc2, mockDoc3).iterator()
        every { mockDoc1.id } returns "note_1"
        every { mockDoc2.id } returns "note_2"
        every { mockDoc3.id } returns "note_3"
        every { mockDoc1.toObject(Note::class.java) } returns note1
        every { mockDoc2.toObject(Note::class.java) } returns note2
        every { mockDoc3.toObject(Note::class.java) } returns note3

        val result = repository.loadUserNotes()

        assertTrue(result.isSuccess)
        val notes = result.getOrNull()
        assertNotNull(notes)
        // Should only include note_1 (note_2 is deleted, note_3 is a child)
        assertEquals(1, notes?.size)
        assertEquals("note_1", notes?.get(0)?.id)
    }

    @Test
    fun `createNote should return failure when user is not signed in`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = repository.createNote()

        assertTrue(result.isFailure)
        assertEquals("User not signed in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `createNote should create new note and return ID`() = runTest {
        val mockDocRef = mockk<DocumentReference>()

        every { mockCollection.add(any<Map<String, Any>>()) } returns Tasks.forResult(mockDocRef)
        every { mockDocRef.id } returns "new_note_id"

        val result = repository.createNote()

        assertTrue(result.isSuccess)
        assertEquals("new_note_id", result.getOrNull())
        verify { mockCollection.add(any<Map<String, Any>>()) }
    }

    @Test
    fun `createMultiLineNote should return failure when user is not signed in`() = runTest {
        every { mockAuth.currentUser } returns null

        val result = repository.createMultiLineNote("Line 1\nLine 2")

        assertTrue(result.isFailure)
        assertEquals("User not signed in", result.exceptionOrNull()?.message)
    }

    @Test
    fun `createMultiLineNote should create parent and child notes`() = runTest {
        val mockParentRef = mockk<DocumentReference>()
        val mockChildRef1 = mockk<DocumentReference>()
        val mockChildRef2 = mockk<DocumentReference>()
        val mockBatch = mockk<WriteBatch>(relaxed = true)

        every { mockCollection.document() } returnsMany listOf(mockParentRef, mockChildRef1, mockChildRef2)
        every { mockParentRef.id } returns "parent_id"
        every { mockChildRef1.id } returns "child_id_1"
        every { mockChildRef2.id } returns "child_id_2"
        every { mockFirestore.batch() } returns mockBatch
        every { mockBatch.commit() } returns Tasks.forResult(null)

        val result = repository.createMultiLineNote("Line 1\nLine 2\nLine 3")

        assertTrue(result.isSuccess)
        assertEquals("parent_id", result.getOrNull())
        // 1 parent + 2 children (Line 2 and Line 3 are non-empty)
        verify(exactly = 3) { mockBatch.set(any<DocumentReference>(), any<Map<String, Any>>()) }
        verify { mockBatch.commit() }
    }

    @Test
    fun `createMultiLineNote should handle empty lines as spacers`() = runTest {
        val mockParentRef = mockk<DocumentReference>()
        val mockChildRef = mockk<DocumentReference>()
        val mockBatch = mockk<WriteBatch>(relaxed = true)

        every { mockCollection.document() } returnsMany listOf(mockParentRef, mockChildRef)
        every { mockParentRef.id } returns "parent_id"
        every { mockChildRef.id } returns "child_id"
        every { mockFirestore.batch() } returns mockBatch
        every { mockBatch.commit() } returns Tasks.forResult(null)

        val result = repository.createMultiLineNote("Line 1\n\nLine 3") // Empty line in middle

        assertTrue(result.isSuccess)
        assertEquals("parent_id", result.getOrNull())
        // Should create parent + 1 child (only non-empty lines after first)
        verify(exactly = 2) { mockBatch.set(any<DocumentReference>(), any<Map<String, Any>>()) }
    }
}

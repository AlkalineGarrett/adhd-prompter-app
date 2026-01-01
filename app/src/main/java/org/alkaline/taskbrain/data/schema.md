# Firestore Schema Design

## Collection: `notes`
Path: `/notes/{noteId}`

### Document Structure
**IMPORTANT** Keep in sync with Note.kt
```json
{
  "id": "String: the ID of this note.",
  "userId": "String: the user ID of the owner.",
  "parentNodeId": "String (noteId) [optional]: Parent note ID",
  "content": "String: The main text content.",
  "createdAt": "Timestamp: Server timestamp of creation",
  "updatedAt": "Timestamp: Server timestamp of last update",
  "tags": [
    "String",
    "String"
  ],
  "containedNotes": [
    "String (noteId)",
    "String (noteId)"
  ],
  "state": "String: null or 'deleted'"
}
```

### Field Explanations
- **content**: First line of the note. Each remaining line goes in its own note and is referenced under containedNotes.
- **tags**: Array of strings for filtering. Use `array-contains` queries.
- **containedNotes**: Array of note IDs representing the contents of the present note (except the first line).
  - Notes are composed recursively.
  - Empty lines don't have their own note. They are indicated by an empty string in the containedNotes array.

## Potential future fields

```json
  "resources": [
    {
      "url": "String: URL to the external resource (e.g., Firebase Storage, YouTube)",
      "type": "String: 'image', 'audio', 'video', etc."
    }
  ],
```

### Explanations
- **resources**: Array of objects for external media. `caption` is omitted as it lives in `content`.

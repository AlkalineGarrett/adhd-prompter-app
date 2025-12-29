# Firestore Schema Design

## Collection: `notes`
Path: `/notes/{noteId}`

### Document Structure
```json
{
  "content": "String: The main text content. In-text links format: [display text](note://{linkedNoteId})",
  "createdAt": "Timestamp: Server timestamp of creation",
  "updatedAt": "Timestamp: Server timestamp of last update",
  "tags": [
    "String",
    "String"
  ],
  "primaryLinks": [
    "String (noteId)",
    "String (noteId)"
  ],
  "resources": [
    {
      "url": "String: URL to the external resource (e.g., Firebase Storage, YouTube)",
      "type": "String: 'image', 'audio', 'video', etc."
    }
  ]
}
```

### Field Explanations
- **content**: Main body text. Custom parsing required for `note://` links.
- **tags**: Array of strings for filtering. Use `array-contains` queries.
- **primaryLinks**: Array of note IDs representing 1-N relationships.
- **resources**: Array of objects for external media. `caption` is omitted as it lives in `content`.

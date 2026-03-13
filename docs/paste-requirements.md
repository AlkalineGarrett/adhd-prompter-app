# Paste Behavior Requirements

Applies to both Android and Web editors.

## Terminology

- **Prefix**: the leading structural characters of a line — indentation tabs, bullet (`• `), checkbox (`☐ `, `☑ `). Everything before the content.
- **Content**: the text after the prefix.
- **Source**: the clipboard content being pasted.
- **Destination**: the line(s) where the paste occurs.
- **Full-line selection**: a selection that covers one or more complete lines (e.g., gutter selection, or selection from position 0 to end of line).

## Core Rules

### Rule 1: Source prefix wins; adopt destination if absent

When pasting, each pasted line is evaluated independently:
- If the pasted line **has a prefix**, it keeps its own prefix.
- If the pasted line **has no prefix**, it adopts the destination line's prefix.

This applies per-line, not just to the first line.

### Rule 2: Mid-line multi-line paste splits the target

When pasting multi-line content and the cursor (or selection) is within a line's content:

1. **Split** the destination line at the cursor position (or selection boundaries) into a **leading half** and a **trailing half**.
2. The leading half keeps the original line's prefix and the content before the cursor.
3. The trailing half inherits the original line's prefix and gets the content after the cursor.
4. The pasted lines are inserted **between** the two halves.
5. **Drop empty halves**: if the leading half has no content (cursor was at start of content), discard it. If the trailing half has no content (cursor was at end of content), discard it.

This rule also applies when there is a partial single-line selection — the selected text is removed, then the line is split at the selection point.

For partial multi-line selections: the leading half comes from the first selected line (text before the selection start), the trailing half comes from the last selected line (text after the selection end). Both inherit their respective line's prefix.

### Rule 3: Full-line selection paste is clean replacement

When the selection covers complete lines (gutter selection), the pasted lines replace the selected lines wholesale. No prefix merging — paste content as-is. Unprefixed external text stays unprefixed.

### Rule 4: Relative indent shifting

Indentation is adjusted so the pasted content's structure is preserved relative to the insertion point:

1. Compute `delta = destination_indent_level - first_pasted_line_indent_level`.
2. Apply `delta` to every pasted line's indent level.
3. Clamp all indent levels to a minimum of 0.

The destination indent level is:
- The indent level of the line the cursor is on (for cursor paste).
- The indent level of the first selected line (for selection paste).
- 0 for empty lines.

### Rule 5: Single-line paste is simple insertion

When pasting text that contains no newlines, insert it at the cursor position or replace the selection. No prefix logic applies — it's a plain text insertion into the content.

## External Source Handling

### Plain text (no formatting)

Lines have no prefixes. Per Rule 1, each line adopts the destination's prefix (if the destination has one). If the destination has no prefix, pasted lines have no prefix.

### Markdown text

Parse markdown list markers into internal prefix format before applying paste rules:

| Markdown | Internal prefix |
|---|---|
| `- ` | `• ` (bullet) |
| `* ` | `• ` (bullet) |
| `- [ ] ` | `☐ ` (unchecked checkbox) |
| `- [x] ` or `- [X] ` | `☑ ` (checked checkbox) |
| Leading spaces/tabs | Convert to indent tabs (e.g., 2 or 4 spaces = 1 tab) |
| `1. `, `2. ` etc. | Strip the number prefix, treat as plain text |

After parsing, apply the standard paste rules.

### Rich text (HTML clipboard from Word, Google Docs, etc.)

Read `text/html` from the clipboard when available. Parse the HTML structure:

| HTML | Internal format |
|---|---|
| `<li>` inside `<ul>` | `• ` (bullet) prefix |
| `<li>` inside `<ol>` | No prefix (strip numbering) |
| `<input type="checkbox">` or `<li data-checked>` | `☐ ` / `☑ ` checkbox prefix |
| Nested `<ul>`/`<ol>` depth | Indent tabs (1 tab per nesting level) |
| `<p>`, `<div>`, `<br>` | Line breaks |
| All other formatting | Strip, keep text content only |

Fall back to `text/plain` if HTML parsing fails or no HTML is available.

## Scenario Examples

### Multi-line paste onto prefix-only line

Clipboard: `☐ one\n☐ two` → Destination: `• |` (prefix-only, empty content)

Leading half is empty → dropped. Source has prefixes → source wins:
```
☐ one
☐ two
```

### Multi-line paste mid-content

Clipboard: `☐ one\n☐ two` → Destination: `• hel|lo`

Split into `• hel` and `• lo`. Pasted lines between:
```
• hel
☐ one
☐ two
• lo
```

### Unprefixed paste mid-content

Clipboard: `one\ntwo` → Destination: `• hel|lo`

Source has no prefix → adopt destination `• `:
```
• hel
• one
• two
• lo
```

### Multi-line paste at end of content

Clipboard: `☐ one\n☐ two` → Destination: `• hello|`

Trailing half is empty → dropped:
```
• hello
☐ one
☐ two
```

### Gutter selection replacement

Clipboard: `☐ one\n☐ two` → Selection (gutter): `[• hello\n• world]`

Clean replacement:
```
☐ one
☐ two
```

### External plain text paste

Clipboard: `one\ntwo\nthree` → Destination: `☐ |`

Source has no prefix → adopt destination `☐ `:
```
☐ one
☐ two
☐ three
```

### Indentation shifting

Clipboard (indents 2, 3, 2):
```
\t\t☐ parent
\t\t\t☐ child
\t\t☐ sibling
```
Destination indent level: 1. Delta = 1 - 2 = -1.
```
\t☐ parent
\t\t☐ child
\t☐ sibling
```

### Partial multi-line selection

Clipboard: `☐ one\n☐ two` → Selection: `• hel[lo\n• wor]ld`

Leading half from first line: `• hel`. Trailing half from last line: `• ld`.
```
• hel
☐ one
☐ two
• ld
```

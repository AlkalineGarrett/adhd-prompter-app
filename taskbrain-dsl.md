# TaskBrain DSL Specification

A domain-specific language for executable note directives in TaskBrain.

## Design Philosophy

- **Mobile-first**: Minimize special characters since this is written on a phone
- **Single-word identifiers**: Built-in names are preferentially single words
- **Intuitive binding**: Easy to understand what binds to what
- **Brackets for execution**: `[...]` denotes executable directives

---

## Syntax Fundamentals

### Identifiers and Literals

| Type | Syntax | Examples |
|------|--------|----------|
| Identifier | Alphanumeric + `_`, starting with letter or `_` | `variable1`, `my_var`, `_other` |
| Number | Numeric sequence | `1234`, `42` |
| String | Double quotes | `"hello world"` |

### Escaping Literal Brackets

- `[[` produces a literal `[`
- `]]` produces a literal `]`

### Directive Structure

Directives are enclosed in square brackets: `[...]`

- Directives may span multiple lines (ends at closing `]`)
- Multiple directives allowed per line: `text [dir1] more text [dir2]`

### Expression Parsing: Right-to-Left Nesting

Space-separated tokens nest right-to-left as function calls:

| Syntax | Equivalent | Meaning |
|--------|------------|---------|
| `[a]` | `[a()]` | Execute `a` (no-arg function) or read `a` (variable) |
| `[a b]` | `[a(b)]` | Call `a` with argument `b` |
| `[a b c]` | `[a(b(c))]` | Call `b` with `c`, pass result to `a` |
| `[a b c d]` | `[a(b(c(d)))]` | Unlimited nesting depth |

### Parentheses for Explicit Grouping

Parentheses override the default nesting:

```
[a(b, c)]        # Call a with two arguments: b and c
[a(b(c, d))]     # Call b with c and d, pass result to a
```

**Rule**: If a function takes 2+ arguments, parentheses are required.

### Invalid Syntax

```
[a, b]           # Invalid: comma separates args, not calls
[a b c, d]       # Invalid: ambiguous binding with mixed syntax
```

### The Colon Operator (Context-Dependent)

The colon `:` has two meanings based on context:

**Variable definition** (standalone):
```
[x: 5]                    # Define local variable x = 5
[.path: "2026-01-01"]     # Set current note's path property
[obj.prop: value]         # Set property on object
```

**Named argument** (after function name):
```
[fcn param1:arg1]                    # Call fcn with named param
[fcn(param1:arg1, param2:arg2)]      # Multiple named params
```

### Statement Separation

Semicolon separates multiple statements within a directive:
```
[x: 5; y: 10; add(x, y)]    # Define x, define y, return sum
```

**Variable scope**: Local variables exist only within their enclosing `[...]` directive.

### The Dot Operator: Current Note Reference

- `[.]` refers to the current note
- `[.prop]` reads a property from the current note
- `[.prop: value]` writes a property to the current note
- `[obj.prop]` reads a property from an object

---

## Data Types

### Primitive Types

- **Number**: Integer or decimal values
- **String**: Text enclosed in double quotes
- **Boolean**: Result of comparison functions (`true`/`false`)
- **Date**: Date value (from `date` function or parsing)
- **Time**: Time value (from `time` function)

### Composite Types

- **List**: Ordered collection created with `list(a, b, c, ...)`
- **Note**: A note object with properties
- **Deferred Reference**: An unevaluated expression (from `later`)
- **Lambda**: A function with implicit parameter `i` (from `lambda[...]`)
- **Pattern**: A matching pattern (from `pattern(...)`)

### Special Values

- **`undefined`**: Returned when accessing a missing property
- **`empty`**: Context-dependent empty value:
  - List → `list()` (empty list)
  - String → `""`
  - Number → `0`

**Rule**: Accessing `undefined` (except in boolean expressions) is an error. Use `maybe(expr)` to convert `undefined` to `empty`.

---

## Note Properties

Notes have the following read/write properties:

| Property | Type | Description |
|----------|------|-------------|
| `path` | String | Unique path identifier (globally enforced) |
| `content` | String | Full text content of the note |
| `created` | Date | Creation timestamp |
| `modified` | Date | Last modification timestamp |
| `viewed` | Date | Last viewed timestamp |

Example:
```
[.path]                    # Read current note's path
[.path: "journal/2026"]    # Set current note's path
[.modified]                # Read last modified date
```

---

## Deferred Execution

### `later` - Defer Next Token

`later` creates a deferred reference to the next token only:

```
[later x]                  # Defer evaluation of x
[later fcn]                # Defer the function call fcn
```

### `later[...]` - Defer a Scope

Square brackets after `later` create a deferred scope where nothing inside executes:

```
[later[.append iso8601 date]]    # Entire expression is deferred
```

### `run` - Force Evaluation in Deferred Scope

Use `run` to evaluate something immediately within a deferred scope:

```
[later[.append iso8601 run date]]    # date evaluates NOW; .append and iso8601 deferred
```

### `lambda[...]` - Lambda with Implicit Parameter

Creates a single-argument function where `i` is the implicit parameter:

```
[lambda[gt(i.modified, add_days(date, -1))]]    # Lambda checking if modified after yesterday
[lambda[parse_date i.path]]                     # Lambda extracting date from path
```

### `lambda(params)[...]` - Lambda with Named Parameters

Creates a multi-argument function with explicit parameter names:

```
[lambda(a, b)[gt(a.modified, b.modified)]]    # Compare two notes by modified date
[lambda(note, threshold)[gt(note.viewed, threshold)]]    # Check if viewed after threshold
```

### `schedule` - Scheduled Execution

Schedules a one-time or recurring execution:

```
[schedule(daily, later[.append iso8601 date])]
```

- **Parameter 1**: Date/time rule (e.g., `daily`)
- **Parameter 2**: A deferred reference; all nested `later` expressions resolve at execution time

**Execution environment**: Hybrid - cloud function for reliability, device background job as offline fallback.

**On note deletion**: User is warned about active schedules; if confirmed, schedules are cancelled.

### `refresh` - Reactive Re-execution

Re-executes when underlying data changes:

```
[refresh view(find(path: pattern(digit*4 "-" digit*2 "-" digit*2)))]
```

**Dependency tracking**: Automatic - the system tracks all note reads/finds in the expression and re-executes when any dependency changes.

---

## Built-in Functions

### Comparison (Function-Style)

| Function | Description |
|----------|-------------|
| `eq(a, b)` | Equal |
| `ne(a, b)` | Not equal |
| `gt(a, b)` | Greater than |
| `lt(a, b)` | Less than |
| `gte(a, b)` | Greater than or equal |
| `lte(a, b)` | Less than or equal |
| `and(a, b)` | Logical AND |
| `or(a, b)` | Logical OR |
| `not(a)` | Logical NOT |

### Arithmetic (Function-Style)

| Function | Description |
|----------|-------------|
| `add(a, b)` | Addition |
| `sub(a, b)` | Subtraction |
| `mul(a, b)` | Multiplication |
| `div(a, b)` | Division |
| `mod(a, b)` | Modulo |

### Date/Time

| Function | Description |
|----------|-------------|
| `date` | Returns current date |
| `time` | Returns current time |
| `iso8601(date)` | Formats date as "yyyy-MM-dd" |
| `parse_date(string)` | Parses a date from string |
| `add_days(date, n)` | Add n days to date (use negative n for past dates) |
| `diff_days(d1, d2)` | Difference in days between dates |
| `before(d1, d2)` | True if d1 is before d2 |
| `after(d1, d2)` | True if d1 is after d2 |

**Note**: Relative dates are derived via arithmetic, not constants:
- Yesterday: `add_days(date, -1)`
- Last week: `add_days(date, -7)`
- Tomorrow: `add_days(date, 1)`

### Date/Time Rules (for `schedule`)

| Rule | Description |
|------|-------------|
| `daily` | Triggers once a day in the first hour |

### String Operations

**`string(...)`** - Concatenation with special parsing:

Inside `string()`, tokens are concatenated instead of nested as function calls:

```
[string("Hello " name ", today is " iso8601 date)]
# Concatenates: "Hello " + name + ", today is " + (iso8601(date))
```

### List Operations

| Function | Description |
|----------|-------------|
| `list(a, b, c, ...)` | Create a list |

### Note Operations

#### Note collection operations
| Function                             | Description                                                         |
|--------------------------------------|---------------------------------------------------------------------|
| `new(path:p, content:c)`             | Create a new note; errors if path exists                            |
| `maybe_new(path:p, maybe_content:c)` | Idempotently ensure note exists, only sets content if doesn't exist |
| `find(path:pattern, where:lambda)`   | Search for notes                                                    |

#### Single note operations
| `.append(text)` | Append line to note |

### Conditionals

```
[if(condition, then_expr, else_expr)]
```

### Null Handling

| Function | Description |
|----------|-------------|
| `maybe(expr)` | Convert `undefined` to `empty` |

### Execution Control

| Function | Description |
|----------|-------------|
| `run(deferred)` | Execute a deferred reference |

---

## The `find` Function

Searches for notes matching criteria.

**Parameters:**
- `path`: A pattern to match against note paths
- `where`: A lambda predicate for filtering

**Returns**: A list of matching notes (empty list if none found).

**Examples:**
```
[find path:"2026-01-15"]                           # Exact path match
[find path:pattern(digit*4 "-" digit*2 "-" digit*2)]   # All ISO date paths
[find where:lambda[after(i.modified, add_days(date, -1))]]     # Modified after yesterday
[find(path:pattern("journal/" any*(1..)), where:lambda[before(i.created, add_days(date, -7))])]
```

---

## Patterns

Mobile-friendly pattern syntax for matching strings.

### Syntax

```
[pattern(digit*4 "-" digit*2 "-" digit*2)]    # Matches "2026-01-15"
```

### Character Classes

| Class | Matches |
|-------|---------|
| `digit` | 0-9 |
| `letter` | a-z, A-Z |
| `space` | Whitespace |
| `punct` | Punctuation |
| `any` | Any character |

### Literals

Literal strings must be in quotes:
```
[pattern(digit*4 "-" digit*2)]    # "-" is a literal dash
```

Spaces outside quotes are token separators (not semantic).

### Quantifiers

| Syntax | Meaning |
|--------|---------|
| `*4` | Exactly 4 times |
| `*any` | Any number of times (0+) |
| `*n` | Number of times held in variable `n` |
| `*(0..5)` | 0 to 5 times |
| `*(0..)` | 0 or more (same as `*any`) |
| `*(1..)` | 1 or more |

### Reserved for Future

- `glob(...)` - Glob-style patterns

---

## UI Elements

### `button` - Interactive Button

Creates a tappable button that executes an action:

```
[button("Add today's date", later[.append iso8601 date])]
```

- **Parameter 1**: Label text (string)
- **Parameter 2**: Action to execute (typically a deferred reference)

**Display**: The directive is replaced with a tappable button showing the label. A secondary button allows editing the directive.

### `view` - Inline Note Content

Dynamically fetches and inlines content from other notes:

```
[view(find path:pattern(digit*4 "-" digit*2 "-" digit*2), order_key:lambda[parse_date i.path], order:desc)]
```

- **Parameter 1**: List of notes to display
- **order_key**: Named parameter; lambda returning the value to sort by
- **order**: Named parameter; `asc` (ascending) or `desc` (descending)

**Display**: Notes' content is inlined as text, separated by dividers.

**Recursion**: Viewed notes' directives also execute, including nested `view` directives.

---

## Execution Model

### Execution Trigger

Directives execute **on save**. Before save, pending directives show an indicator.

### Execution Rules by Top-Level Token

| Top-Level | Behavior |
|-----------|----------|
| `refresh` | Re-execute when dependencies change |
| `schedule` | Register with cloud/device scheduler |
| `later` | Don't execute automatically; provide manual trigger |
| `button` | Render as button; execute on tap |
| (other) | Execute immediately on save; cache result |

### Result Display

- **Default**: Show the computed result; directive is collapsed
- **Expand**: A small button reveals/hides the original directive
- **Cached**: Results are cached in the note alongside the original directive

### Manual Re-execution

For non-`refresh` directives, users can manually re-execute to update the cached result.

---

## Error Handling

### Parse Errors

Shown inline immediately (before save) where the directive appears.

### Execution Errors

Shown inline after save where the result would appear.

### Global Error Navigation

A navigation button at the bottom lists execution errors across all notes.

### Specific Error Conditions

| Condition | Behavior |
|-----------|----------|
| `new` with existing path | Error: duplicate path |
| Access `undefined` property | Error (except in boolean context) |
| Network failure in `schedule` | Error displayed; schedule retried |

---

## Complete Examples

### Set Note Path
```
[.path: "2026-01-01"]
```
Sets the current note's path to "2026-01-01".

### Daily Date Append
```
[schedule(daily, later[.append iso8601 date])]
```
Every day, append the current date (formatted as ISO 8601) to the end of the note.

### Daily Note Creation
```
[date_str: iso8601 later date; schedule(daily, later[new(path:date_str, content:date_str)])]
```
Every day, create a new note with the path and content set to that day's ISO 8601 date.

### Dynamic Journal View
```
[refresh view(find path:pattern(digit*4 "-" digit*2 "-" digit*2), order_key:lambda[parse_date i.path], order:desc)]
```
Show all notes with ISO date paths, ordered most recent first. Refresh when any matching note changes.

### Quick Action Button
```
[button("New Journal Entry", later[new(path:iso8601 date, content:string("# " iso8601 date))])]
```
A button that creates a new journal note for today when tapped.

### Conditional Append
```
[if(eq(.path, "inbox"), .append "Processed", .append "Skipped")]
```
Append "Processed" if this note's path is "inbox", otherwise append "Skipped".

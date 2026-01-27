# TaskBrain DSL Specification

A domain-specific language for executable note directives in TaskBrain.

## Design Philosophy

- **Mobile-first**: This DSL is primarily written on a phone, so ease of typing drives many design decisions.

- **Minimize special characters**: Prefer built-in identifiers and functions over symbolic operators. For example, arithmetic uses `add(a, b)` instead of `a + b`, and comparison uses `gt(a, b)` instead of `a > b`. When special characters are necessary, prefer accessible characters commonly used in sentences: `.`, `(`, `)`, `,`, `:`, `"`.

- **Graceful undefined access**: Accessing non-existent data returns `undefined` rather than throwing an error. This includes: out-of-bounds list access (e.g., `first` on empty list), missing properties, and hierarchy navigation beyond available ancestors (e.g., `.up` on a root note). This reduces branching paths, code complexity, and typing on mobile. Use `maybe(expr)` to convert `undefined` to `empty` when needed.

- **Single-word identifiers**: Built-in names are preferentially single words for brevity.

- **Intuitive binding**: Easy to understand what binds to what.

- **Brackets for execution**: `[...]` denotes executable directives.

---

## Syntax Fundamentals

### Identifiers and Literals

| Type | Syntax | Examples |
|------|--------|----------|
| Identifier | Alphanumeric + `_`, starting with letter or `_` | `variable1`, `my_var`, `_other` |
| Number | Numeric sequence | `1234`, `42` |
| String | Double quotes | `"hello world"` |

**Strings have no escape sequences** (mobile-friendly design). All characters between quotes are literal. To include special characters like quotes or newlines, use the `string()` function with character constants (`qt`, `nl`, `tab`, `ret`). See [String Operations](#string-operations).

### Escaping Literal Brackets

- `[[` produces a literal `[`
- `]]` produces a literal `]`

### Directive Structure

Directives are enclosed in square brackets: `[...]`

- Directives may span multiple lines (ends at closing `]`)
- Multi-line directives: lines before closing `]` should be indented for readability
- Multiple directives allowed per line: `text [dir1] more text [dir2]`
- **Directive identity**: Each directive is identified by its position (line number + character offset), not just its text. This means multiple identical directives (e.g., two `[date]` on different lines) are tracked and cached independently.

### Comments

Hash comments are supported within directives:
```
[x: 5  # this assigns 5 to x
 y: 10 # this assigns 10 to y
 add(x, y)]
```
Everything after `#` to end of line is ignored.

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
[a(b c, d)]      # Space-nesting inside parens: a(b(c), d)
```

**Rule**: If a function takes 2+ arguments, parentheses are required.

Space-separated nesting still applies inside parentheses: `[a(b c, d)]` means `a(b(c), d)`.

### Invalid Syntax

```
[a, b]           # Invalid: comma separates args, not calls
[a b c, d]       # Invalid: ambiguous binding with mixed syntax
```

### The Colon Operator (Context-Dependent)

The colon `:` has two meanings based on context. Whitespace around `:` is allowed and ignored.

**Variable definition** (standalone):
```
[x: 5]                    # Define local variable x = 5
[.path: "2026-01-01"]     # Set current note's path property
[obj.prop: value]         # Set property on object
```

**Named argument** (after function name):
```
[fcn param1:arg1]                    # Call fcn with named param
[fcn param1: arg1]                   # Space after colon is allowed
[fcn(param1:arg1, param2:arg2)]      # Multiple named params
```

### Statement Separation

Semicolon separates multiple statements within a directive:
```
[x: 5; y: 10; add(x, y)]    # Define x, define y, return sum
```

**Variable scope**: Local variables exist only within their enclosing `[...]` directive.

### Reserved Words

All built-in function and constant names are reserved and cannot be used as variable names. This includes: `date`, `time`, `datetime`, `find`, `new`, `if`, `later`, `run`, `lambda`, `schedule`, `refresh`, `view`, `button`, `sort`, `first`, `list`, `string`, `qt`, `nl`, `tab`, `ret`, `pattern`, `true`, `false`, `undefined`, `empty`, and all comparison/arithmetic function names.

### The Dot Operator: Note Hierarchy Navigation

The dot operator provides access to notes in the hierarchy relative to the directive's location.

**Current line (the note containing the directive):**
- `[.]` refers to the current line's note
- `[.prop]` reads a property from the current line
- `[.prop: value]` writes a property to the current line

**General property access:**
- `[obj.prop]` reads a property from any object

**Note hierarchy members:**

Notes have two members for hierarchy navigation: `up` and `root`. These follow standard member access syntax (not special syntax).

`up` - Navigate to ancestor notes:
- 0-arg form: `[.up]` or `[.up()]` returns the parent note (1 level up)
- 1-arg form: `[.up(n)]` returns the ancestor n levels up
- `[.up 0]`, `[.up(0)]` are equivalent to `[.]` (current line)
- `[.up]`, `[.up()]`, `[.up 1]`, `[.up(1)]` all return the parent
- `[.up.up]`, `[.up 2]`, `[.up(2)]` all return the grandparent
- Chainable: `[.up.up.up]` goes 3 levels up

`root` - Navigate to root ancestor:
- `[.root]` returns the root ancestor (top-level note with no parent)
- On a top-level note, `.root` returns that note (equivalent to `.`)

**Examples:**
- `[.up.path]` reads the parent note's path
- `[.root.name]` reads the root note's name (first line)
- `[.up(2).append("text")]` appends to the grandparent note
- `[n: 2; .up(n).path]` uses a variable for the level

**Boundary behavior:**
- If `.up(n)` exceeds the hierarchy depth, it returns `undefined` (similar to `first` on an empty list)
- Use `maybe(.up(n))` to safely handle cases where the ancestor may not exist

---

## Data Types

### Primitive Types

- **Number**: Integer or decimal values
- **String**: Text enclosed in double quotes
- **Boolean**: Result of comparison functions (`true`/`false`)
- **Datetime**: Combined date and time value (from `datetime` function or combining date and time); displays as `yyyy-MM-dd, HH:mm:ss` (e.g., "2026-01-25, 14:30:00")
- **Date**: Date-only value (from `date` function or parsing); displays as `yyyy-MM-dd` (e.g., "2026-01-25")
- **Time**: Time value with full precision including seconds/ms (from `time` function); displays as `HH:mm:ss` (e.g., "14:30:00"); supports same arithmetic as dates

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

Notes have the following properties:

| Property | Type | Access | Description |
|----------|------|--------|-------------|
| `id` | String | read | Firebase document ID |
| `path` | String | read/write | Unique path identifier (globally enforced) |
| `name` | String | read/write | First line of note content (the note's title) |
| `created` | DateTime | read | Creation timestamp |
| `modified` | DateTime | read | Last modification timestamp |
| `viewed` | DateTime | read | Last viewed timestamp |
| `up` | Note | read | Parent note; 0-arg returns parent, 1-arg `up(n)` returns ancestor n levels up; returns `undefined` if ancestor doesn't exist |
| `root` | Note | read | Root ancestor (top-level note with no parent) |

**Path character restrictions**: Paths are URL-safe only—alphanumeric characters, `-`, `_`, and `/` (for hierarchy). No spaces, brackets, or quotes.

**Current note reference**: The dot operator `[.]` always refers to the note containing the directive (the current line), even when that note's content is displayed via `view` in another note.

Examples:
```
[.path]                    # Read current line's path
[.path: "journal/2026"]    # Set current line's path
[.name]                    # Read current line's name (first line)
[.name: "New Title"]       # Set current line's name
[.modified]                # Read current line's last modified date
[.up.path]                 # Read parent note's path
[.root.name]               # Read root note's name
[.up.append("child note")] # Append to parent note
```

---

## Deferred Execution

### Auto-Propagation

When a function receives a deferred value as input, it automatically returns a deferred result. This allows natural composition:

```
[format later date]        # Returns deferred string (formatted date)
```

The `format` function sees a deferred date, so it returns a deferred string that will format the date when eventually evaluated.

### Variable Capture

Variables are captured at definition time. In a deferred expression, captured variables retain their values (which may themselves be deferred):

```
[x: later date; schedule(daily_at("9:00"), later x)]
# x captures a deferred date; when schedule runs, x resolves to that day's date
```

### `later` - Defer Next Token

`later` creates a deferred reference to the next token only:

```
[later x]                  # Defer evaluation of x
[later fcn]                # Defer the function call fcn
```

### `later[...]` - Defer a Scope

Square brackets after `later` create a deferred scope where nothing inside executes:

```
[later[.append date]]    # Entire expression is deferred
```

### `run` - Force Evaluation in Deferred Scope

Use `run` to evaluate the next token immediately within a deferred scope:

```
[later[.append run date]]    # date evaluates NOW; .append deferred
```

**Note**: `run` evaluates only the next token. `run a b` evaluates `a`; `b` remains deferred.

### `lambda[...]` - Lambda with Implicit Parameter

Creates a single-argument function where `i` is the implicit parameter:

```
[lambda[gt(i.modified, add_days(date, -1))]]    # Lambda checking if modified after yesterday
[lambda[parse_date(i.path)]]                    # Lambda extracting date from path
```

**Note**: The implicit parameter `i` is only available in this form.

### Lambda Invocation

Lambdas are invoked like builtins - a bare identifier calls the function:

```
[f: lambda[add(i, 1)]; f(5)]     # Calls f with i=5, returns 6
[f: lambda[mul(i, 2)]; f(10)]    # Calls f with i=10, returns 20
```

**Invocation semantics** (consistent with builtins):
- `[f]` where f is a lambda → tries to call with 0 args → **error** (lambda requires 1 argument)
- `[f(x)]` → calls lambda with `i` bound to `x`
- `[later f]` → returns the lambda without calling (for passing to other functions)

**Chained calls**:
```
[f: lambda[mul(i, 2)]; g: lambda[add(i, 10)]; f(g(5))]    # g(5)=15, f(15)=30
```

**Errors**:
```
[f: lambda[i]; f]           # Error: lambda requires 1 argument
[f: lambda[i]; f(1, 2)]     # Error: lambda requires 1 argument, got 2
[x: 5; x(10)]               # Error: Cannot call number as a function
```

### `lambda(params)[...]` - Lambda with Named Parameters

Creates a multi-argument function with explicit parameter names. The implicit `i` is not available in this form:

```
[lambda(a, b)[gt(a.modified, b.modified)]]    # Compare two notes by modified date
[lambda(note, threshold)[gt(note.viewed, threshold)]]    # Check if viewed after threshold
```

### `schedule` - Scheduled Execution

Schedules a one-time or recurring execution:

```
[schedule(daily_at("9:00"), later[.append date])]
[schedule(at(datetime(date, "14:30")), later[.append "Reminder!"])]
```

- **Parameter 1**: Date/time rule (see scheduling rules below)
- **Parameter 2**: A deferred reference; all nested `later` expressions resolve at execution time

**Execution environment**: Hybrid - cloud function for reliability, device background job as offline fallback.

**Schedule identity**: A schedule's identity is the hash of its directive text. This means:
- Saving a note multiple times doesn't create duplicate schedules
- Moving a directive to a different note preserves the same schedule
- Copying a directive to another note shows an error ("schedule already exists")
- Changing the directive text creates a new schedule (old one is cancelled)

**On note deletion**: User is warned about active schedules; if confirmed, schedules are cancelled.

**Retry policy**: Failed executions use exponential backoff (1m, 5m, 30m, etc.). Failures are visible in the schedule view.

**Schedule view**: A global screen shows all active schedules across notes. Individual notes with schedules also show an indicator; tapping reveals that note's schedule status.

### `refresh` - Reactive Re-execution

Re-executes when underlying data changes:

```
[refresh view(find(path: pattern(digit*4 "-" digit*2 "-" digit*2)))]
```

**Dependency tracking**: Static analysis at parse time identifies `find` patterns and note references. When any matching note changes, the directive re-executes on next view.

**Re-execution timing**: Refresh directives re-execute when the note is viewed, not immediately when dependencies change.

---

## Built-in Functions

**Dynamic vs Static Functions**: Functions are classified as either dynamic or static:
- **Dynamic functions** (e.g., `date`, `datetime`, `time`) can return different results on each call. Directives containing dynamic functions are re-executed when confirmed.
- **Static functions** (e.g., `add`, `qt`, `nl`) always return the same result for the same inputs. Their results are cached and reused.

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
| `datetime` | Returns current datetime |
| `date` | Returns current date (date-only, no time); displays as "yyyy-MM-dd" |
| `time` | Returns current time (full precision with seconds/ms) |
| `datetime(date, time)` | Combines date and time into datetime |
| `parse_datetime(string)` | Parses datetime from string (e.g., "2026-01-15 09:00") |
| `parse_date(string)` | Parses a date from string |
| `add_days(date, n)` | Add n days to date (use negative n for past dates) |
| `diff_days(d1, d2)` | Difference in days between dates |
| `before(d1, d2)` | True if d1 is before d2 |
| `after(d1, d2)` | True if d1 is after d2 |

**Timezone**: All date/time functions use device local timezone by default. Use optional `tz:` parameter for different timezone:
```
[date(tz: "America/New_York")]
[datetime(tz: "UTC")]
```

**Note**: Relative dates are derived via arithmetic, not constants:
- Yesterday: `add_days(date, -1)`
- Last week: `add_days(date, -7)`
- Tomorrow: `add_days(date, 1)`

### Date/Time Rules (for `schedule`)

| Rule | Description |
|------|-------------|
| `at(datetime)` | Triggers once at the specified datetime |
| `daily_at(time)` | Triggers daily at the specified time |

### String Operations

**`string(...)`** - Concatenation:

Inside `string()`, tokens are concatenated. Function calls require explicit parentheses:

```
[string("Hello " name ", today is " date)]
# Concatenates: "Hello " + name + ", today is " + date
```

**Character constants** - For special characters (mobile-friendly, no escape sequences):

| Constant | Character | Description |
|----------|-----------|-------------|
| `qt` | `"` | Double quote |
| `nl` | newline | Line break |
| `tab` | tab | Tab character |
| `ret` | carriage return | Carriage return |

Examples:

```
[string("He said " qt "hello" qt " to me")]
# Produces: He said "hello" to me

[string("Line 1" nl "Line 2")]
# Produces two lines of text

[string("Col1" tab "Col2" tab "Col3")]
# Produces tab-separated columns
```

### List Operations

| Function | Description |
|----------|-------------|
| `list(a, b, c, ...)` | Create a list |
| `first(list)` | Returns first item, or `undefined` if list is empty |
| `sort(list, key:lambda, order:asc)` | Sort list; `key` extracts sort value; `order` is `asc` or `desc` |

**Note**: `find` returns an empty list (not `undefined`) when no notes match. Use `maybe(first(find(...)))` to safely get a single note.

### Note Operations

#### Note collection operations
| Function                             | Description                                                         |
|--------------------------------------|---------------------------------------------------------------------|
| `new(path:p, content:c)`             | Create a new note; returns the note; errors if path exists          |
| `maybe_new(path:p, maybe_content:c)` | Idempotently ensure note exists; returns existing or new note       |
| `find(path:pattern, where:lambda)`   | Search for notes; returns list (empty if none match)                |

#### Single note operations
| Function | Description |
|----------|-------------|
| `.append(text)` | Append line to note; returns the note |

**Note**: Currently only `.append` is supported for note mutation. Additional edit operations may be added later.

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
- `path`: A string or pattern to match against note paths
- `name`: A string or pattern to match against the note's name (first line of content)
- `where`: A lambda predicate for filtering

**Returns**: A list of matching notes (empty list if none found).

**Examples:**
```
[find(path: "2026-01-15")]                                    # Exact path match
[find(path: pattern(digit*4 "-" digit*2 "-" digit*2))]        # All ISO date paths
[find(name: "Shopping List")]                                 # Exact name match
[find(name: pattern("Meeting" any*(0..)))]                    # Names starting with "Meeting"
[find(where: lambda[after(i.modified, add_days(date, -1))])]  # Modified after yesterday
[find(path: pattern("journal/" any*(1..)), where: lambda[before(i.created, add_days(date, -7))])]
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
[button("Add today's date", later[.append date])]
```

- **Parameter 1**: Label text (string)
- **Parameter 2**: Action to execute (typically a deferred reference)

**Display**: The directive is replaced with a tappable button showing the label. A secondary button allows editing the directive.

**Error handling**: When button action fails:
1. Button changes color to orange to indicate error state
2. A secondary button appears; tapping it shows the error details in a dialog

### `view` - Inline Note Content

Dynamically fetches and inlines content from other notes:

```
[view(sort(find(path: pattern(digit*4 "-" digit*2 "-" digit*2)), key: lambda[parse_date(i.path)], order: desc))]
```

- **Parameter**: List of notes to display (use `sort()` if ordering is needed)

**Display**: Notes' content is inlined as raw text, separated by dividers. No path headers are shown because the first line of each note serves as its title by convention.

**Error handling**: If a viewed note's directive fails, the error appears inline where that directive would render; other content still displays.

**Recursion**: Viewed notes' directives also execute, including nested `view` directives.

**Circular dependency**: If a view creates a cycle (A views B views A), an error is shown inline.

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
- **State persistence**: Collapsed/expanded state is saved and restored when the note is reloaded
- **Empty results**: When a directive evaluates to an empty string, a vertical dashed line placeholder is displayed to provide a tappable target for editing

### Caching

Results are cached using a hybrid approach:
- Primary: Cached in Firestore alongside the note
- Fallback: Local device cache for offline access

### Manual Re-execution

For non-`refresh` directives, users can manually re-execute to update the cached result.

### Directive Independence

Each directive in a note executes independently. Directives cannot reference other directives' results within the same note. Use note properties to share state if needed.

---

## Error and Warning Handling

### Directive States

Directives can be in one of three visual states:

| State | Border Color | Meaning |
|-------|--------------|---------|
| Success | Green dashed | Executed successfully with displayable result |
| Warning | Orange dashed | Executed but produced no meaningful effect |
| Error | Red dashed | Failed to parse or execute |

### No-Effect Warnings

Some values cannot be meaningfully displayed or stored as top-level directive results. These produce warnings rather than errors:

| Value Type | Warning Message |
|------------|-----------------|
| `lambda[...]` | "Uncalled lambda has no effect" |
| `pattern(...)` | "Unused pattern has no effect" |

**Resolution**: These values are meant to be passed to functions:
- Lambdas → `find(where: lambda[...])`, `sort(key: lambda[...])`
- Patterns → `find(path: pattern(...))`, `matches(str, pattern(...))`

### Parse Errors

Shown inline when the cursor leaves the directive (on focus-out), before save.

### Execution Errors

Shown inline after save where the result would appear.

### Global Error Navigation

A navigation button at the bottom lists execution errors across all notes.

### Specific Error Conditions

| Condition | Behavior |
|-----------|----------|
| `new` with existing path | Error: duplicate path |
| `schedule` directive copied to another note | Error: schedule already exists (same hash) |
| Access `undefined` property | Error (except in boolean context—`undefined` is falsy) |
| Circular `view` dependency | Error: cycle detected |
| Network failure in `schedule` | Exponential backoff retry; failure visible in schedule view |

---

## Complete Examples

### Set Note Path
```
[.path: "2026-01-01"]
```
Sets the current note's path to "2026-01-01".

### Daily Date Append
```
[schedule(daily_at("06:00"), later[.append date])]
```
Every day at 6 AM, append the current date to the end of the note.

### Daily Note Creation
```
[schedule(daily_at("00:01"), later[
  maybe_new(path: date, maybe_content: string("# " date))
])]
```
Every day just after midnight, ensure a note exists for that day's date.

### One-Time Reminder
```
[schedule(at(datetime(add_days(date, 1), "09:00")), later[.append "Don't forget!"])]
```
Schedule a one-time reminder for 9 AM tomorrow.

### Dynamic Journal View
```
[refresh view(sort(
  find(path: pattern(digit*4 "-" digit*2 "-" digit*2)),
  key: lambda[parse_date(i.path)],
  order: desc
))]
```
Show all notes with ISO date paths, ordered most recent first. Refresh when any matching note changes.

### Quick Action Button
```
[button("New Journal Entry", later[new(path: date, content: string("# " date))])]
```
A button that creates a new journal note for today when tapped.

### Conditional Append
```
[if(eq(.path, "inbox"), .append("Processed"), .append("Skipped"))]
```
Append "Processed" if this note's path is "inbox", otherwise append "Skipped".

### Note Hierarchy Navigation
```
[.path]           # Current line's path
[.up.path]        # Parent note's path
[.up.up.path]     # Grandparent note's path
[.root.path]      # Root note's path (top-level ancestor)
[.up.append(string("Child: " .path))]  # Append current line's path to parent
[n: 2; .up(n).path]  # Go up n levels using a variable
```
Access properties and methods on notes at different levels of the hierarchy.

### Safe Single Note Lookup
```
[target: maybe(first(find(path: "inbox")));
 if(target, target.append("New item"), .append("Inbox not found"))]
```
Find the inbox note and append to it, or show a message if not found.

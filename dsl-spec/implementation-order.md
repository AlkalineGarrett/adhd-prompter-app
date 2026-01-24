# DSL Implementation Order

Vertical slice approach: each milestone delivers end-to-end functionality (parse → execute → display).

**Sprint targets:**
```
[schedule daily_at parse_time "01:00", later[
  maybe_new(path: iso8601 date, maybe_content: iso8601 date)
]]

[refresh view sort
  find(path: pattern(digit*4 "-" digit*2 "-" digit*2)),
  key: lambda[parse_date i.path],
  order: desc
]
```

---

## Milestone 1: Literals

`[1]`, `["hello"]`

- Lexer: brackets, numbers, strings
- Parser: single-token directive
- Executor: literal evaluation
- Display: inline result, collapsed/expanded toggle

## Milestone 2: Function Calls

`[date]`, `[iso8601 date]`

- Lexer: identifiers
- Parser: single identifier as no-arg function call
- Parser: space-separated tokens as nested calls (right-to-left)
- Executor: `date`, `iso8601`

## Milestone 3: Parentheses and Named Arguments

`[add(1, 2)]`, `[foo(bar: "baz")]`

- Parser: parentheses grouping, comma-separated arguments
- Parser: named arguments with colon
- Executor: arithmetic functions

## Milestone 4: Patterns

`[pattern(digit*4 "-" digit*2 "-" digit*2)]`

- Parser: pattern syntax within `pattern()`
- Executor: character classes (`digit`, `letter`, `any`, etc.)
- Executor: quantifiers (`*4`, `*any`, `*(1..)`, etc.)
- Executor: pattern type

## Milestone 5: Find with Pattern

`[find(path: pattern(digit*4 "-" digit*2 "-" digit*2))]`

- Executor: `find` with `path:` named argument
- Executor: pattern matching against note paths
- Returns list of notes

## Milestone 6: Note Properties

`[.path]`, `[i.path]`, `[parse_date i.path]`

- Parser: dot operator for property access
- Executor: note property reads (`path`, `content`, `created`, `modified`, `viewed`)
- Executor: `parse_date`

## Milestone 7: Lambda

`[lambda[parse_date i.path]]`

- Parser: `lambda[...]` syntax
- Executor: lambda type with implicit `i` parameter
- Integration with `find(where: lambda[...])`

## Milestone 8: Sort

`[sort find(...), key: lambda[parse_date i.path], order: desc]`

- Executor: `sort` with `key:` and `order:` named arguments
- Executor: `asc`/`desc` ordering

## Milestone 9: View

`[view find(...)]`, `[view sort find(...), ...]`

- Executor: `view` function
- UI: inline note content with dividers
- Error handling: circular dependency detection

## Milestone 10: Refresh ✓ Target 2

`[refresh view sort find(path: pattern(...)), key: lambda[...], order: desc]`

- Parser: `refresh` as top-level modifier
- Dependency tracking via static analysis
- Re-execution on view when dependencies change

## Milestone 11: Deferred Execution

`[later date]`, `[later[iso8601 date]]`

- Parser: `later` (single token)
- Parser: `later[...]` (deferred scope)
- Executor: deferred reference type
- Executor: variable capture in deferred expressions

## Milestone 12: Note Mutation

`[maybe_new(path: "x", maybe_content: "y")]`

- Parser: current note property assignment `[.path: "value"]`
- Executor: `new`, `maybe_new`
- Executor: `.append`
- Error handling: duplicate path error

## Milestone 13: Schedule ✓ Target 1

`[schedule daily_at parse_time "01:00", later[maybe_new(...)]]`

- Executor: `parse_time`
- Executor: `daily_at`, `at` rules
- Executor: `schedule` function
- Backend: schedule storage, recurring execution
- Schedule identity (hash-based deduplication)
- UI: schedule indicator on note

---

## Later Milestones

### Variables and Conditionals

`[x: 5; add(x, 3)]`, `[if(gt(1, 0), "yes", "no")]`

- Parser: colon for variable definition, semicolon statement separation
- Executor: variable scoping, comparison functions, `if`

### List Operations

`[first(find(...))]`, `[list(1, 2, 3)]`

- Executor: `first`, `list`
- Special values: `undefined`, `empty`
- Executor: `maybe`

### Button

`[button("Click me", later[...])]`

- UI: button rendering, tap to execute
- Button error state display

### Polish

- Parse error display (on focus-out)
- Global error navigation
- Schedule view (all active schedules)
- Retry policy for failed schedule executions
- Comments within directives
- Bracket escaping
- Timezone support
- `run` for immediate evaluation in deferred scope
- `lambda(params)[...]` with named parameters

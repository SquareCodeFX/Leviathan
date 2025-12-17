### Leviathan SlashCommand System — Documentation Index

Welcome to the complete documentation of Leviathan's SlashCommand system. This wiki explains every feature with step‑by‑step examples and API references.

Start here:

- [SlashCommand Overview](SlashCommand-Overview.md)
- [Builder API](Builder-API.md)
- [Arguments (parsers, ranges, choices, validators)](Arguments.md)
  - [Type-Safe Choice Arguments (ChoiceArg)](Arguments.md#type-safe-choice-arguments-choicearg)
  - [Variadic Arguments (List Types)](Arguments.md#variadic-arguments-list-types)
- [Flags and Key-Value Options](Flags-KeyValues.md)
- [Guards, Permissions, and Cross-Argument Validation](Guards-Validation-Permissions.md)
  - [Permission Cascade Modes](Guards-Validation-Permissions.md#permission-cascade-modes)
  - [Argument Dependencies](Guards-Validation-Permissions.md#argument-dependencies)
- [Async Execution and Cooldowns](Async-Cooldowns.md)
- [Help, Usage, and Tab Completion](Help-TabCompletion.md)
- [Exceptions and Error Handling](Exceptions-ErrorHandling.md)
- [Pagination Utilities](Pagination.md)
- [Cookbook: Common Recipes](Examples.md)

Advanced Topics:

- [Advanced Completions (async, dynamic, permission-filtered)](Advanced-Completions.md)
- [Progress Tracking (ProgressBar, ProgressReporter)](Progress-Tracking.md)
- [CommandContext API (accessors, streaming, type conversion)](CommandContext-API.md)
- [Execution Hooks (before/after hooks, logging, metrics)](Execution-Hooks.md)
- [Parsing API (split parsing from execution)](Parsing-API.md)
  - [Quoted String Parsing](Parsing-API.md#quoted-string-parsing)
- [Interactive Prompting (conversational argument gathering)](Interactive-Prompting.md)

If you're new, read the Overview and then Builder API. The rest are deep dives you can consult as needed.

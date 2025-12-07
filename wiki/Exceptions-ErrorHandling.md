### Exceptions and Error Handling

Leviathan centralizes failures (parsing, guards, cooldowns, permission, execution) and routes them through an `ExceptionHandler`. You can customize messages and diagnostics.

#### Error flow

During `SlashCommand#execute(...)` the following can fail:

- Permission/guard checks
- Argument parsing and validation
- Cross-argument validators
- Cooldown checks
- Action execution (sync or async)

On failure, `sendErrorMessage(sender, ErrorType, message, exception)` is invoked. If `sendErrors(true)` is enabled and a `MessageProvider` is set, user-friendly messages are sent to the sender. Otherwise, minimal feedback is provided.

#### ExceptionHandler

- Interface: `ExceptionHandler`
- You can install a custom instance via `builder.exceptionHandler(handler)`.
- Typical responsibilities: mapping exceptions to `ErrorType`, formatting messages, adding hints.

#### DetailedExceptionHandler

`DetailedExceptionHandler` enriches reports with JVM/thread diagnostics to aid debugging production issues.

Ways to enable:

- `builder.detailedExceptionHandler(JavaPlugin plugin)` — convenience that wires up collectors.
- Or `builder.detailedExceptionHandler(DetailedExceptionHandler instance)`.

Related classes:

- `JvmInfoCollector` — Captures JVM properties, memory usage, GC info.
- `ThreadDumpCollector` — Captures stack traces of running threads.

#### ErrorType

Categorization enum used throughout error handling (permission, invalid usage, cooldown, internal error, etc.). This drives how messages are built and which suggestions to show.

#### ExceptionSuggestionRegistry

Register hints for common errors to improve the UX:

- Suggest missing flags or key‑values when similar names exist.
- Recommend the closest subcommand when `fuzzySubcommandMatching(true)`.

#### MessageProvider

- `MessageProvider` allows full control over user‑facing text.
- Use `DefaultMessageProvider` or provide your own implementation for localization and customization.

Example customizing error messages:

```java
builder.messages(new MessageProvider() {
    @Override public String noPermission(String perm) { return "You lack: " + perm; }
    @Override public String invalidArgument(String name, String reason) { return "Arg '" + name + "': " + reason; }
    // implement other methods as needed
});
```

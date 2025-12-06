/**
 * Tab completion system for commands.
 * <p>
 * This package provides a flexible and powerful tab completion system that supports:
 * <ul>
 *   <li><b>Predefined completions</b> - Static lists of completion suggestions</li>
 *   <li><b>Dynamic completions</b> - Runtime-generated completions based on context</li>
 *   <li><b>Parser-based completions</b> - Automatic completions from argument parsers</li>
 *   <li><b>Smart filtering</b> - Intelligent matching with exact, prefix, and substring support</li>
 *   <li><b>Permission-based filtering</b> - Hide completions based on permissions</li>
 *   <li><b>Context-aware completions</b> - Access to previously parsed arguments</li>
 * </ul>
 * 
 * <h2>Using Predefined Completions</h2>
 * <pre>{@code
 * SlashCommand.builder("example")
 *     .argStringWithCompletions("action", "start", "stop", "restart")
 *     .executes((sender, ctx) -> {
 *         String action = ctx.get("action", String.class);
 *         sender.sendMessage("Action: " + action);
 *     })
 *     .register(plugin);
 * }</pre>
 * 
 * <h2>Using Dynamic Completions</h2>
 * <pre>{@code
 * SlashCommand.builder("config")
 *     .argString("key", ArgContext.builder()
 *         .withDynamicCompletions(ctx -> {
 *             // Return completions based on sender or previous args
 *             if (ctx.sender().hasPermission("admin.config")) {
 *                 return List.of("debug", "verbose", "advanced");
 *             }
 *             return List.of("basic", "simple");
 *         })
 *         .build())
 *     .executes((sender, ctx) -> {
 *         // handle command
 *     })
 *     .register(plugin);
 * }</pre>
 * 
 * <h2>Using Helper Factories</h2>
 * <pre>{@code
 * // Permission-filtered completions
 * ArgContext.DynamicCompletionProvider provider = 
 *     ArgContext.DynamicCompletionProvider.permissionFiltered(
 *         List.of("admin", "moderator", "user"),
 *         "myplugin.role."
 *     );
 * 
 * // Context-based completions
 * ArgContext.DynamicCompletionProvider contextProvider = 
 *     ArgContext.DynamicCompletionProvider.contextBased(ctx -> {
 *         // Access previously parsed arguments
 *         Object previous = ctx.parsedArgsSoFar().get("previousArg");
 *         return generateCompletionsBasedOn(previous);
 *     });
 * 
 * // Combined completions from multiple sources
 * ArgContext.DynamicCompletionProvider combined = 
 *     ArgContext.DynamicCompletionProvider.combined(
 *         provider1, provider2, provider3
 *     );
 * }</pre>
 * 
 * <h2>Range Hints for Numeric Arguments</h2>
 * <pre>{@code
 * SlashCommand.builder("setlevel")
 *     .argIntRange("level", 1, 100)  // Shows "[1-100]" hint in tab completion
 *     .executes((sender, ctx) -> {
 *         int level = ctx.get("level", Integer.class);
 *         sender.sendMessage("Level set to " + level);
 *     })
 *     .register(plugin);
 * }</pre>
 * 
 * <h2>Convenience Methods</h2>
 * The {@link de.feelix.leviathan.command.argument.ArgContext.Builder} provides several
 * convenience methods for common completion patterns:
 * <ul>
 *   <li>{@code addCompletion(String)} - Add a single completion</li>
 *   <li>{@code addCompletions(String...)} - Add multiple completions</li>
 *   <li>{@code completionsFromEnum(Class)} - Generate completions from an enum</li>
 *   <li>{@code rangeHint(int, int)} - Show numeric range hint</li>
 * </ul>
 * 
 * <h2>Smart Filtering</h2>
 * The completion system uses smart filtering to provide the best matches:
 * <ol>
 *   <li>Exact matches (case-insensitive) are shown first</li>
 *   <li>Prefix matches are shown second</li>
 *   <li>Substring matches are shown last for better discoverability</li>
 * </ol>
 * 
 * @see de.feelix.leviathan.command.argument.ArgContext
 * @see de.feelix.leviathan.command.argument.ArgContext.DynamicCompletionProvider
 * @see de.feelix.leviathan.command.completion.DynamicCompletionContext
 * @see de.feelix.leviathan.command.completion.TabCompletionHandler
 */
package de.feelix.leviathan.command.completion;

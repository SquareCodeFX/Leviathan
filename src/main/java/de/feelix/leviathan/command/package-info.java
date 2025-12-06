/**
 * Core command framework implementation.
 * <p>
 * This package contains all the essential components for building and executing commands:
 * <ul>
 *   <li><b>Core</b> - {@link de.feelix.leviathan.command.core.SlashCommand} and {@link de.feelix.leviathan.command.core.SlashCommandBuilder}</li>
 *   <li><b>Arguments</b> - Type-safe argument parsing with {@link de.feelix.leviathan.command.argument}</li>
 *   <li><b>Completion</b> - Tab completion with {@link de.feelix.leviathan.command.completion}</li>
 *   <li><b>Validation</b> - Argument validation with {@link de.feelix.leviathan.command.validation}</li>
 *   <li><b>Guards</b> - Permission and custom guards with {@link de.feelix.leviathan.command.guard}</li>
 *   <li><b>Cooldowns</b> - Command cooldowns with {@link de.feelix.leviathan.command.cooldown}</li>
 *   <li><b>Messages</b> - Customizable messages with {@link de.feelix.leviathan.command.message}</li>
 *   <li><b>Async</b> - Asynchronous execution with {@link de.feelix.leviathan.command.async}</li>
 * </ul>
 * 
 * <h3>Package Structure</h3>
 * <pre>
 * command/
 * ├── core/           - Core command and builder classes
 * ├── argument/       - Argument parsing, validation, and context
 * ├── completion/     - Tab completion handlers and providers
 * ├── validation/     - Validation helpers and cross-argument validators
 * ├── guard/          - Guard predicates for command access control
 * ├── cooldown/       - Cooldown management (per-user and per-server)
 * ├── message/        - Message providers for customizable user feedback
 * ├── async/          - Asynchronous execution support
 * ├── error/          - Error types and exception handlers
 * └── mapping/        - Option mapping and type conversion utilities
 * </pre>
 * 
 * <h3>Building Commands</h3>
 * Commands are built using the fluent builder pattern:
 * <pre>{@code
 * SlashCommand.builder("mycommand")
 *     .description("Example command")
 *     .argString("name")
 *     .argInt("age", ArgContext.builder()
 *         .intRange(1, 100)
 *         .withPermission("admin.age")
 *         .build())
 *     .executes((sender, ctx) -> {
 *         String name = ctx.get("name", String.class);
 *         int age = ctx.get("age", Integer.class);
 *         sender.sendMessage("Hello " + name + ", age " + age);
 *     })
 *     .register(plugin);
 * }</pre>
 * 
 * @see de.feelix.leviathan.command.core.SlashCommand
 * @see de.feelix.leviathan.command.core.SlashCommandBuilder
 */
package de.feelix.leviathan.command;

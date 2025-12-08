/**
 * Progress bar system for visualizing command execution progress.
 * <p>
 * This package provides a fluent API for creating and using progress bars
 * in async command actions. The progress bar system integrates seamlessly
 * with the SlashCommand system's existing Progress interface.
 * <p>
 * Key classes:
 * <ul>
 *   <li>{@link de.feelix.leviathan.command.progress.ProgressBar} - Main progress bar class with builder pattern</li>
 *   <li>{@link de.feelix.leviathan.command.progress.ProgressReporter} - Wrapper for automatic progress bar rendering</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * SlashCommand.create("process")
 *     .description("Process data with progress indicator")
 *     .async()
 *     .asyncAction((sender, ctx, token, progress) -> {
 *         ProgressBar bar = ProgressBar.builder()
 *             .total(100)
 *             .width(30)
 *             .prefix("Processing: ")
 *             .showPercentage(true)
 *             .showRatio(true)
 *             .build();
 *
 *         ProgressReporter reporter = bar.reporter(progress);
 *
 *         for (int i = 0; i <= 100; i++) {
 *             if (token.isCancelled()) break;
 *             // Do work...
 *             reporter.report(i, "Step " + i);
 *             Thread.sleep(100);
 *         }
 *
 *         sender.sendMessage("§aProcessing complete!");
 *     })
 *     .build()
 *     .register(plugin);
 * }</pre>
 */
package de.feelix.leviathan.command.progress;

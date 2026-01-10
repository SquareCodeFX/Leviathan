/**
 * Batch operations support for the Leviathan command framework.
 * <p>
 * This package provides functionality for executing commands on multiple targets
 * simultaneously, with support for:
 * <ul>
 *   <li>Sequential and parallel execution</li>
 *   <li>Progress tracking and reporting</li>
 *   <li>Configurable error handling (continue or stop on failure)</li>
 *   <li>Timeout support</li>
 *   <li>Result aggregation and summary generation</li>
 * </ul>
 *
 * <h2>Core Classes</h2>
 * <ul>
 *   <li>{@link de.feelix.leviathan.command.batch.BatchAction} - Action to execute for each target</li>
 *   <li>{@link de.feelix.leviathan.command.batch.BatchContext} - Execution context with shared state</li>
 *   <li>{@link de.feelix.leviathan.command.batch.BatchConfig} - Configuration for batch execution</li>
 *   <li>{@link de.feelix.leviathan.command.batch.BatchEntry} - Result for a single target</li>
 *   <li>{@link de.feelix.leviathan.command.batch.BatchResult} - Aggregated results</li>
 *   <li>{@link de.feelix.leviathan.command.batch.BatchExecutor} - Executor for batch operations</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Define a batch command
 * SlashCommand.create("heal-all")
 *     .description("Heal multiple players")
 *     .argVariadic(VariadicArg.of("players", ArgParsers.playerParser()))
 *     .argInt("amount", ArgContext.builder().optional(true).defaultValue(20).build())
 *     .batch(batch -> batch
 *         .parallel(true)
 *         .maxBatchSize(50)
 *         .continueOnFailure(true)
 *         .showProgress(true)
 *     )
 *     .executesBatch((target, ctx) -> {
 *         Player player = (Player) target;
 *         int amount = ctx.argInt("amount", 20);
 *         player.setHealth(Math.min(player.getHealth() + amount, player.getMaxHealth()));
 *     })
 *     .build();
 * }</pre>
 *
 * @see de.feelix.leviathan.command.batch.BatchExecutor
 * @see de.feelix.leviathan.command.batch.BatchResult
 */
package de.feelix.leviathan.command.batch;

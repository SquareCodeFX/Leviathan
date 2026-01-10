/**
 * Wizard system for the Leviathan command framework.
 * <p>
 * This package provides an interactive, branching wizard system for complex
 * command interactions. Wizards guide users through a series of questions
 * and choices to collect information and perform actions.
 *
 * <h2>Features</h2>
 * <ul>
 *   <li><b>Decision Trees</b> - Create complex branching logic based on user choices</li>
 *   <li><b>Multiple Node Types</b> - Question, input, action, and terminal nodes</li>
 *   <li><b>Variable Collection</b> - Collect and store user input for later use</li>
 *   <li><b>Conditional Options</b> - Show/hide options based on previous choices</li>
 *   <li><b>Navigation</b> - Go back to previous nodes, cancel anytime</li>
 *   <li><b>Timeout Handling</b> - Automatic session expiration</li>
 * </ul>
 *
 * <h2>Core Classes</h2>
 * <ul>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardDefinition} - Defines the complete wizard structure</li>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardNode} - A single node in the decision tree</li>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardOption} - A selectable option within a node</li>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardContext} - Runtime context with collected variables</li>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardSession} - An active wizard session for a player</li>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardManager} - Manages all active sessions</li>
 *   <li>{@link de.feelix.leviathan.command.wizard.WizardAction} - Action to execute at terminal nodes</li>
 * </ul>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Define a wizard
 * WizardDefinition wizard = WizardDefinition.builder("setup-kit")
 *     .description("Create a custom kit")
 *
 *     // Start node: Choose kit type
 *     .question("start", "What type of kit do you want?", node -> node
 *         .option("armor", "Armor Kit", "armor-type")
 *         .option("weapon", "Weapon Kit", "weapon-type")
 *         .option("food", "Food Kit", "food-amount"))
 *
 *     // Armor branch
 *     .question("armor-type", "What armor type?", node -> node
 *         .option("leather", "Leather Armor", "save")
 *         .option("iron", "Iron Armor", "save")
 *         .option("diamond", "Diamond Armor", "save"))
 *
 *     // Weapon branch
 *     .question("weapon-type", "What weapon?", node -> node
 *         .option("sword", "Sword", "save")
 *         .option("bow", "Bow", "save")
 *         .option("axe", "Axe", "save"))
 *
 *     // Food branch - input node
 *     .input("food-amount", "amount", "How many food items?",
 *            ArgParsers.intParser(), "save")
 *
 *     // Save confirmation
 *     .question("save", "Save this kit?", node -> node
 *         .option(WizardOption.builder("yes")
 *             .displayText("Yes, save it!")
 *             .action(ctx -> {
 *                 String type = ctx.getString("type", "unknown");
 *                 ctx.player().sendMessage("Kit saved: " + type);
 *             })
 *             .requiresConfirmation(true)
 *             .build())
 *         .option("no", "No, start over", "start"))
 *
 *     .build();
 *
 * // Start the wizard for a player
 * SlashCommand.create("create-kit")
 *     .wizard(wizard)
 *     .build();
 * }</pre>
 *
 * @see de.feelix.leviathan.command.wizard.WizardDefinition
 * @see de.feelix.leviathan.command.wizard.WizardManager
 */
package de.feelix.leviathan.command.wizard;

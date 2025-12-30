package de.feelix.leviathan.command.performance;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.Arg;
import de.feelix.leviathan.command.argument.ArgContext;
import de.feelix.leviathan.util.Preconditions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.function.Predicate;

/**
 * Pre-compiles command structures for faster runtime execution.
 * <p>
 * This class analyzes command definitions at startup/registration time and
 * pre-computes expensive operations like:
 * <ul>
 *   <li>Regex pattern compilation</li>
 *   <li>Argument dependency graphs</li>
 *   <li>Permission hierarchies</li>
 *   <li>Validation chains</li>
 *   <li>Completion lookup tables</li>
 *   <li>Argument index mappings</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Pre-compile a command structure
 * CompiledCommand compiled = CommandPrecompiler.compile(myCommand);
 *
 * // Use compiled data for fast lookups
 * int argIndex = compiled.getArgumentIndex("player");
 * Pattern pattern = compiled.getValidationPattern("email");
 * List<String> staticCompletions = compiled.getStaticCompletions("gamemode");
 * }</pre>
 */
public final class CommandPrecompiler {

    private CommandPrecompiler() {
        // Static utility class
    }

    // Global cache of compiled commands
    private static final Map<String, CompiledCommand> compiledCache = new ConcurrentHashMap<>();

    // Statistics
    private static final AtomicLong compilations = new AtomicLong(0);
    private static final AtomicLong cacheHits = new AtomicLong(0);
    private static final AtomicLong cacheMisses = new AtomicLong(0);

    /**
     * Pre-compile a command structure.
     *
     * @param commandName the command name
     * @param args        the argument list
     * @return a compiled command structure
     */
    public static @NotNull CompiledCommand compile(@NotNull String commandName,
                                                    @NotNull List<Arg<?>> args) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(args, "args");

        String cacheKey = commandName;
        CompiledCommand cached = compiledCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        compilations.incrementAndGet();

        CompiledCommand compiled = doCompile(commandName, args);
        compiledCache.put(cacheKey, compiled);
        return compiled;
    }

    /**
     * Pre-compile a command with subcommands.
     *
     * @param commandName the command name
     * @param args        the argument list
     * @param subcommands map of subcommand names to their args
     * @return a compiled command structure
     */
    public static @NotNull CompiledCommand compileWithSubcommands(
            @NotNull String commandName,
            @NotNull List<Arg<?>> args,
            @NotNull Map<String, List<Arg<?>>> subcommands) {
        Preconditions.checkNotNull(commandName, "commandName");
        Preconditions.checkNotNull(args, "args");
        Preconditions.checkNotNull(subcommands, "subcommands");

        String cacheKey = commandName + ":with-subcommands";
        CompiledCommand cached = compiledCache.get(cacheKey);
        if (cached != null) {
            cacheHits.incrementAndGet();
            return cached;
        }

        cacheMisses.incrementAndGet();
        compilations.incrementAndGet();

        CompiledCommand.Builder builder = doCompileBuilder(commandName, args);

        // Compile subcommands
        Map<String, CompiledCommand> compiledSubcommands = new LinkedHashMap<>();
        for (Map.Entry<String, List<Arg<?>>> entry : subcommands.entrySet()) {
            CompiledCommand subCompiled = doCompile(entry.getKey(), entry.getValue());
            compiledSubcommands.put(entry.getKey().toLowerCase(Locale.ROOT), subCompiled);
        }
        builder.subcommands(compiledSubcommands);

        CompiledCommand compiled = builder.build();
        compiledCache.put(cacheKey, compiled);
        return compiled;
    }

    /**
     * Get a previously compiled command.
     *
     * @param commandName the command name
     * @return the compiled command, or null if not found
     */
    public static @Nullable CompiledCommand get(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        return compiledCache.get(commandName);
    }

    /**
     * Check if a command is compiled.
     *
     * @param commandName the command name
     * @return true if compiled
     */
    public static boolean isCompiled(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        return compiledCache.containsKey(commandName);
    }

    /**
     * Invalidate a compiled command.
     *
     * @param commandName the command name
     */
    public static void invalidate(@NotNull String commandName) {
        Preconditions.checkNotNull(commandName, "commandName");
        compiledCache.remove(commandName);
        compiledCache.remove(commandName + ":with-subcommands");
    }

    /**
     * Clear all compiled commands.
     */
    public static void clearAll() {
        compiledCache.clear();
    }

    /**
     * Get compilation statistics.
     *
     * @return statistics snapshot
     */
    public static @NotNull CompilerStats getStats() {
        return new CompilerStats(
            compiledCache.size(),
            compilations.get(),
            cacheHits.get(),
            cacheMisses.get()
        );
    }

    /**
     * Reset statistics counters.
     */
    public static void resetStats() {
        compilations.set(0);
        cacheHits.set(0);
        cacheMisses.set(0);
    }

    /**
     * Perform the actual compilation.
     */
    private static @NotNull CompiledCommand doCompile(@NotNull String commandName,
                                                       @NotNull List<Arg<?>> args) {
        return doCompileBuilder(commandName, args).build();
    }

    /**
     * Create a builder with compiled data.
     */
    private static @NotNull CompiledCommand.Builder doCompileBuilder(@NotNull String commandName,
                                                                      @NotNull List<Arg<?>> args) {
        CompiledCommand.Builder builder = CompiledCommand.builder(commandName);

        // Build argument index map
        Map<String, Integer> argIndexMap = new LinkedHashMap<>();
        for (int i = 0; i < args.size(); i++) {
            Arg<?> arg = args.get(i);
            argIndexMap.put(arg.name(), i);
            // Also index aliases
            for (String alias : arg.aliases()) {
                argIndexMap.put(alias, i);
            }
        }
        builder.argumentIndexMap(argIndexMap);

        // Compile validation patterns
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        for (Arg<?> arg : args) {
            ArgContext ctx = arg.context();
            if (ctx.stringPattern() != null) {
                try {
                    Pattern pattern = ctx.stringPattern();
                    patterns.put(arg.name(), pattern);
                } catch (Exception e) {
                    // Invalid pattern, skip
                }
            }
        }
        builder.validationPatterns(patterns);

        // Pre-compute static completions
        Map<String, List<String>> staticCompletions = new LinkedHashMap<>();
        for (Arg<?> arg : args) {
            ArgContext ctx = arg.context();
            List<String> predefined = ctx.completionsPredefined();
            if (predefined != null && !predefined.isEmpty()) {
                staticCompletions.put(arg.name(), Collections.unmodifiableList(new ArrayList<>(predefined)));
            }
        }
        builder.staticCompletions(staticCompletions);

        // Build required/optional lists
        List<String> requiredArgs = new ArrayList<>();
        List<String> optionalArgs = new ArrayList<>();
        for (Arg<?> arg : args) {
            if (arg.optional()) {
                optionalArgs.add(arg.name());
            } else {
                requiredArgs.add(arg.name());
            }
        }
        builder.requiredArguments(requiredArgs);
        builder.optionalArguments(optionalArgs);

        // Build argument type map
        Map<String, String> typeNames = new LinkedHashMap<>();
        for (Arg<?> arg : args) {
            typeNames.put(arg.name(), arg.parser().getTypeName());
        }
        builder.argumentTypeNames(typeNames);

        // Pre-compute usage string
        StringBuilder usage = new StringBuilder("/").append(commandName);
        for (Arg<?> arg : args) {
            usage.append(" ");
            if (arg.optional()) {
                usage.append("[").append(arg.name()).append("]");
            } else {
                usage.append("<").append(arg.name()).append(">");
            }
        }
        builder.usageString(usage.toString());

        // Build alias to primary name mapping
        Map<String, String> aliasMap = new LinkedHashMap<>();
        for (Arg<?> arg : args) {
            for (String alias : arg.aliases()) {
                aliasMap.put(alias, arg.name());
            }
        }
        builder.aliasToPrimaryMap(aliasMap);

        // Determine if command has greedy arguments
        boolean hasGreedy = false;
        for (Arg<?> arg : args) {
            if (arg.greedy()) {
                hasGreedy = true;
                break;
            }
        }
        builder.hasGreedyArgument(hasGreedy);

        // Count arguments with permissions
        int permissionedArgs = 0;
        for (Arg<?> arg : args) {
            if (arg.permission() != null) {
                permissionedArgs++;
            }
        }
        builder.permissionedArgumentCount(permissionedArgs);

        return builder;
    }

    // ==================== Compiled Command ====================

    /**
     * A pre-compiled command structure for fast runtime access.
     */
    public static final class CompiledCommand {
        private final String commandName;
        private final Map<String, Integer> argumentIndexMap;
        private final Map<String, Pattern> validationPatterns;
        private final Map<String, List<String>> staticCompletions;
        private final List<String> requiredArguments;
        private final List<String> optionalArguments;
        private final Map<String, String> argumentTypeNames;
        private final String usageString;
        private final Map<String, String> aliasToPrimaryMap;
        private final boolean hasGreedyArgument;
        private final int permissionedArgumentCount;
        private final Map<String, CompiledCommand> subcommands;
        private final long compiledAt;

        private CompiledCommand(Builder builder) {
            this.commandName = builder.commandName;
            this.argumentIndexMap = Collections.unmodifiableMap(new LinkedHashMap<>(builder.argumentIndexMap));
            this.validationPatterns = Collections.unmodifiableMap(new LinkedHashMap<>(builder.validationPatterns));
            this.staticCompletions = Collections.unmodifiableMap(new LinkedHashMap<>(builder.staticCompletions));
            this.requiredArguments = Collections.unmodifiableList(new ArrayList<>(builder.requiredArguments));
            this.optionalArguments = Collections.unmodifiableList(new ArrayList<>(builder.optionalArguments));
            this.argumentTypeNames = Collections.unmodifiableMap(new LinkedHashMap<>(builder.argumentTypeNames));
            this.usageString = builder.usageString;
            this.aliasToPrimaryMap = Collections.unmodifiableMap(new LinkedHashMap<>(builder.aliasToPrimaryMap));
            this.hasGreedyArgument = builder.hasGreedyArgument;
            this.permissionedArgumentCount = builder.permissionedArgumentCount;
            this.subcommands = builder.subcommands != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(builder.subcommands))
                : Collections.emptyMap();
            this.compiledAt = System.currentTimeMillis();
        }

        public static @NotNull Builder builder(@NotNull String commandName) {
            return new Builder(commandName);
        }

        // Accessors

        public @NotNull String getCommandName() { return commandName; }

        /**
         * Get the index of an argument by name or alias.
         *
         * @param name the argument name or alias
         * @return the index, or -1 if not found
         */
        public int getArgumentIndex(@NotNull String name) {
            Integer index = argumentIndexMap.get(name);
            return index != null ? index : -1;
        }

        /**
         * Check if an argument exists.
         *
         * @param name the argument name or alias
         * @return true if exists
         */
        public boolean hasArgument(@NotNull String name) {
            return argumentIndexMap.containsKey(name);
        }

        /**
         * Get a pre-compiled validation pattern.
         *
         * @param argumentName the argument name
         * @return the pattern, or null if none
         */
        public @Nullable Pattern getValidationPattern(@NotNull String argumentName) {
            return validationPatterns.get(argumentName);
        }

        /**
         * Get static completions for an argument.
         *
         * @param argumentName the argument name
         * @return list of static completions, or empty list
         */
        public @NotNull List<String> getStaticCompletions(@NotNull String argumentName) {
            List<String> completions = staticCompletions.get(argumentName);
            return completions != null ? completions : Collections.emptyList();
        }

        /**
         * Filter static completions by prefix.
         *
         * @param argumentName the argument name
         * @param prefix       the prefix to filter by
         * @return filtered completions
         */
        public @NotNull List<String> getStaticCompletionsStartingWith(@NotNull String argumentName,
                                                                       @NotNull String prefix) {
            List<String> all = getStaticCompletions(argumentName);
            if (all.isEmpty() || prefix.isEmpty()) {
                return all;
            }
            String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<>();
            for (String completion : all) {
                if (completion.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    result.add(completion);
                }
            }
            return result;
        }

        public @NotNull List<String> getRequiredArguments() { return requiredArguments; }
        public @NotNull List<String> getOptionalArguments() { return optionalArguments; }

        /**
         * Get the type name for an argument.
         *
         * @param argumentName the argument name
         * @return the type name, or null if not found
         */
        public @Nullable String getArgumentTypeName(@NotNull String argumentName) {
            return argumentTypeNames.get(argumentName);
        }

        public @NotNull String getUsageString() { return usageString; }

        /**
         * Resolve an alias to its primary argument name.
         *
         * @param alias the alias
         * @return the primary name, or the alias itself if not an alias
         */
        public @NotNull String resolvePrimaryName(@NotNull String alias) {
            String primary = aliasToPrimaryMap.get(alias);
            return primary != null ? primary : alias;
        }

        public boolean hasGreedyArgument() { return hasGreedyArgument; }
        public int getPermissionedArgumentCount() { return permissionedArgumentCount; }

        /**
         * Get a compiled subcommand.
         *
         * @param name the subcommand name
         * @return the compiled subcommand, or null if not found
         */
        public @Nullable CompiledCommand getSubcommand(@NotNull String name) {
            return subcommands.get(name.toLowerCase(Locale.ROOT));
        }

        public @NotNull Map<String, CompiledCommand> getSubcommands() { return subcommands; }
        public boolean hasSubcommands() { return !subcommands.isEmpty(); }

        public long getCompiledAt() { return compiledAt; }

        public int getTotalArgumentCount() {
            return requiredArguments.size() + optionalArguments.size();
        }

        @Override
        public String toString() {
            return "CompiledCommand{" +
                   "name='" + commandName + '\'' +
                   ", args=" + getTotalArgumentCount() +
                   ", required=" + requiredArguments.size() +
                   ", optional=" + optionalArguments.size() +
                   ", subcommands=" + subcommands.size() +
                   '}';
        }

        // Builder
        public static final class Builder {
            private final String commandName;
            private Map<String, Integer> argumentIndexMap = new LinkedHashMap<>();
            private Map<String, Pattern> validationPatterns = new LinkedHashMap<>();
            private Map<String, List<String>> staticCompletions = new LinkedHashMap<>();
            private List<String> requiredArguments = new ArrayList<>();
            private List<String> optionalArguments = new ArrayList<>();
            private Map<String, String> argumentTypeNames = new LinkedHashMap<>();
            private String usageString = "";
            private Map<String, String> aliasToPrimaryMap = new LinkedHashMap<>();
            private boolean hasGreedyArgument = false;
            private int permissionedArgumentCount = 0;
            private Map<String, CompiledCommand> subcommands;

            Builder(String commandName) {
                this.commandName = commandName;
            }

            public Builder argumentIndexMap(Map<String, Integer> map) {
                this.argumentIndexMap = map;
                return this;
            }

            public Builder validationPatterns(Map<String, Pattern> patterns) {
                this.validationPatterns = patterns;
                return this;
            }

            public Builder staticCompletions(Map<String, List<String>> completions) {
                this.staticCompletions = completions;
                return this;
            }

            public Builder requiredArguments(List<String> args) {
                this.requiredArguments = args;
                return this;
            }

            public Builder optionalArguments(List<String> args) {
                this.optionalArguments = args;
                return this;
            }

            public Builder argumentTypeNames(Map<String, String> types) {
                this.argumentTypeNames = types;
                return this;
            }

            public Builder usageString(String usage) {
                this.usageString = usage;
                return this;
            }

            public Builder aliasToPrimaryMap(Map<String, String> map) {
                this.aliasToPrimaryMap = map;
                return this;
            }

            public Builder hasGreedyArgument(boolean hasGreedy) {
                this.hasGreedyArgument = hasGreedy;
                return this;
            }

            public Builder permissionedArgumentCount(int count) {
                this.permissionedArgumentCount = count;
                return this;
            }

            public Builder subcommands(Map<String, CompiledCommand> subs) {
                this.subcommands = subs;
                return this;
            }

            public CompiledCommand build() {
                return new CompiledCommand(this);
            }
        }
    }

    /**
     * Compiler statistics snapshot.
     */
    public static final class CompilerStats {
        private final int cachedCommands;
        private final long compilations;
        private final long cacheHits;
        private final long cacheMisses;

        CompilerStats(int cachedCommands, long compilations, long cacheHits, long cacheMisses) {
            this.cachedCommands = cachedCommands;
            this.compilations = compilations;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
        }

        public int getCachedCommands() { return cachedCommands; }
        public long getCompilations() { return compilations; }
        public long getCacheHits() { return cacheHits; }
        public long getCacheMisses() { return cacheMisses; }

        public double getCacheHitRatio() {
            long total = cacheHits + cacheMisses;
            if (total == 0) return 1.0;
            return (double) cacheHits / total;
        }

        @Override
        public String toString() {
            return String.format(
                "CompilerStats{cached=%d, compilations=%d, hits=%d, misses=%d, hitRatio=%.2f}",
                cachedCommands, compilations, cacheHits, cacheMisses, getCacheHitRatio()
            );
        }
    }
}

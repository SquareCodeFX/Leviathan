package de.feelix.leviathan.command;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-argument configuration container.
 * Holds metadata like optionality, per-argument permission, greedy flag and
 * tab-completion configuration.
 */
public final class ArgContext {
    /**
     * Functional interface for providing dynamic completions with full runtime context.
     */
    @FunctionalInterface
    public interface DynamicCompletionProvider {
        @NotNull List<String> provide(@NotNull DynamicCompletionContext ctx);
    }

    private final boolean optional;
    private final boolean greedy;
    private final @Nullable String permission;
    private final List<String> completionsPredefined;
    /**
     * Optional dynamic completion provider. When present, FluentCommand will invoke it on tab-complete.
     */
    private final @Nullable DynamicCompletionProvider completionsDynamic;

    private ArgContext(boolean optional,
                       boolean greedy,
                       @Nullable String permission,
                       @Nullable List<String> completionsPredefined,
                       @Nullable DynamicCompletionProvider completionsDynamic) {
        this.optional = optional;
        this.greedy = greedy;
        this.permission = (permission == null || permission.isBlank()) ? null : permission;
        List<String> list = (completionsPredefined == null) ? List.of() : new ArrayList<>(completionsPredefined);
        this.completionsPredefined = Collections.unmodifiableList(list);
        this.completionsDynamic = completionsDynamic;
    }

    public static @NotNull Builder builder() {
        return new Builder();
    }

    public static @NotNull ArgContext defaultContext() {
        return new Builder().build();
    }

    public boolean optional() { return optional; }
    public boolean greedy() { return greedy; }
    public @Nullable String permission() { return permission; }
    public @NotNull List<String> completionsPredefined() { return completionsPredefined; }
    public @Nullable DynamicCompletionProvider completionsDynamic() { return completionsDynamic; }

    public static final class Builder {
        private boolean optional;
        private boolean greedy;
        private @Nullable String permission;
        private List<String> completionsPredefined = new ArrayList<>();
        private @Nullable DynamicCompletionProvider completionsDynamic;

        public @NotNull Builder optional(boolean optional) { this.optional = optional; return this; }
        public @NotNull Builder greedy(boolean greedy) { this.greedy = greedy; return this; }
        public @NotNull Builder permission(@Nullable String permission) { this.permission = permission; return this; }
        public @NotNull Builder completionsPredefined(@NotNull List<String> completions) {
            this.completionsPredefined = new ArrayList<>(completions);
            return this;
        }
        public @NotNull Builder completionsDynamic(@Nullable DynamicCompletionProvider provider) { this.completionsDynamic = provider; return this; }
        public @NotNull ArgContext build() {
            return new ArgContext(optional, greedy, permission, completionsPredefined, completionsDynamic);
        }
    }
}

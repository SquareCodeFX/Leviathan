package de.feelix.leviathan.command.progress;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.async.Progress;
import de.feelix.leviathan.util.Preconditions;

/**
 * A visual progress bar that can be used to display progress in commands.
 * <p>
 * This progress bar provides a fluent API for building customizable progress indicators
 * that integrate seamlessly with the SlashCommand system's async progress reporting.
 * <p>
 * Example usage:
 * <pre>{@code
 * ProgressBar bar = ProgressBar.builder()
 *     .total(100)
 *     .width(30)
 *     .character('█')
 *     .prefix("Processing: ")
 *     .showPercentage(true)
 *     .build();
 *
 * // Update progress
 * progress.report(bar.render(50));  // 50/100 = 50%
 * }</pre>
 */
public final class ProgressBar {

    private final int total;
    private final int width;
    private final char filledChar;
    private final char emptyChar;
    private final String prefix;
    private final String suffix;
    private final boolean showPercentage;
    private final boolean showRatio;
    private final String colorFilled;
    private final String colorEmpty;
    private final String colorText;

    private ProgressBar(int total, int width, char filledChar, char emptyChar,
                        String prefix, String suffix, boolean showPercentage, boolean showRatio,
                        String colorFilled, String colorEmpty, String colorText) {
        this.total = total;
        this.width = width;
        this.filledChar = filledChar;
        this.emptyChar = emptyChar;
        this.prefix = prefix;
        this.suffix = suffix;
        this.showPercentage = showPercentage;
        this.showRatio = showRatio;
        this.colorFilled = colorFilled;
        this.colorEmpty = colorEmpty;
        this.colorText = colorText;
    }

    /**
     * Creates a new ProgressBar builder.
     *
     * @return a new ProgressBarBuilder instance
     */
    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Renders the progress bar for the given current value.
     *
     * @param current the current progress value (0 to total)
     * @return a formatted string representation of the progress bar
     */
    public @NotNull String render(int current) {
        return render(current, null);
    }

    /**
     * Renders the progress bar for the given current value with an optional message.
     *
     * @param current the current progress value (0 to total)
     * @param message optional message to append after the progress bar
     * @return a formatted string representation of the progress bar
     */
    public @NotNull String render(int current, @Nullable String message) {
        // Clamp current value between 0 and total
        current = Math.max(0, Math.min(current, total));

        double percentage = total > 0 ? (double) current / total : 0.0;
        int filledWidth = (int) Math.round(percentage * width);
        int emptyWidth = width - filledWidth;

        StringBuilder sb = new StringBuilder();

        // Add prefix
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(colorText).append(prefix);
        }

        // Build the bar
        sb.append(colorFilled);
        for (int i = 0; i < filledWidth; i++) {
            sb.append(filledChar);
        }
        sb.append(colorEmpty);
        for (int i = 0; i < emptyWidth; i++) {
            sb.append(emptyChar);
        }

        // Add suffix
        if (suffix != null && !suffix.isEmpty()) {
            sb.append(colorText).append(suffix);
        }

        // Add percentage
        if (showPercentage) {
            sb.append(colorText).append(String.format(" %.1f%%", percentage * 100));
        }

        // Add ratio
        if (showRatio) {
            sb.append(colorText).append(String.format(" (%d/%d)", current, total));
        }

        // Add optional message
        if (message != null && !message.isEmpty()) {
            sb.append(colorText).append(" - ").append(message);
        }

        return sb.toString();
    }

    /**
     * Renders the progress bar as a percentage (0.0 to 1.0).
     *
     * @param percentage the progress as a percentage (0.0 to 1.0)
     * @return a formatted string representation of the progress bar
     */
    public @NotNull String renderPercentage(double percentage) {
        return renderPercentage(percentage, null);
    }

    /**
     * Renders the progress bar as a percentage (0.0 to 1.0) with an optional message.
     *
     * @param percentage the progress as a percentage (0.0 to 1.0)
     * @param message    optional message to append after the progress bar
     * @return a formatted string representation of the progress bar
     */
    public @NotNull String renderPercentage(double percentage, @Nullable String message) {
        int current = (int) Math.round(percentage * total);
        return render(current, message);
    }

    /**
     * Creates a Progress callback that updates this progress bar.
     * This is useful for integrating with async command actions.
     *
     * @param progress the Progress callback to wrap
     * @return a new Progress callback that renders and reports progress
     */
    public @NotNull ProgressReporter reporter(@NotNull Progress progress) {
        Preconditions.checkNotNull(progress, "progress");
        return new ProgressReporter(this, progress);
    }

    /**
     * @return the total value for this progress bar
     */
    public int total() {
        return total;
    }

    /**
     * @return the width in characters of this progress bar
     */
    public int width() {
        return width;
    }

    /**
     * Builder for creating ProgressBar instances with a fluent API.
     */
    public static final class Builder {
        private int total = 100;
        private int width = 30;
        private char filledChar = '█';
        private char emptyChar = '░';
        private String prefix = "";
        private String suffix = "";
        private boolean showPercentage = false;
        private boolean showRatio = false;
        private String colorFilled = "§a";  // Green
        private String colorEmpty = "§7";   // Gray
        private String colorText = "§f";    // White

        private Builder() {
        }

        /**
         * Sets the total value for the progress bar (default: 100).
         *
         * @param total the total value
         * @return this builder
         */
        public @NotNull Builder total(int total) {
            Preconditions.checkArgument(total > 0, "total must be positive");
            this.total = total;
            return this;
        }

        /**
         * Sets the width in characters of the progress bar (default: 30).
         *
         * @param width the width in characters
         * @return this builder
         */
        public @NotNull Builder width(int width) {
            Preconditions.checkArgument(width > 0, "width must be positive");
            this.width = width;
            return this;
        }

        /**
         * Sets the character used for the filled portion (default: '█').
         *
         * @param filledChar the character for filled portions
         * @return this builder
         */
        public @NotNull Builder filledChar(char filledChar) {
            this.filledChar = filledChar;
            return this;
        }

        /**
         * Sets the character used for the empty portion (default: '░').
         *
         * @param emptyChar the character for empty portions
         * @return this builder
         */
        public @NotNull Builder emptyChar(char emptyChar) {
            this.emptyChar = emptyChar;
            return this;
        }

        /**
         * Sets both filled and empty characters at once.
         *
         * @param filledChar the character for filled portions
         * @param emptyChar  the character for empty portions
         * @return this builder
         */
        public @NotNull Builder characters(char filledChar, char emptyChar) {
            this.filledChar = filledChar;
            this.emptyChar = emptyChar;
            return this;
        }

        /**
         * Sets the prefix text before the progress bar.
         *
         * @param prefix the prefix text
         * @return this builder
         */
        public @NotNull Builder prefix(@NotNull String prefix) {
            Preconditions.checkNotNull(prefix, "prefix");
            this.prefix = prefix;
            return this;
        }

        /**
         * Sets the suffix text after the progress bar.
         *
         * @param suffix the suffix text
         * @return this builder
         */
        public @NotNull Builder suffix(@NotNull String suffix) {
            Preconditions.checkNotNull(suffix, "suffix");
            this.suffix = suffix;
            return this;
        }

        /**
         * Sets whether to show the percentage (default: false).
         *
         * @param showPercentage true to show percentage
         * @return this builder
         */
        public @NotNull Builder showPercentage(boolean showPercentage) {
            this.showPercentage = showPercentage;
            return this;
        }

        /**
         * Sets whether to show the ratio (current/total) (default: false).
         *
         * @param showRatio true to show ratio
         * @return this builder
         */
        public @NotNull Builder showRatio(boolean showRatio) {
            this.showRatio = showRatio;
            return this;
        }

        /**
         * Sets the Minecraft color code for the filled portion (default: "§a" - green).
         *
         * @param colorFilled the color code for filled portions
         * @return this builder
         */
        public @NotNull Builder colorFilled(@NotNull String colorFilled) {
            Preconditions.checkNotNull(colorFilled, "colorFilled");
            this.colorFilled = colorFilled;
            return this;
        }

        /**
         * Sets the Minecraft color code for the empty portion (default: "§7" - gray).
         *
         * @param colorEmpty the color code for empty portions
         * @return this builder
         */
        public @NotNull Builder colorEmpty(@NotNull String colorEmpty) {
            Preconditions.checkNotNull(colorEmpty, "colorEmpty");
            this.colorEmpty = colorEmpty;
            return this;
        }

        /**
         * Sets the Minecraft color code for text (prefix, suffix, percentage, ratio) (default: "§f" - white).
         *
         * @param colorText the color code for text
         * @return this builder
         */
        public @NotNull Builder colorText(@NotNull String colorText) {
            Preconditions.checkNotNull(colorText, "colorText");
            this.colorText = colorText;
            return this;
        }

        /**
         * Sets all colors at once.
         *
         * @param colorFilled the color code for filled portions
         * @param colorEmpty  the color code for empty portions
         * @param colorText   the color code for text
         * @return this builder
         */
        public @NotNull Builder colors(@NotNull String colorFilled, @NotNull String colorEmpty,
                                       @NotNull String colorText) {
            Preconditions.checkNotNull(colorFilled, "colorFilled");
            Preconditions.checkNotNull(colorEmpty, "colorEmpty");
            Preconditions.checkNotNull(colorText, "colorText");
            this.colorFilled = colorFilled;
            this.colorEmpty = colorEmpty;
            this.colorText = colorText;
            return this;
        }

        /**
         * Builds the ProgressBar with the configured settings.
         *
         * @return a new ProgressBar instance
         */
        public @NotNull ProgressBar build() {
            return new ProgressBar(total, width, filledChar, emptyChar, prefix, suffix,
                showPercentage, showRatio, colorFilled, colorEmpty, colorText);
        }
    }
}

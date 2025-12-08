package de.feelix.leviathan.command.progress;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.async.Progress;
import de.feelix.leviathan.util.Preconditions;

/**
 * A wrapper around Progress that automatically renders a ProgressBar when reporting.
 * This provides a convenient way to integrate ProgressBar with async command actions.
 * <p>
 * Example usage:
 * <pre>{@code
 * ProgressBar bar = ProgressBar.builder()
 *     .total(100)
 *     .showPercentage(true)
 *     .build();
 * ProgressReporter reporter = bar.reporter(progress);
 *
 * // Report progress with automatic rendering
 * reporter.report(25);           // Renders: [██████████░░░░░░░░░░░░░░░░░░] 25.0%
 * reporter.report(50, "Loading"); // Renders: [███████████████░░░░░░░░░░░░] 50.0% - Loading
 * }</pre>
 */
public final class ProgressReporter {

    private final ProgressBar progressBar;
    private final Progress progress;

    /**
     * Creates a new ProgressReporter.
     *
     * @param progressBar the progress bar to use for rendering
     * @param progress    the underlying progress callback
     */
    public ProgressReporter(@NotNull ProgressBar progressBar, @NotNull Progress progress) {
        this.progressBar = Preconditions.checkNotNull(progressBar, "progressBar");
        this.progress = Preconditions.checkNotNull(progress, "progress");
    }

    /**
     * Reports progress by rendering the progress bar with the current value.
     *
     * @param current the current progress value
     */
    public void report(int current) {
        progress.report(progressBar.render(current));
    }

    /**
     * Reports progress by rendering the progress bar with the current value and a message.
     *
     * @param current the current progress value
     * @param message the message to display
     */
    public void report(int current, @Nullable String message) {
        progress.report(progressBar.render(current, message));
    }

    /**
     * Reports progress by rendering the progress bar with a percentage (0.0 to 1.0).
     *
     * @param percentage the progress percentage (0.0 to 1.0)
     */
    public void reportPercentage(double percentage) {
        progress.report(progressBar.renderPercentage(percentage));
    }

    /**
     * Reports progress by rendering the progress bar with a percentage and a message.
     *
     * @param percentage the progress percentage (0.0 to 1.0)
     * @param message    the message to display
     */
    public void reportPercentage(double percentage, @Nullable String message) {
        progress.report(progressBar.renderPercentage(percentage, message));
    }

    /**
     * Reports a raw message without rendering the progress bar.
     * This is useful for sending additional status messages.
     *
     * @param message the message to report
     */
    public void reportRaw(@NotNull String message) {
        Preconditions.checkNotNull(message, "message");
        progress.report(message);
    }

    /**
     * @return the underlying ProgressBar
     */
    public @NotNull ProgressBar progressBar() {
        return progressBar;
    }

    /**
     * @return the underlying Progress callback
     */
    public @NotNull Progress progress() {
        return progress;
    }
}

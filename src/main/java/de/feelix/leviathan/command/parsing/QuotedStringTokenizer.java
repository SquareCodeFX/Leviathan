package de.feelix.leviathan.command.parsing;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizer that handles quoted strings in command input.
 * <p>
 * This tokenizer recognizes both single and double quotes and treats
 * the content within quotes as a single token, even if it contains spaces.
 * <p>
 * Features:
 * <ul>
 *   <li>Double quotes: {@code "hello world"} → single token "hello world"</li>
 *   <li>Single quotes: {@code 'hello world'} → single token "hello world"</li>
 *   <li>Escape sequences: {@code \"} within quoted strings</li>
 *   <li>Mixed content: {@code arg1 "quoted value" arg2}</li>
 *   <li>Nested quotes: {@code "he said 'hello'"} → "he said 'hello'"</li>
 * </ul>
 * <p>
 * Example usage:
 * <pre>{@code
 * String input = "player \"hello world\" 42";
 * List<String> tokens = QuotedStringTokenizer.tokenize(input);
 * // Result: ["player", "hello world", "42"]
 * }</pre>
 */
public final class QuotedStringTokenizer {

    private QuotedStringTokenizer() {
        // Utility class
    }

    /**
     * Result of tokenization, containing the tokens and any parsing errors.
     */
    public static final class TokenizeResult {
        private final List<String> tokens;
        private final boolean success;
        private final String error;

        private TokenizeResult(List<String> tokens, boolean success, String error) {
            this.tokens = tokens;
            this.success = success;
            this.error = error;
        }

        /**
         * Create a successful result.
         */
        public static @NotNull TokenizeResult success(@NotNull List<String> tokens) {
            return new TokenizeResult(tokens, true, null);
        }

        /**
         * Create an error result with partial tokens.
         */
        public static @NotNull TokenizeResult error(@NotNull List<String> partialTokens, @NotNull String error) {
            return new TokenizeResult(partialTokens, false, error);
        }

        /**
         * @return the list of parsed tokens
         */
        public @NotNull List<String> tokens() {
            return tokens;
        }

        /**
         * @return true if tokenization was successful
         */
        public boolean isSuccess() {
            return success;
        }

        /**
         * @return the error message, or null if successful
         */
        public String error() {
            return error;
        }
    }

    /**
     * Tokenize a command input string, handling quoted strings.
     * <p>
     * Quoted strings (both single and double quotes) are treated as single tokens.
     * Escape sequences within quotes are handled (e.g., \", \', \\).
     *
     * @param input the raw command input string
     * @return a TokenizeResult containing the tokens and success status
     */
    public static @NotNull TokenizeResult tokenize(@NotNull String input) {
        Preconditions.checkNotNull(input, "input");

        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inDoubleQuotes = false;
        boolean inSingleQuotes = false;
        boolean escaped = false;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);

            if (escaped) {
                // Handle escape sequences
                currentToken.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\' && (inDoubleQuotes || inSingleQuotes)) {
                // Start escape sequence within quotes
                escaped = true;
                continue;
            }

            if (c == '"' && !inSingleQuotes) {
                if (inDoubleQuotes) {
                    // End of double-quoted string
                    inDoubleQuotes = false;
                } else {
                    // Start of double-quoted string
                    inDoubleQuotes = true;
                }
                continue;
            }

            if (c == '\'' && !inDoubleQuotes) {
                if (inSingleQuotes) {
                    // End of single-quoted string
                    inSingleQuotes = false;
                } else {
                    // Start of single-quoted string
                    inSingleQuotes = true;
                }
                continue;
            }

            if (Character.isWhitespace(c) && !inDoubleQuotes && !inSingleQuotes) {
                // Whitespace outside quotes - end of current token
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken.setLength(0);
                }
                continue;
            }

            // Regular character - add to current token
            currentToken.append(c);
        }

        // Check for unclosed quotes
        if (inDoubleQuotes) {
            // Add partial token and return error
            if (currentToken.length() > 0) {
                tokens.add(currentToken.toString());
            }
            return TokenizeResult.error(tokens, "Unclosed double quote");
        }

        if (inSingleQuotes) {
            // Add partial token and return error
            if (currentToken.length() > 0) {
                tokens.add(currentToken.toString());
            }
            return TokenizeResult.error(tokens, "Unclosed single quote");
        }

        // Add final token if present
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }

        return TokenizeResult.success(tokens);
    }

    /**
     * Tokenize command arguments array, handling quoted strings that may span multiple array elements.
     * <p>
     * This is useful when Bukkit has already split the command but quoted strings
     * were split incorrectly.
     *
     * @param args the argument array from Bukkit
     * @return a TokenizeResult containing the re-tokenized arguments
     */
    public static @NotNull TokenizeResult tokenize(@NotNull String[] args) {
        Preconditions.checkNotNull(args, "args");

        if (args.length == 0) {
            return TokenizeResult.success(new ArrayList<>());
        }

        // Join args with spaces and re-tokenize with quote handling
        return tokenize(String.join(" ", args));
    }

    /**
     * Check if a string needs quote escaping for safe usage in commands.
     *
     * @param value the value to check
     * @return true if the value contains spaces or special characters that need quoting
     */
    public static boolean needsQuoting(@NotNull String value) {
        Preconditions.checkNotNull(value, "value");

        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c) || c == '"' || c == '\'') {
                return true;
            }
        }
        return false;
    }

    /**
     * Quote a string value if necessary for safe command usage.
     *
     * @param value the value to quote
     * @return the quoted value, or the original if quoting is not needed
     */
    public static @NotNull String quoteIfNeeded(@NotNull String value) {
        Preconditions.checkNotNull(value, "value");

        if (!needsQuoting(value)) {
            return value;
        }

        // Escape any existing double quotes
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }

    /**
     * Convert tokens back to a properly quoted command string.
     *
     * @param tokens the tokens to join
     * @return a command string with proper quoting
     */
    public static @NotNull String toCommandString(@NotNull List<String> tokens) {
        Preconditions.checkNotNull(tokens, "tokens");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(quoteIfNeeded(tokens.get(i)));
        }
        return sb.toString();
    }
}

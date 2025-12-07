package de.feelix.leviathan.command.flag;

import de.feelix.leviathan.annotations.NotNull;
import de.feelix.leviathan.annotations.Nullable;
import de.feelix.leviathan.command.argument.ParseResult;
import de.feelix.leviathan.exceptions.ParsingException;
import org.bukkit.command.CommandSender;

import java.util.*;

/**
 * Parser for extracting flags and key-value pairs from command arguments.
 * <p>
 * This parser handles multiple input formats:
 * <ul>
 *   <li>Short flags: {@code -s}, {@code -f}</li>
 *   <li>Combined short flags: {@code -sf} (equivalent to {@code -s -f})</li>
 *   <li>Long flags: {@code --silent}, {@code --force}</li>
 *   <li>Negated flags: {@code --no-confirm}</li>
 *   <li>Key-value with equals: {@code key=value}, {@code --key=value}</li>
 *   <li>Key-value with colon: {@code key:value}</li>
 *   <li>Key-value with space: {@code --key value}</li>
 *   <li>Quoted values: {@code reason="This is the reason"}</li>
 *   <li>Multiple values: {@code tags=pvp,survival,hardcore}</li>
 * </ul>
 */
public final class FlagAndKeyValueParser {

    /**
     * Result of parsing flags and key-value pairs from command arguments.
     */
    public static final class ParsedResult {
        private final Map<String, Boolean> flagValues;
        private final Map<String, Object> keyValuePairs;
        private final Map<String, List<Object>> multiValuePairs;
        private final List<String> remainingArgs;
        private final List<String> errors;

        private ParsedResult(Map<String, Boolean> flagValues,
                             Map<String, Object> keyValuePairs,
                             Map<String, List<Object>> multiValuePairs,
                             List<String> remainingArgs,
                             List<String> errors) {
            this.flagValues = Collections.unmodifiableMap(new HashMap<>(flagValues));
            this.keyValuePairs = Collections.unmodifiableMap(new HashMap<>(keyValuePairs));
            this.multiValuePairs = Collections.unmodifiableMap(new HashMap<>(multiValuePairs));
            this.remainingArgs = Collections.unmodifiableList(new ArrayList<>(remainingArgs));
            this.errors = Collections.unmodifiableList(new ArrayList<>(errors));
        }

        /**
         * @return map of flag names to their boolean values
         */
        public @NotNull Map<String, Boolean> flagValues() {
            return flagValues;
        }

        /**
         * @return map of key-value names to their parsed values (single values)
         */
        public @NotNull Map<String, Object> keyValuePairs() {
            return keyValuePairs;
        }

        /**
         * @return map of key-value names to their parsed values (multiple values as list)
         */
        public @NotNull Map<String, List<Object>> multiValuePairs() {
            return multiValuePairs;
        }

        /**
         * @return arguments that were not recognized as flags or key-value pairs
         */
        public @NotNull List<String> remainingArgs() {
            return remainingArgs;
        }

        /**
         * @return list of parsing errors encountered
         */
        public @NotNull List<String> errors() {
            return errors;
        }

        /**
         * @return true if there were no parsing errors
         */
        public boolean isSuccess() {
            return errors.isEmpty();
        }

        /**
         * Get a flag value by name.
         *
         * @param name         the flag name
         * @param defaultValue value to return if flag is not present
         * @return the flag value or default
         */
        public boolean getFlag(@NotNull String name, boolean defaultValue) {
            return flagValues.getOrDefault(name, defaultValue);
        }

        /**
         * Get a key-value by name.
         *
         * @param name the key name
         * @param type the expected type
         * @param <T>  the type
         * @return the value or null if not present or wrong type
         */
        @SuppressWarnings("unchecked")
        public @Nullable <T> T getKeyValue(@NotNull String name, @NotNull Class<T> type) {
            Object value = keyValuePairs.get(name);
            if (value == null) return null;
            if (!type.isInstance(value)) return null;
            return (T) value;
        }

        /**
         * Get multi-values by name.
         *
         * @param name the key name
         * @return the list of values or empty list if not present
         */
        @SuppressWarnings("unchecked")
        public @NotNull <T> List<T> getMultiValue(@NotNull String name) {
            List<Object> values = multiValuePairs.get(name);
            if (values == null) return Collections.emptyList();
            return (List<T>) values;
        }
    }

    private final List<Flag> flags;
    private final List<KeyValue<?>> keyValues;

    /**
     * Create a new parser with the given flags and key-values.
     *
     * @param flags     the list of flag definitions
     * @param keyValues the list of key-value definitions
     */
    public FlagAndKeyValueParser(@NotNull List<Flag> flags, @NotNull List<KeyValue<?>> keyValues) {
        this.flags = new ArrayList<>(flags);
        this.keyValues = new ArrayList<>(keyValues);
    }

    /**
     * Parse the given arguments, extracting flags and key-value pairs.
     *
     * @param args   the command arguments to parse
     * @param sender the command sender for context during parsing
     * @return the parse result containing extracted values and remaining arguments
     */
    public @NotNull ParsedResult parse(@NotNull String[] args, @NotNull CommandSender sender) {
        Map<String, Boolean> flagValues = new HashMap<>();
        Map<String, Object> kvValues = new HashMap<>();
        Map<String, List<Object>> multiValues = new HashMap<>();
        List<String> remaining = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Initialize flags with default values
        for (Flag flag : flags) {
            flagValues.put(flag.name(), flag.defaultValue());
        }

        // Initialize key-values with default values
        for (KeyValue<?> kv : keyValues) {
            if (kv.defaultValue() != null) {
                if (kv.multipleValues()) {
                    multiValues.put(kv.name(), new ArrayList<>(List.of(kv.defaultValue())));
                } else {
                    kvValues.put(kv.name(), kv.defaultValue());
                }
            }
        }

        int i = 0;
        while (i < args.length) {
            String arg = args[i];

            // Check for long form flags/key-values (--xxx)
            if (arg.startsWith("--")) {
                String content = arg.substring(2);
                
                // Check for --key=value format
                int eqIdx = content.indexOf('=');
                if (eqIdx > 0) {
                    String key = content.substring(0, eqIdx);
                    String value = content.substring(eqIdx + 1);
                    value = unquote(value);
                    
                    if (!handleKeyValue(key, value, kvValues, multiValues, errors, sender)) {
                        // Check if it's a flag (shouldn't have value but user provided one)
                        Flag flag = findFlagByLongForm(key);
                        if (flag != null) {
                            errors.add("Flag '--" + key + "' does not accept a value");
                        } else {
                            remaining.add(arg);
                        }
                    }
                    i++;
                    continue;
                }

                // Check for negated flag (--no-xxx)
                Flag negatedFlag = findFlagByNegatedLongForm(content);
                if (negatedFlag != null) {
                    flagValues.put(negatedFlag.name(), false);
                    i++;
                    continue;
                }

                // Check for long form flag
                Flag flag = findFlagByLongForm(content);
                if (flag != null) {
                    flagValues.put(flag.name(), true);
                    i++;
                    continue;
                }

                // Check for --key value format (space separated)
                KeyValue<?> kv = findKeyValueByKey(content);
                if (kv != null) {
                    if (i + 1 < args.length) {
                        String value = unquote(args[i + 1]);
                        handleKeyValue(content, value, kvValues, multiValues, errors, sender);
                        i += 2;
                        continue;
                    } else {
                        errors.add("Key-value '--" + content + "' requires a value");
                        i++;
                        continue;
                    }
                }

                // Unknown long form
                remaining.add(arg);
                i++;
                continue;
            }

            // Check for short form flags (-x or -xyz)
            if (arg.startsWith("-") && arg.length() > 1 && !Character.isDigit(arg.charAt(1))) {
                String content = arg.substring(1);
                
                // Check for -k=value format
                int eqIdx = content.indexOf('=');
                if (eqIdx > 0) {
                    String key = content.substring(0, eqIdx);
                    String value = content.substring(eqIdx + 1);
                    value = unquote(value);
                    
                    if (!handleKeyValue(key, value, kvValues, multiValues, errors, sender)) {
                        remaining.add(arg);
                    }
                    i++;
                    continue;
                }

                // Process combined short flags (-sf = -s -f)
                boolean allValid = true;
                for (char c : content.toCharArray()) {
                    Flag flag = findFlagByShortForm(c);
                    if (flag != null) {
                        flagValues.put(flag.name(), true);
                    } else {
                        allValid = false;
                        break;
                    }
                }
                
                if (allValid) {
                    i++;
                    continue;
                } else {
                    // Not valid short flags, treat as regular arg
                    remaining.add(arg);
                    i++;
                    continue;
                }
            }

            // Check for key=value or key:value format (without dashes)
            int eqIdx = arg.indexOf('=');
            int colonIdx = arg.indexOf(':');
            int separatorIdx = -1;

            if (eqIdx > 0 && (colonIdx < 0 || eqIdx < colonIdx)) {
                separatorIdx = eqIdx;
            } else if (colonIdx > 0) {
                separatorIdx = colonIdx;
            }
            
            if (separatorIdx > 0) {
                String key = arg.substring(0, separatorIdx);
                String value = arg.substring(separatorIdx + 1);
                value = unquote(value);
                
                if (handleKeyValue(key, value, kvValues, multiValues, errors, sender)) {
                    i++;
                    continue;
                }
            }

            // Regular argument - add to remaining
            remaining.add(arg);
            i++;
        }

        // Check for missing required key-values
        for (KeyValue<?> kv : keyValues) {
            if (kv.required()) {
                if (kv.multipleValues()) {
                    if (!multiValues.containsKey(kv.name()) || multiValues.get(kv.name()).isEmpty()) {
                        errors.add("Required key-value '" + kv.key() + "' is missing");
                    }
                } else {
                    if (!kvValues.containsKey(kv.name())) {
                        errors.add("Required key-value '" + kv.key() + "' is missing");
                    }
                }
            }
        }

        return new ParsedResult(flagValues, kvValues, multiValues, remaining, errors);
    }

    /**
     * Handle a key-value pair, parsing and storing the value.
     *
     * @return true if the key was recognized and handled
     */
    private boolean handleKeyValue(String key, String value,
                                   Map<String, Object> kvValues,
                                   Map<String, List<Object>> multiValues,
                                   List<String> errors,
                                   CommandSender sender) {
        KeyValue<?> kv = findKeyValueByKey(key);
        if (kv == null) {
            return false;
        }

        if (kv.multipleValues()) {
            // Split by separator and parse each value
            String[] parts = value.split(kv.valueSeparator());
            List<Object> parsedValues = new ArrayList<>();
            
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                
                try {
                    ParseResult<?> result = kv.parser().parse(part, sender);
                    if (result.isSuccess()) {
                        parsedValues.add(result.value().orElse(null));
                    } else {
                        errors.add("Invalid value '" + part + "' for key '" + kv.key() + "': " + result.error().orElse("unknown error"));
                    }
                } catch (ParsingException e) {
                    errors.add("Error parsing value '" + part + "' for key '" + kv.key() + "': " + e.getMessage());
                }
            }
            
            multiValues.put(kv.name(), parsedValues);
        } else {
            // Parse single value
            try {
                ParseResult<?> result = kv.parser().parse(value, sender);
                if (result.isSuccess()) {
                    kvValues.put(kv.name(), result.value().orElse(null));
                } else {
                    errors.add("Invalid value '" + value + "' for key '" + kv.key() + "': " + result.error().orElse("unknown error"));
                }
            } catch (ParsingException e) {
                errors.add("Error parsing value '" + value + "' for key '" + kv.key() + "': " + e.getMessage());
            }
        }
        
        return true;
    }

    /**
     * Remove surrounding quotes from a value string.
     */
    private @NotNull String unquote(@NotNull String value) {
        if (value.length() >= 2) {
            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    /**
     * Find a flag by its short form character.
     */
    private @Nullable Flag findFlagByShortForm(char c) {
        for (Flag flag : flags) {
            if (flag.matchesShort(c)) {
                return flag;
            }
        }
        return null;
    }

    /**
     * Find a flag by its long form string.
     */
    private @Nullable Flag findFlagByLongForm(@NotNull String form) {
        for (Flag flag : flags) {
            if (flag.matchesLong(form)) {
                return flag;
            }
        }
        return null;
    }

    /**
     * Find a flag by its negated long form string (no-xxx).
     */
    private @Nullable Flag findFlagByNegatedLongForm(@NotNull String form) {
        for (Flag flag : flags) {
            if (flag.matchesNegatedLong(form)) {
                return flag;
            }
        }
        return null;
    }

    /**
     * Find a key-value by its key string.
     */
    private @Nullable KeyValue<?> findKeyValueByKey(@NotNull String key) {
        for (KeyValue<?> kv : keyValues) {
            if (kv.matchesKey(key)) {
                return kv;
            }
        }
        return null;
    }

    /**
     * Get tab completion suggestions for the current input.
     *
     * @param currentArg the current argument being typed
     * @param parsedArgs already parsed arguments
     * @return list of completion suggestions
     */
    public @NotNull List<String> getCompletions(@NotNull String currentArg, @NotNull ParsedResult parsedArgs) {
        List<String> completions = new ArrayList<>();

        if (currentArg.startsWith("--")) {
            String prefix = currentArg.substring(2).toLowerCase();
            
            // Suggest long form flags
            for (Flag flag : flags) {
                if (flag.longForm() != null) {
                    String suggestion = "--" + flag.longForm();
                    if (suggestion.toLowerCase().startsWith("--" + prefix)) {
                        // Don't suggest if already set
                        if (!parsedArgs.flagValues().containsKey(flag.name()) || 
                            parsedArgs.flagValues().get(flag.name()) == flag.defaultValue()) {
                            completions.add(suggestion);
                        }
                    }
                    // Also suggest negated form
                    if (flag.supportsNegation()) {
                        String negated = "--no-" + flag.longForm();
                        if (negated.toLowerCase().startsWith("--" + prefix)) {
                            completions.add(negated);
                        }
                    }
                }
            }
            
            // Suggest key-value keys
            for (KeyValue<?> kv : keyValues) {
                String suggestion = "--" + kv.key();
                if (suggestion.toLowerCase().startsWith("--" + prefix)) {
                    if (!parsedArgs.keyValuePairs().containsKey(kv.name())) {
                        completions.add(suggestion + "=");
                    }
                }
            }
        } else if (currentArg.startsWith("-") && !currentArg.startsWith("--")) {
            String prefix = currentArg.substring(1).toLowerCase();
            
            // Suggest short form flags
            for (Flag flag : flags) {
                if (flag.shortForm() != null) {
                    String suggestion = "-" + flag.shortForm();
                    if (suggestion.toLowerCase().startsWith("-" + prefix)) {
                        completions.add(suggestion);
                    }
                }
            }
        } else {
            // Suggest key= or key: formats
            String lowerArg = currentArg.toLowerCase();
            for (KeyValue<?> kv : keyValues) {
                String suggestion = kv.key() + "=";
                if (suggestion.toLowerCase().startsWith(lowerArg)) {
                    if (!parsedArgs.keyValuePairs().containsKey(kv.name())) {
                        completions.add(suggestion);
                    }
                }
            }
        }

        return completions;
    }
}

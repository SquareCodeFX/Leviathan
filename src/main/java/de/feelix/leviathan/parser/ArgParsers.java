package de.feelix.leviathan.parser;

import org.bukkit.command.CommandSender;
import de.feelix.leviathan.exceptions.ParsingException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Factory and utility methods for common {@link ArgumentParser} implementations.
 * <p>
 * All parsers returned from this class are stateless and thread-safe.
 */
public final class ArgParsers {
    private ArgParsers() {}

    private static List<String> startingWith(String prefix, Collection<String> options) {
        String low = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(low))
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Parser for 32-bit integers.
     * @return an ArgumentParser that parses {@code int} values
     */
    public static ArgumentParser<Integer> intParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "int"; }

            @Override
            public ParseResult<Integer> parse(String input, CommandSender sender) {
                try {
                    return ParseResult.success(Integer.parseInt(input));
                } catch (NumberFormatException e) {
                    return ParseResult.error("not an integer");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                return Collections.emptyList();
            }
        };
    }

    /**
     * Parser for 64-bit integers.
     * @return an ArgumentParser that parses {@code long} values
     */
    public static ArgumentParser<Long> longParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "long"; }

            @Override
            public ParseResult<Long> parse(String input, CommandSender sender) {
                try {
                    return ParseResult.success(Long.parseLong(input));
                } catch (NumberFormatException e) {
                    return ParseResult.error("not a long");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                return Collections.emptyList();
            }
        };
    }

    /**
     * Parser for a raw string token (no validation).
     * @return an ArgumentParser that returns the input unchanged
     */
    public static ArgumentParser<String> stringParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "string"; }

            @Override
            public ParseResult<String> parse(String input, CommandSender sender) {
                return ParseResult.success(input);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                return Collections.emptyList();
            }
        };
    }


    /**
     * Parser for a UUID in canonical string form.
     * @return an ArgumentParser that parses {@link UUID} values
     */
    public static ArgumentParser<UUID> uuidParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "uuid"; }

            @Override
            public ParseResult<UUID> parse(String input, CommandSender sender) {
                try {
                    return ParseResult.success(UUID.fromString(input));
                } catch (IllegalArgumentException e) {
                    return ParseResult.error("invalid uuid");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                return Collections.emptyList();
            }
        };
    }

    /**
     * Create a parser that accepts only a predefined set of aliases and maps them to values.
     * Keys are matched case-insensitively.
     *
     * @param aliasToValue mapping from alias to value (must be non-empty; keys non-blank; values non-null)
     * @param typeNameForError short type label for error messages (e.g., "gamemode")
     * @param <T> value type
     * @return an ArgumentParser that parses to one of the provided values
     * @throws ParsingException if the alias map is invalid (null/empty keys/values or case-insensitive duplicates)
     */
    public static <T> ArgumentParser<T> choices(Map<String, T> aliasToValue, String typeNameForError) {
        if (aliasToValue == null || aliasToValue.isEmpty()) {
            throw new ParsingException("choices requires a non-empty alias map");
        }
        if (typeNameForError == null || typeNameForError.trim().isEmpty()) {
            throw new ParsingException("choices requires a non-blank typeNameForError");
        }
        // Validate keys and values and detect case-insensitive duplicates
        Set<String> lowered = new LinkedHashSet<>();
        for (Map.Entry<String, T> e : aliasToValue.entrySet()) {
            String k = e.getKey();
            if (k == null || k.trim().isEmpty()) {
                throw new ParsingException("choices alias map contains a null/blank key");
            }
            if (e.getValue() == null) {
                throw new ParsingException("choices alias '" + k + "' has a null value");
            }
            String low = k.toLowerCase(Locale.ROOT);
            if (!lowered.add(low)) {
                throw new ParsingException("choices contains duplicate aliases differing only by case: '" + k + "'");
            }
        }
        Map<String, T> lower = aliasToValue.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return typeNameForError; }

            @Override
            public ParseResult<T> parse(String input, CommandSender sender) {
                T v = lower.get(input.toLowerCase(Locale.ROOT));
                if (v == null) return ParseResult.error("expected one of: " + String.join(", ", lower.keySet()));
                return ParseResult.success(v);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                return startingWith(input, lower.keySet());
            }
        };
    }

    /**
     * Create a parser that attempts multiple parsers and succeeds with the first one that parses the input.
     * Tab completions from all provided parsers are merged (duplicates removed, original order preserved).
     *
     * @param typeNameForError short label for error messages encompassing the accepted types (e.g., "uuid")
     * @param parsers candidate parsers (must be non-null, at least one)
     * @param <T> target type
     * @return an ArgumentParser that delegates to the first successful parser
     * @throws ParsingException if typeNameForError is blank or parsers list is invalid
     */
    @SafeVarargs
    public static <T> ArgumentParser<T> oneOf(String typeNameForError, ArgumentParser<? extends T>... parsers) {
        if (typeNameForError == null || typeNameForError.trim().isEmpty()) {
            throw new ParsingException("oneOf requires a non-blank typeNameForError");
        }
        if (parsers == null || parsers.length == 0) {
            throw new ParsingException("oneOf requires at least one parser");
        }
        for (int i = 0; i < parsers.length; i++) {
            if (parsers[i] == null) {
                throw new ParsingException("oneOf received a null parser at index " + i);
            }
        }
        List<ArgumentParser<? extends T>> list = List.of(parsers);
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return typeNameForError; }

            @Override
            public ParseResult<T> parse(String input, CommandSender sender) {
                for (ArgumentParser<? extends T> p : list) {
                    ParseResult<? extends T> res = p.parse(input, sender);
                    if (res.isSuccess()) {
                        return ParseResult.success(res.value().orElse(null));
                    }
                }
                return ParseResult.error("no matching parser for input");
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Set<String> combined = new LinkedHashSet<>();
                for (ArgumentParser<? extends T> p : list) {
                    combined.addAll(p.complete(input, sender));
                }
                return new ArrayList<>(combined);
            }
        };
    }
}

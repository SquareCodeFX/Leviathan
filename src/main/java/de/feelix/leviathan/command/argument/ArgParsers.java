package de.feelix.leviathan.command.argument;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.feelix.leviathan.exceptions.ParsingException;
import de.feelix.leviathan.util.Preconditions;
import de.feelix.leviathan.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Factory and utility methods for common {@link ArgumentParser} implementations.
 * <p>
 * All parsers returned from this class are stateless and thread-safe.
 */
public final class ArgParsers {
    private ArgParsers() {
    }

    // Cached Material completions for performance (Material enum has ~900 constants)
    private static final List<String> MATERIAL_COMPLETIONS = Collections.unmodifiableList(
        Arrays.stream(Material.values())
            .map(m -> m.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toList())
    );

    private static List<String> startingWith(String prefix, Collection<String> options) {
        String low = prefix.toLowerCase(Locale.ROOT);
        return options.stream()
            .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(low))
            .sorted()
            .collect(Collectors.toList());
    }

    /**
     * Parser for 32-bit integers.
     *
     * @return an ArgumentParser that parses {@code int} values
     */
    public static @NotNull ArgumentParser<Integer> intParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "int";
            }

            @Override
            public ParseResult<Integer> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                try {
                    return ParseResult.success(Integer.parseInt(input));
                } catch (NumberFormatException e) {
                    return ParseResult.error("not an integer");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return Collections.emptyList();
            }
        };
    }

    /**
     * Parser for 64-bit integers.
     *
     * @return an ArgumentParser that parses {@code long} values
     */
    public static @NotNull ArgumentParser<Long> longParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "long";
            }

            @Override
            public ParseResult<Long> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                try {
                    return ParseResult.success(Long.parseLong(input));
                } catch (NumberFormatException e) {
                    return ParseResult.error("not a long");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return Collections.emptyList();
            }
        };
    }

    /**
     * Parser for a raw string token (no validation).
     *
     * @return an ArgumentParser that returns the input unchanged
     */
    public static @NotNull ArgumentParser<String> stringParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "string";
            }

            @Override
            public ParseResult<String> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return ParseResult.success(input);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return Collections.emptyList();
            }
        };
    }


    /**
     * Parser for a UUID in canonical string form.
     *
     * @return an ArgumentParser that parses {@link UUID} values
     */
    public static @NotNull ArgumentParser<UUID> uuidParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "uuid";
            }

            @Override
            public ParseResult<UUID> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                try {
                    return ParseResult.success(UUID.fromString(input));
                } catch (IllegalArgumentException e) {
                    return ParseResult.error("invalid uuid");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return Collections.emptyList();
            }
        };
    }

    /**
     * Create a parser that accepts only a predefined set of aliases and maps them to values.
     * Keys are matched case-insensitively.
     *
     * @param aliasToValue     mapping from alias to value (must be non-empty; keys non-blank; values non-null)
     * @param typeNameForError short type label for error messages (e.g., "gamemode")
     * @param <T>              value type
     * @return an ArgumentParser that parses to one of the provided values
     * @throws ParsingException if the alias map is invalid (null/empty keys/values or case-insensitive duplicates)
     */
    public static <T> @NotNull ArgumentParser<T> choices(@NotNull Map<String, T> aliasToValue,
                                                         @NotNull String typeNameForError) {
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
            .collect(Collectors.toMap(
                e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue, (a, b) -> a,
                LinkedHashMap::new
            ));
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return typeNameForError;
            }

            @Override
            public ParseResult<T> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                T v = lower.get(input.toLowerCase(Locale.ROOT));
                if (v == null) return ParseResult.error("expected one of: " + String.join(", ", lower.keySet()));
                return ParseResult.success(v);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return startingWith(input, lower.keySet());
            }
        };
    }

    /**
     * Create a parser that attempts multiple parsers and succeeds with the first one that parses the input.
     * Tab completions from all provided parsers are merged (duplicates removed, original order preserved).
     *
     * @param typeNameForError short label for error messages encompassing the accepted types (e.g., "uuid")
     * @param parsers          candidate parsers (must be non-null, at least one)
     * @param <T>              target type
     * @return an ArgumentParser that delegates to the first successful parser
     * @throws ParsingException if typeNameForError is blank or parsers list is invalid
     */
    @SafeVarargs
    public static <T> @NotNull ArgumentParser<T> oneOf(@NotNull String typeNameForError,
                                                       @NotNull ArgumentParser<? extends T>... parsers) {
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
            public String getTypeName() {
                return typeNameForError;
            }

            @Override
            public ParseResult<T> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
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
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                Set<String> combined = new LinkedHashSet<>();
                for (ArgumentParser<? extends T> p : list) {
                    combined.addAll(p.complete(input, sender));
                }
                return new ArrayList<>(combined);
            }
        };
    }

    /**
     * Parser for double-precision floating point numbers.
     *
     * @return an ArgumentParser that parses {@code double} values
     */
    public static @NotNull ArgumentParser<Double> doubleParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "double";
            }

            @Override
            public ParseResult<Double> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                try {
                    return ParseResult.success(Double.parseDouble(input));
                } catch (NumberFormatException e) {
                    return ParseResult.error("not a valid number");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return Collections.emptyList();
            }
        };
    }

    /**
     * Parser for single-precision floating point numbers.
     *
     * @return an ArgumentParser that parses {@code float} values
     */
    public static @NotNull ArgumentParser<Float> floatParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "float";
            }

            @Override
            public ParseResult<Float> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                try {
                    return ParseResult.success(Float.parseFloat(input));
                } catch (NumberFormatException e) {
                    return ParseResult.error("not a valid number");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return Collections.emptyList();
            }
        };
    }

    /**
     * Parser for boolean values (accepts: true, false, yes, no, on, off, 1, 0).
     * All inputs are case-insensitive.
     *
     * @return an ArgumentParser that parses {@code boolean} values
     */
    public static @NotNull ArgumentParser<Boolean> booleanParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "boolean";
            }

            @Override
            public ParseResult<Boolean> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                String lower = input.toLowerCase(Locale.ROOT);
                return switch (lower) {
                    case "true", "yes", "on", "1" -> ParseResult.success(true);
                    case "false", "no", "off", "0" -> ParseResult.success(false);
                    default -> ParseResult.error("expected true/false, yes/no, on/off, or 1/0");
                };
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return startingWith(input, List.of("true", "false", "yes", "no", "on", "off"));
            }
        };
    }

    /**
     * Parser for online players by name.
     *
     * @return an ArgumentParser that parses online {@link Player} objects
     */
    public static @NotNull ArgumentParser<Player> playerParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "player";
            }

            @Override
            public ParseResult<Player> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                Player player = Bukkit.getPlayer(input);
                if (player == null) {
                    return ParseResult.error("player '" + input + "' not found or offline");
                }
                return ParseResult.success(player);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return startingWith(
                    input, Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList())
                );
            }
        };
    }

    /**
     * Parser for offline players by name (includes online players).
     * Uses Bukkit's getOfflinePlayer which may perform blocking lookups.
     *
     * @return an ArgumentParser that parses {@link OfflinePlayer} objects
     */
    public static @NotNull ArgumentParser<OfflinePlayer> offlinePlayerParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "offlinePlayer";
            }

            @Override
            public ParseResult<OfflinePlayer> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                @SuppressWarnings("deprecation")
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(input);
                return ParseResult.success(offlinePlayer);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return startingWith(
                    input, Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList())
                );
            }
        };
    }

    /**
     * Parser for worlds by name.
     *
     * @return an ArgumentParser that parses {@link World} objects
     */
    public static @NotNull ArgumentParser<World> worldParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "world";
            }

            @Override
            public ParseResult<World> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                World world = Bukkit.getWorld(input);
                if (world == null) {
                    return ParseResult.error("world '" + input + "' not found");
                }
                return ParseResult.success(world);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return startingWith(
                    input, Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .collect(Collectors.toList())
                );
            }
        };
    }

    /**
     * Parser for materials by name (case-insensitive).
     *
     * @return an ArgumentParser that parses {@link Material} values
     */
    public static @NotNull ArgumentParser<Material> materialParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return "material";
            }

            @Override
            public ParseResult<Material> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                try {
                    Material material = Material.valueOf(input.toUpperCase(Locale.ROOT));
                    return ParseResult.success(material);
                } catch (IllegalArgumentException e) {
                    return ParseResult.error("unknown material '" + input + "'");
                }
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                // Use cached completions for performance
                return startingWith(input, MATERIAL_COMPLETIONS);
            }
        };
    }

    /**
     * Parser for enum constants by name (case-insensitive).
     *
     * @param enumClass the enum class to parse
     * @param <E>       enum type
     * @return an ArgumentParser that parses enum constants
     * @throws ParsingException if enumClass is null or has no constants
     */
    public static <E extends Enum<E>> @NotNull ArgumentParser<E> enumParser(@NotNull Class<E> enumClass) {
        if (enumClass == null) {
            throw new ParsingException("enumParser requires a non-null enum class");
        }
        E[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new ParsingException("enumParser requires an enum class with at least one constant");
        }
        Map<String, E> lowerMap = Arrays.stream(constants)
            .collect(Collectors.toMap(e -> e.name().toLowerCase(Locale.ROOT), e -> e));
        String typeName = enumClass.getSimpleName().toLowerCase(Locale.ROOT);

        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return typeName;
            }

            @Override
            public ParseResult<E> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                E value = lowerMap.get(input.toLowerCase(Locale.ROOT));
                if (value == null) {
                    return ParseResult.error("unknown " + typeName + " '" + input + "'");
                }
                return ParseResult.success(value);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                return startingWith(input, lowerMap.keySet());
            }
        };
    }

    /**
     * Parser for enum constants with custom aliases support.
     * <p>
     * Allows defining shorthand aliases for enum values, making commands more user-friendly.
     * <p>
     * Example usage:
     * <pre>{@code
     * // Define aliases for GameMode
     * Map<String, GameMode> aliases = Map.of(
     *     "0", GameMode.SURVIVAL,
     *     "1", GameMode.CREATIVE,
     *     "2", GameMode.ADVENTURE,
     *     "3", GameMode.SPECTATOR,
     *     "s", GameMode.SURVIVAL,
     *     "c", GameMode.CREATIVE,
     *     "a", GameMode.ADVENTURE,
     *     "sp", GameMode.SPECTATOR,
     *     "sv", GameMode.SURVIVAL,
     *     "cr", GameMode.CREATIVE
     * );
     *
     * .argEnum("gamemode", GameMode.class, ArgParsers.enumParserWithAliases(GameMode.class, aliases))
     * }</pre>
     *
     * @param enumClass the enum class to parse
     * @param aliases   map of alias -> enum value
     * @param <E>       enum type
     * @return an ArgumentParser that parses enum constants with aliases
     */
    public static <E extends Enum<E>> @NotNull ArgumentParser<E> enumParserWithAliases(
        @NotNull Class<E> enumClass,
        @NotNull Map<String, E> aliases) {
        if (enumClass == null) {
            throw new ParsingException("enumParserWithAliases requires a non-null enum class");
        }
        if (aliases == null) {
            throw new ParsingException("enumParserWithAliases requires a non-null aliases map");
        }
        E[] constants = enumClass.getEnumConstants();
        if (constants == null || constants.length == 0) {
            throw new ParsingException("enumParserWithAliases requires an enum class with at least one constant");
        }

        // Build combined map: standard enum names (lowercase) + custom aliases (lowercase)
        Map<String, E> combined = new LinkedHashMap<>();
        for (E constant : constants) {
            combined.put(constant.name().toLowerCase(Locale.ROOT), constant);
        }
        for (Map.Entry<String, E> entry : aliases.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                combined.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }

        String typeName = enumClass.getSimpleName().toLowerCase(Locale.ROOT);
        Set<String> completions = new LinkedHashSet<>();
        for (E constant : constants) {
            completions.add(constant.name().toLowerCase(Locale.ROOT));
        }

        return new ArgumentParser<>() {
            @Override
            public String getTypeName() {
                return typeName;
            }

            @Override
            public ParseResult<E> parse(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                E value = combined.get(input.toLowerCase(Locale.ROOT));
                if (value == null) {
                    return ParseResult.error("unknown " + typeName + " '" + input + "'");
                }
                return ParseResult.success(value);
            }

            @Override
            public List<String> complete(String input, CommandSender sender) {
                Preconditions.checkNotNull(input, "input");
                Preconditions.checkNotNull(sender, "sender");
                // Only show standard names in completions, not aliases (cleaner UX)
                return startingWith(input, completions);
            }
        };
    }

    /**
     * Create common GameMode aliases map.
     * <p>
     * Includes numeric (0-3), short forms (s, c, a, sp), and common abbreviations.
     *
     * @return map of aliases to GameMode values
     */
    public static @NotNull Map<String, org.bukkit.GameMode> gameModeAliases() {
        Map<String, org.bukkit.GameMode> aliases = new LinkedHashMap<>();
        // Numeric aliases (like vanilla)
        aliases.put("0", org.bukkit.GameMode.SURVIVAL);
        aliases.put("1", org.bukkit.GameMode.CREATIVE);
        aliases.put("2", org.bukkit.GameMode.ADVENTURE);
        aliases.put("3", org.bukkit.GameMode.SPECTATOR);
        // Single-letter aliases
        aliases.put("s", org.bukkit.GameMode.SURVIVAL);
        aliases.put("c", org.bukkit.GameMode.CREATIVE);
        aliases.put("a", org.bukkit.GameMode.ADVENTURE);
        // Common abbreviations
        aliases.put("sv", org.bukkit.GameMode.SURVIVAL);
        aliases.put("cr", org.bukkit.GameMode.CREATIVE);
        aliases.put("ad", org.bukkit.GameMode.ADVENTURE);
        aliases.put("sp", org.bukkit.GameMode.SPECTATOR);
        aliases.put("spec", org.bukkit.GameMode.SPECTATOR);
        return aliases;
    }

    /**
     * Parser for GameMode with common aliases (0-3, s/c/a/sp, etc.).
     *
     * @return an ArgumentParser for GameMode with aliases
     */
    public static @NotNull ArgumentParser<org.bukkit.GameMode> gameModeParser() {
        return enumParserWithAliases(org.bukkit.GameMode.class, gameModeAliases());
    }

    // ==================== Duration Parser ====================

    /**
     * Parser for duration strings that converts human-readable time formats to milliseconds.
     * <p>
     * Supported formats:
     * <ul>
     *   <li>{@code 30s} - 30 seconds</li>
     *   <li>{@code 5m} - 5 minutes</li>
     *   <li>{@code 2h} - 2 hours</li>
     *   <li>{@code 1d} - 1 day</li>
     *   <li>{@code 1w} - 1 week</li>
     *   <li>{@code 1mo} - 1 month (30 days)</li>
     *   <li>{@code 1y} - 1 year (365 days)</li>
     *   <li>Combinations: {@code 2h30m}, {@code 1d12h30m}</li>
     *   <li>Pure numbers are treated as seconds</li>
     * </ul>
     *
     * @return an ArgumentParser that parses duration strings to milliseconds
     */
    public static @NotNull ArgumentParser<Long> durationParser() {
        return new DurationParser(false);
    }

    /**
     * Parser for duration strings that converts human-readable time formats to seconds.
     * <p>
     * Same formats as {@link #durationParser()} but returns seconds instead of milliseconds.
     *
     * @return an ArgumentParser that parses duration strings to seconds
     */
    public static @NotNull ArgumentParser<Long> durationParserSeconds() {
        return new DurationParser(true);
    }

    /**
     * Internal duration parser implementation.
     */
    private static final class DurationParser implements ArgumentParser<Long> {
        private static final Map<String, Long> UNIT_MILLIS = new LinkedHashMap<>();
        private static final List<String> EXAMPLE_COMPLETIONS = List.of(
            "30s", "1m", "5m", "10m", "30m",
            "1h", "2h", "6h", "12h",
            "1d", "7d", "30d",
            "1w", "2w",
            "1mo", "1y",
            "1h30m", "2d12h"
        );

        static {
            // Order matters for parsing - longer units first
            UNIT_MILLIS.put("y", 365L * 24 * 60 * 60 * 1000);      // year
            UNIT_MILLIS.put("mo", 30L * 24 * 60 * 60 * 1000);      // month
            UNIT_MILLIS.put("w", 7L * 24 * 60 * 60 * 1000);        // week
            UNIT_MILLIS.put("d", 24L * 60 * 60 * 1000);            // day
            UNIT_MILLIS.put("h", 60L * 60 * 1000);                 // hour
            UNIT_MILLIS.put("m", 60L * 1000);                      // minute
            UNIT_MILLIS.put("s", 1000L);                           // second
            UNIT_MILLIS.put("ms", 1L);                             // millisecond
        }

        private final boolean returnSeconds;

        DurationParser(boolean returnSeconds) {
            this.returnSeconds = returnSeconds;
        }

        @Override
        public String getTypeName() {
            return "duration";
        }

        @Override
        public ParseResult<Long> parse(String input, CommandSender sender) {
            Preconditions.checkNotNull(input, "input");
            Preconditions.checkNotNull(sender, "sender");

            String trimmed = input.trim().toLowerCase(Locale.ROOT);
            if (trimmed.isEmpty()) {
                return ParseResult.error("duration cannot be empty");
            }

            // Check for special keywords
            if ("permanent".equals(trimmed) || "forever".equals(trimmed) || "infinite".equals(trimmed)) {
                return ParseResult.success(-1L); // -1 indicates permanent/infinite
            }

            // Try to parse as pure number (interpreted as seconds)
            try {
                long seconds = Long.parseLong(trimmed);
                long result = returnSeconds ? seconds : seconds * 1000;
                return ParseResult.success(result);
            } catch (NumberFormatException ignored) {
                // Not a pure number, continue with duration parsing
            }

            // Parse duration string like "2h30m" or "1d"
            long totalMillis = 0;
            String remaining = trimmed;

            while (!remaining.isEmpty()) {
                // Find the next number
                int numEnd = 0;
                while (numEnd < remaining.length() && (Character.isDigit(remaining.charAt(numEnd))
                       || remaining.charAt(numEnd) == '.')) {
                    numEnd++;
                }

                if (numEnd == 0) {
                    return ParseResult.error("invalid duration format: expected number at '" + remaining + "'");
                }

                String numStr = remaining.substring(0, numEnd);
                remaining = remaining.substring(numEnd);

                double value;
                try {
                    value = Double.parseDouble(numStr);
                } catch (NumberFormatException e) {
                    return ParseResult.error("invalid number '" + numStr + "' in duration");
                }

                if (value < 0) {
                    return ParseResult.error("duration values cannot be negative");
                }

                // Find the unit
                String matchedUnit = null;
                for (String unit : UNIT_MILLIS.keySet()) {
                    if (remaining.startsWith(unit)) {
                        matchedUnit = unit;
                        break;
                    }
                }

                if (matchedUnit == null) {
                    if (remaining.isEmpty()) {
                        // No unit at end - treat as seconds
                        totalMillis += (long) (value * 1000);
                    } else {
                        return ParseResult.error("unknown time unit at '" + remaining + "'. " +
                            "Valid units: s, m, h, d, w, mo, y");
                    }
                } else {
                    totalMillis += (long) (value * UNIT_MILLIS.get(matchedUnit));
                    remaining = remaining.substring(matchedUnit.length());
                }
            }

            if (totalMillis == 0 && !trimmed.equals("0") && !trimmed.equals("0s")) {
                return ParseResult.error("invalid duration: '" + input + "'");
            }

            long result = returnSeconds ? totalMillis / 1000 : totalMillis;
            return ParseResult.success(result);
        }

        @Override
        public List<String> complete(String input, CommandSender sender) {
            Preconditions.checkNotNull(input, "input");
            Preconditions.checkNotNull(sender, "sender");

            if (input.isEmpty()) {
                return new ArrayList<>(EXAMPLE_COMPLETIONS);
            }

            // Filter completions that start with input
            String low = input.toLowerCase(Locale.ROOT);
            List<String> matches = EXAMPLE_COMPLETIONS.stream()
                .filter(c -> c.startsWith(low))
                .collect(Collectors.toList());

            // If input ends with a number, suggest units
            if (Character.isDigit(input.charAt(input.length() - 1))) {
                matches.addAll(List.of(
                    input + "s", input + "m", input + "h",
                    input + "d", input + "w"
                ));
            }

            return matches;
        }
    }

    // ==================== Duration Utility Methods ====================

    /**
     * Format a duration in milliseconds to a human-readable string.
     * Useful for displaying durations to users.
     *
     * @param millis duration in milliseconds
     * @return formatted string like "2h 30m 15s"
     */
    public static @NotNull String formatDuration(long millis) {
        if (millis < 0) {
            return "permanent";
        }
        if (millis == 0) {
            return "0s";
        }

        StringBuilder sb = new StringBuilder();
        long remaining = millis;

        long years = remaining / (365L * 24 * 60 * 60 * 1000);
        if (years > 0) {
            sb.append(years).append("y ");
            remaining %= (365L * 24 * 60 * 60 * 1000);
        }

        long months = remaining / (30L * 24 * 60 * 60 * 1000);
        if (months > 0) {
            sb.append(months).append("mo ");
            remaining %= (30L * 24 * 60 * 60 * 1000);
        }

        long weeks = remaining / (7L * 24 * 60 * 60 * 1000);
        if (weeks > 0) {
            sb.append(weeks).append("w ");
            remaining %= (7L * 24 * 60 * 60 * 1000);
        }

        long days = remaining / (24L * 60 * 60 * 1000);
        if (days > 0) {
            sb.append(days).append("d ");
            remaining %= (24L * 60 * 60 * 1000);
        }

        long hours = remaining / (60L * 60 * 1000);
        if (hours > 0) {
            sb.append(hours).append("h ");
            remaining %= (60L * 60 * 1000);
        }

        long minutes = remaining / (60L * 1000);
        if (minutes > 0) {
            sb.append(minutes).append("m ");
            remaining %= (60L * 1000);
        }

        long seconds = remaining / 1000;
        if (seconds > 0 || sb.isEmpty()) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }
}

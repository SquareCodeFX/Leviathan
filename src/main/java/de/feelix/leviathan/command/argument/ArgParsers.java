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
    public static @NotNull ArgumentParser<Integer> intParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "int"; }

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
     * @return an ArgumentParser that parses {@code long} values
     */
    public static @NotNull ArgumentParser<Long> longParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "long"; }

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
     * @return an ArgumentParser that returns the input unchanged
     */
    public static @NotNull ArgumentParser<String> stringParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "string"; }

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
     * @return an ArgumentParser that parses {@link UUID} values
     */
    public static @NotNull ArgumentParser<UUID> uuidParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "uuid"; }

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
     * @param aliasToValue mapping from alias to value (must be non-empty; keys non-blank; values non-null)
     * @param typeNameForError short type label for error messages (e.g., "gamemode")
     * @param <T> value type
     * @return an ArgumentParser that parses to one of the provided values
     * @throws ParsingException if the alias map is invalid (null/empty keys/values or case-insensitive duplicates)
     */
    public static <T> @NotNull ArgumentParser<T> choices(@NotNull Map<String, T> aliasToValue, @NotNull String typeNameForError) {
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
     * @param parsers candidate parsers (must be non-null, at least one)
     * @param <T> target type
     * @return an ArgumentParser that delegates to the first successful parser
     * @throws ParsingException if typeNameForError is blank or parsers list is invalid
     */
    @SafeVarargs
    public static <T> @NotNull ArgumentParser<T> oneOf(@NotNull String typeNameForError, @NotNull ArgumentParser<? extends T>... parsers) {
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
     * @return an ArgumentParser that parses {@code double} values
     */
    public static @NotNull ArgumentParser<Double> doubleParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "double"; }

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
     * @return an ArgumentParser that parses {@code float} values
     */
    public static @NotNull ArgumentParser<Float> floatParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "float"; }

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
     * @return an ArgumentParser that parses {@code boolean} values
     */
    public static @NotNull ArgumentParser<Boolean> booleanParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "boolean"; }

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
     * @return an ArgumentParser that parses online {@link Player} objects
     */
    public static @NotNull ArgumentParser<Player> playerParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "player"; }

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
                return startingWith(input, Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            }
        };
    }

    /**
     * Parser for offline players by name (includes online players).
     * Uses Bukkit's getOfflinePlayer which may perform blocking lookups.
     * @return an ArgumentParser that parses {@link OfflinePlayer} objects
     */
    public static @NotNull ArgumentParser<OfflinePlayer> offlinePlayerParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "offlinePlayer"; }

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
                return startingWith(input, Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .collect(Collectors.toList()));
            }
        };
    }

    /**
     * Parser for worlds by name.
     * @return an ArgumentParser that parses {@link World} objects
     */
    public static @NotNull ArgumentParser<World> worldParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "world"; }

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
                return startingWith(input, Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .collect(Collectors.toList()));
            }
        };
    }

    /**
     * Parser for materials by name (case-insensitive).
     * @return an ArgumentParser that parses {@link Material} values
     */
    public static @NotNull ArgumentParser<Material> materialParser() {
        return new ArgumentParser<>() {
            @Override
            public String getTypeName() { return "material"; }

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
                return startingWith(input, Arrays.stream(Material.values())
                    .map(m -> m.name().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toList()));
            }
        };
    }

    /**
     * Parser for enum constants by name (case-insensitive).
     * @param enumClass the enum class to parse
     * @param <E> enum type
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
            public String getTypeName() { return typeName; }

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
}

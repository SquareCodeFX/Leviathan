# Leviathan Command Framework - Security Audit Report

**Datum:** 2025-12-14
**Version:** 1.2.0 (Update 2)
**Analyst:** Claude Code Security Audit

---

## Zusammenfassung

Diese Sicherheitsanalyse identifiziert potentielle Sicherheitslucken, Algorithmus-Probleme und Parsing-Fehler im Leviathan Command Framework. Die Probleme sind nach Schweregrad kategorisiert.

### Status der Behebungen (Update 2)

| Problem | Schweregrad | Status |
|---------|-------------|--------|
| Duration Parser Overflow | HIGH | ✅ BEHOBEN |
| Double/Float NaN/Infinity | HIGH | ✅ BEHOBEN |
| Levenshtein DoS | MEDIUM | ✅ BEHOBEN |
| Quote-Escaping | MEDIUM | ✅ BEHOBEN |
| CommandContext.getAsDouble() NaN/Infinity | MEDIUM | ✅ BEHOBEN (Update 2) |
| SimpleDateFormat Thread-Safety | MEDIUM | ✅ BEHOBEN (Update 2) |
| ReDoS in ArgContext | HIGH | ⚠️ DOKUMENTIERT |
| Race Condition Cooldown | MEDIUM | ⚠️ TEILWEISE BEHOBEN (atomic ops) |

---

## Kritische Probleme (HIGH)

### 1. ReDoS (Regular Expression Denial of Service) in ArgContext

**Datei:** `ArgContext.java:617-619`

```java
public @NotNull Builder stringPattern(@Nullable String regex) {
    this.stringPattern = (regex == null) ? null : Pattern.compile(regex);
    return this;
}
```

**Problem:** Regex-Patterns werden ohne Validierung kompiliert. Boswillige oder schlecht geschriebene Patterns konnen zu katastrophalem Backtracking fuhren.

**Beispiel eines gefahrlichen Patterns:**
```java
.stringPattern("(a+)+$")  // Evil regex
```

**Empfehlung:**
- Timeout fur Regex-Ausfuhrung implementieren
- Pattern-Komplexitat validieren
- `Pattern.compile(regex, Pattern.DOTALL | Pattern.UNICODE_CASE)` mit Flags verwenden

---

### 2. Duration Parser Arithmetic Overflow

**Datei:** `ArgParsers.java:836`

```java
totalMillis += (long) (value * UNIT_MILLIS.get(matchedUnit));
```

**Problem:** Bei sehr grossen Eingaben wie `999999999999999y` kann ein Long-Overflow auftreten.

**Empfehlung:**
```java
// Overflow-Check vor Addition
long addition = (long) (value * UNIT_MILLIS.get(matchedUnit));
if (Long.MAX_VALUE - totalMillis < addition) {
    return ParseResult.error("Duration overflow - value too large");
}
totalMillis += addition;
```

---

### 3. Double/Float Parser akzeptiert spezielle Werte

**Datei:** `ArgParsers.java:301, 334`

```java
return ParseResult.success(Double.parseDouble(input));  // Akzeptiert "NaN", "Infinity"
return ParseResult.success(Float.parseFloat(input));    // Akzeptiert "NaN", "Infinity"
```

**Problem:** Diese Parser akzeptieren `NaN`, `Infinity`, `-Infinity`, was zu unerwarteten Ergebnissen fuhren kann.

**Empfehlung:**
```java
double value = Double.parseDouble(input);
if (Double.isNaN(value) || Double.isInfinite(value)) {
    return ParseResult.error("Special values (NaN, Infinity) are not allowed");
}
return ParseResult.success(value);
```

---

## Mittlere Probleme (MEDIUM)

### 4. CommandContext.getAsDouble() - NaN/Infinity nicht geprüft ✅ BEHOBEN

**Datei:** `CommandContext.java:820-834`

```java
public @Nullable Double getAsDouble(@NotNull String name) {
    // ...
    return Double.parseDouble((String) value);  // Akzeptierte NaN, Infinity!
}
```

**Problem:** Inkonsistent mit den Fixes in ArgParsers - `getAsDouble()` akzeptierte spezielle Werte.

**Fix angewendet:** NaN/Infinity-Check in allen Konvertierungspfaden hinzugefügt.

---

### 5. SimpleDateFormat Thread-Safety Issue ✅ BEHOBEN

**Datei:** `DetailedExceptionHandler.java:53` und `JvmInfoCollector.java`

```java
private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("...");
```

**Problem:** `SimpleDateFormat` ist NICHT thread-safe. Bei gleichzeitigen Aufrufen aus mehreren Threads können Datenkorruption oder Exceptions auftreten.

**Fix angewendet:** Ersetzt durch thread-safe `DateTimeFormatter`:

```java
private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
```

---

### 6. Levenshtein Distance DoS-Risiko ✅ BEHOBEN

**Datei:** `StringSimilarity.java:29-67`

**Problem:** Die Levenshtein-Distanz hat O(n*m) Zeitkomplexitat. Sehr lange Eingaben konnen den Server blockieren.

**Fix angewendet:**
```java
public static final int MAX_STRING_LENGTH = 256;

// Security: Limit string length to prevent DoS (O(n*m) complexity)
String truncated1 = s1.length() > MAX_STRING_LENGTH ? s1.substring(0, MAX_STRING_LENGTH) : s1;
String truncated2 = s2.length() > MAX_STRING_LENGTH ? s2.substring(0, MAX_STRING_LENGTH) : s2;
```

---

### 7. Race Condition bei Cooldown-Bypass

**Datei:** `SlashCommand.java:594-610` und `CooldownManager.java`

**Problem:** Zwischen `checkUserCooldown()` und `updateUserCooldown()` besteht eine Race Condition. Bei schnellen aufeinanderfolgenden Anfragen kann der Cooldown umgangen werden.

**Code-Flow:**
```
1. checkUserCooldown() -> nicht auf Cooldown
2. Befehl wird ausgefuhrt
3. updateUserCooldown()
```

**Problem:** Zwischen Schritt 1 und 3 kann ein zweiter Request durchkommen.

**Empfehlung:** Atomare Check-and-Set Operation implementieren:
```java
public static CooldownResult checkAndUpdateCooldown(String cmd, String userId, long cooldownMs) {
    return perUserCooldowns.compute(cmd, (k, userMap) -> {
        if (userMap == null) userMap = new ConcurrentHashMap<>();
        Long lastExec = userMap.get(userId);
        long now = System.currentTimeMillis();
        if (lastExec != null && (now - lastExec) < cooldownMs) {
            return userMap; // Still on cooldown
        }
        userMap.put(userId, now);
        return userMap;
    });
}
```

---

### 8. Input-Sanitisierung unvollstandig

**Datei:** `SlashCommand.java:358-432`

**Problem:** Die Sanitisierung deckt nicht alle Unicode-Steuerzeichen ab:
- Keine Behandlung von Unicode-Kategorien wie Cc (Control), Cf (Format)
- Keine Behandlung von Zero-Width-Zeichen
- Keine Behandlung von Right-to-Left Override (U+202E)

**Empfehlung:**
```java
private @NotNull String sanitizeString(@NotNull String input) {
    if (input == null || input.isEmpty()) {
        return input == null ? "" : input;
    }

    StringBuilder result = new StringBuilder(input.length());
    for (int i = 0; i < input.length(); i++) {
        char c = input.charAt(i);
        int type = Character.getType(c);

        // Skip all control and format characters
        if (type == Character.CONTROL ||
            type == Character.FORMAT ||
            type == Character.SURROGATE ||
            type == Character.PRIVATE_USE) {
            continue;
        }

        // Existing escape logic...
        result.append(c);
    }
    return result.toString().trim().replaceAll("\\s+", " ");
}
```

---

### 9. OfflinePlayer Blocking Call

**Datei:** `ArgParsers.java:435-437`

```java
@SuppressWarnings("deprecation")
OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(input);
return ParseResult.success(offlinePlayer);
```

**Problem:** `Bukkit.getOfflinePlayer(String)` kann blockierende I/O-Operationen auslosen (Mojang API Calls).

**Empfehlung:**
- Warnung in Dokumentation hinzufugen
- Async-Variante des Parsers anbieten
- Caching-Mechanismus implementieren

---

### 10. Unzureichendes Quote-Handling ✅ BEHOBEN

**Datei:** `FlagAndKeyValueParser.java:416-426`

```java
private @NotNull String unquote(@NotNull String value) {
    if (value.length() >= 2) {
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }
    }
    return value;
}
```

**Problem:**
- Escaped Anfuhrungszeichen innerhalb des Strings werden nicht behandelt
- `reason="He said \"Hello\""` wird falsch geparst

**Empfehlung:**
```java
private @NotNull String unquote(@NotNull String value) {
    if (value.length() >= 2) {
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            String inner = value.substring(1, value.length() - 1);
            // Handle escaped quotes
            if (first == '"') {
                inner = inner.replace("\\\"", "\"");
            } else {
                inner = inner.replace("\\'", "'");
            }
            return inner.replace("\\\\", "\\");
        }
    }
    return value;
}
```

---

## Niedrige Probleme (LOW)

### 11. Memory Leak Potential in pendingConfirmations

**Datei:** `SlashCommand.java:67`

```java
private static final Map<String, Long> pendingConfirmations = new java.util.concurrent.ConcurrentHashMap<>();
```

**Problem:** Cleanup erfolgt nur bei Command-Execution. Bei hohem Traffic ohne wiederholte Befehle konnen Eintrage akkumulieren.

**Empfehlung:** Periodischen Cleanup implementieren oder WeakHashMap-Alternative verwenden.

---

### 12. Information Disclosure uber Tab-Completion

**Datei:** `TabCompletionHandler.java`

**Problem:** Fuzzy-Matching kann Subcommand-Namen leaken, selbst wenn der Benutzer keine vollstandige Berechtigung hat.

**Empfehlung:** Fuzzy-Matching erst nach Permission-Check anwenden.

---

### 13. Unchecked Type Cast

**Datei:** `ValidationHelper.java:98`

```java
ArgContext.Validator<Object> objValidator = (ArgContext.Validator<Object>) validator;
```

**Problem:** Unchecked cast kann zu ClassCastException fuhren (wird aber gefangen).

**Empfehlung:** Generics-Warnung beheben oder explizit dokumentieren.

---

### 14. CompletionCache evictOldest Thread-Safety

**Datei:** `CompletionCache.java:267-281`

**Problem:** Die Iteration uber `cache.entrySet()` wahrend `evictOldest()` ist nicht atomar mit dem folgenden `cache.remove()`.

**Empfehlung:** Synchronisierung oder atomare Operationen verwenden.

---

## Algorithmus-Analyse

### Performance-Kritische Bereiche

| Stelle | Komplexitat | Risiko |
|--------|-------------|--------|
| `StringSimilarity.levenshteinDistance()` | O(n*m) | DoS bei langen Strings |
| `TabCompletionHandler.filterAndSort()` | O(n log n) | Akzeptabel |
| `CooldownManager.cleanupExpired()` | O(n) | Akzeptabel |
| `FlagAndKeyValueParser.parse()` | O(n*k) | n=args, k=flags - Akzeptabel |

### Empfehlungen zur Performance

1. **String-Langen begrenzen:** Max. 256 Zeichen fur Fuzzy-Matching
2. **Completion-Listen begrenzen:** Max. 1000 Eintrage
3. **Regex-Timeout:** Max. 100ms fur Pattern-Matching

---

## Behobene Sicherheitsaspekte (Positiv)

1. **Gutes Exception-Handling:** Alle Parser-Exceptions werden gefangen
2. **Permission-Checks:** Durchgangig implementiert
3. **Guard-System:** Flexibler Schutz vor Ausfuhrung
4. **Input-Sanitisierung:** Grundlegend vorhanden (HTML-Escaping)
5. **Confirmation-System:** Atomare Operationen fur kritische Befehle
6. **Timeout fur Async-Operations:** 2s Standard-Timeout

---

## Empfohlene Massnahmen (Priorisiert)

### Sofort (P0)
1. ReDoS-Schutz fur Regex-Patterns
2. Overflow-Check im Duration-Parser

### Kurzfristig (P1)
3. String-Langen-Limits fur Levenshtein
4. NaN/Infinity-Validierung in Double/Float-Parsern
5. Cooldown Race-Condition beheben

### Mittelfristig (P2)
6. Input-Sanitisierung erweitern
7. Quote-Escaping verbessern
8. OfflinePlayer-Caching

### Langfristig (P3)
9. Memory-Leak-Monitoring
10. Comprehensive Logging fur Security-Events

---

## Fazit

Das Leviathan Command Framework zeigt insgesamt gute Sicherheitspraktiken, hat jedoch einige Bereiche, die Verbesserungen erfordern. Die kritischsten Probleme sind das ReDoS-Risiko und der Arithmetic Overflow im Duration-Parser. Diese sollten vorrangig behoben werden.

Die Framework-Architektur ist gut durchdacht und ermoglicht einfache Erweiterungen fur zusatzliche Sicherheitsfeatures.

/**
 * Enumeration for shell builtins.
 */
public enum BuiltIn {
    EXIT,
    ECHO,
    TYPE,
    PWD,
    CD,
    HISTORY;

    public static BuiltIn parse(String string) {
        return switch (string.toUpperCase()) {
            case "EXIT" -> EXIT;
            case "ECHO" -> ECHO;
            case "TYPE" -> TYPE;
            case "PWD" -> PWD;
            case "CD" -> CD;
            case "HISTORY" -> HISTORY;
            default -> null;
        };
    }
}

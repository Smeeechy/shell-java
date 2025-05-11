public enum BuiltIn {
    EXIT,
    ECHO,
    TYPE;

    public static BuiltIn parse(String string) {
        return switch (string.toUpperCase()) {
            case "EXIT" -> EXIT;
            case "ECHO" -> ECHO;
            case "TYPE" -> TYPE;
            default -> null;
        };
    }
}

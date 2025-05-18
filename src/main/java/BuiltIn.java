public enum BuiltIn {
    EXIT,
    ECHO,
    TYPE,
    PWD;

    public static BuiltIn parse(String string) {
        return switch (string.toUpperCase()) {
            case "EXIT" -> EXIT;
            case "ECHO" -> ECHO;
            case "TYPE" -> TYPE;
            case "PWD" -> PWD;
            default -> null;
        };
    }
}

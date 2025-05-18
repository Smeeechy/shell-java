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

    public static void execute(BuiltIn command, String args) {
        switch (command) {
            case EXIT:
                System.exit(0);
            case ECHO:
                System.out.println(args != null ? args : "");
                break;
            case TYPE:
                if (args == null || args.isBlank()) break;
                
                String cmd = args.split(" ")[0];

                // check if builtin
                if (parse(cmd) != null) {
                    System.out.println(cmd + " is a shell builtin");
                    break;
                }

                // check if executable found in PATH
                String executablePath = PathScanner.findExecutablePath(cmd);
                if (executablePath != null) {
                    System.out.println(cmd + " is " + executablePath);
                    break;
                }

                System.out.println(cmd + ": not found");
                break;
            default:
                System.out.println(command + ": command not found");
        }
    }
}

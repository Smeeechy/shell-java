import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An object that executes shell commands and tracks relevant state. Able to execute builtins (<code>exit</code>,
 * <code>echo</code>, <code>type</code>, etc.) and external programs (<code>git</code>, <code>ls</code>,
 * <code>cat</code>, etc.).
 */
public class Shell {
    private final PathScanner PATH_SCANNER;

    private Path currentWorkingDirectory;

    public Shell() {
        this.PATH_SCANNER = new PathScanner();
        this.currentWorkingDirectory = Path.of(System.getProperty("user.dir"));
    }

    /**
     * General purpose method for executing a shell command.
     *
     * @param command The raw input string containing commands, subcommands, and any relevant arguments.
     */
    public void execute(String command) {
        List<String> arguments = parseArgs(command);
        String cmd = arguments.getFirst();

        // check for and execute builtin
        BuiltIn builtIn = BuiltIn.parse(cmd);
        if (builtIn != null) {
            executeBuiltIn(arguments);
            return;
        }

        // check for and execute external program
        if (PATH_SCANNER.findExecutablePath(cmd) != null) {
            executeExternal(arguments);
            return;
        }

        // unknown command
        System.err.println(cmd + ": command not found");
    }

    /**
     * Helper for executing a builtin command, like <code>cd</code> or <code>echo</code>.
     *
     * @param arguments a list of strings containing all relevant command arguments
     */
    private void executeBuiltIn(List<String> arguments) {
        BuiltIn builtIn = BuiltIn.parse(arguments.removeFirst());
        switch (builtIn) {
            case EXIT -> System.exit(0);
            case ECHO -> System.out.println(String.join(" ", arguments));
            case TYPE -> {
                if (arguments.isEmpty()) return;
                printType(arguments.getFirst());
            }
            case PWD -> System.out.println(currentWorkingDirectory.toAbsolutePath());
            case CD -> {
                if (arguments.isEmpty()) changeDirectory("~");
                else changeDirectory(arguments.getFirst());
            }
            case null, default -> System.err.println(builtIn + ": no handler for builtin");
        }
    }

    /**
     * Helper for executing an external command, like <code>git</code> or <code>docker</code>.
     *
     * @param argList A list of strings containing all relevant command arguments
     */
    private void executeExternal(List<String> argList) {
        try {
            Process process = new ProcessBuilder(argList)
                    .directory(currentWorkingDirectory.toFile()) // runs command from current directory
                    .redirectErrorStream(true) // sends errors to input stream so they can be read with a single reader
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) System.out.println(line);
            process.waitFor(); // blocks until process finishes. returns an exit code that can be used later maybe
        } catch (Exception e) {
            System.err.println("Error executing external program: " + e.getMessage());
        }
    }

    /**
     * Helper used to break down a single string of arguments into a list of tokens.
     *
     * @param argsString The string containing all command arguments
     * @return A list of individual argument strings
     */
    private List<String> parseArgs(String argsString) {
        if (argsString == null || argsString.isEmpty()) return new ArrayList<>();

        List<String> arguments = new ArrayList<>();
        int index = 0;
        StringBuilder builder = new StringBuilder();
        boolean withinSingleQuotes = false;
        boolean withinDoubleQuotes = false;
        while (index < argsString.length()) {
            char charAtIndex = argsString.charAt(index);
            switch (charAtIndex) {
                case '\\' -> {
                    if (withinDoubleQuotes && index + 1 < argsString.length()) {
                        char nextChar = argsString.charAt(index + 1);
                        switch (nextChar) {
                            case '\\', '$', '\"', '\n' -> {
                                builder.append(nextChar);
                                index++;
                            }
                            default -> builder.append(charAtIndex);
                        }
                    } else if (!withinSingleQuotes && index + 1 < argsString.length()) {
                        builder.append(argsString.charAt(index + 1));
                        index++;
                    } else builder.append(charAtIndex);
                }
                case '\'' -> {
                    if (withinDoubleQuotes) builder.append(charAtIndex);
                    else withinSingleQuotes = !withinSingleQuotes;
                }
                case '\"' -> {
                    if (withinSingleQuotes) builder.append(charAtIndex);
                    else withinDoubleQuotes = !withinDoubleQuotes;
                }
                case ' ' -> {
                    if (withinSingleQuotes || withinDoubleQuotes) builder.append(charAtIndex);
                    else if (!builder.isEmpty()) {
                        arguments.add(builder.toString().trim());
                        builder = new StringBuilder();
                    }
                }
                default -> builder.append(charAtIndex);
            }
            index++;
        }
        if (!builder.isEmpty()) arguments.add(builder.toString());
        return arguments;
    }

    /**
     * Helper used to validate a string representing an absolute or relative path before updating the current working
     * directory.
     *
     * @param directory A string representation of the proposed new working directory
     */
    private void changeDirectory(String directory) {
        // interpolate with home directory when applicable
        directory = directory.replaceAll("^~", System.getenv("HOME"));

        // not a requirement but a nice QOL
        if (directory.equals("..")) {
            directory = currentWorkingDirectory.toAbsolutePath().getParent().toString();
        }

        // handle navigating backwards relative to cwd
        if (directory.startsWith("../")) {
            Path tempWorkingDirectory = Path.of(currentWorkingDirectory.toUri());
            while (directory.startsWith("../")) {
                directory = directory.replaceFirst("\\.\\./", "");
                tempWorkingDirectory = tempWorkingDirectory.getParent();
                if (tempWorkingDirectory == null) tempWorkingDirectory = Path.of("/");
            }
            directory = tempWorkingDirectory.toAbsolutePath() + directory;
        }

        // handle relative paths
        if (directory.startsWith("./")) {
            directory = directory.replaceFirst("\\.", currentWorkingDirectory.toString());
        }

        // validate final path
        Path directoryPath;
        try {
            directoryPath = Path.of(directory);
        } catch (Exception ignored) {
            System.err.println("Error parsing path: " + directory);
            return;
        }
        if (!Files.exists(directoryPath)) {
            System.err.println("cd: " + directory + ": No such file or directory");
            return;
        }
        if (!Files.isDirectory(directoryPath)) {
            System.err.println("cd: " + directory + ": Not a directory");
            return;
        }

        this.currentWorkingDirectory = directoryPath;
    }

    /**
     * Helper used to determine and print the type of a given command.
     *
     * @param command the command of which to print the type
     */
    private void printType(String command) {
        if (command == null || command.isBlank()) return;

        // shell builtins
        if (BuiltIn.parse(command) != null) {
            System.out.println(command + " is a shell builtin");
            return;
        }

        // external programs
        String executablePath = PATH_SCANNER.findExecutablePath(command);
        if (executablePath != null) {
            System.out.println(command + " is " + executablePath);
            return;
        }

        System.err.println(command + ": not found");
    }
}

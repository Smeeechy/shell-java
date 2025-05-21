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
        String[] tokens = command.split(" ", 2);
        String cmd = tokens[0];
        String args = tokens.length > 1 ? tokens[1] : null;

        // check for and execute builtin
        BuiltIn builtIn = BuiltIn.parse(cmd);
        if (builtIn != null) {
            executeBuiltIn(builtIn, args);
            return;
        }

        // check for and execute external program
        if (PATH_SCANNER.findExecutablePath(cmd) != null) {
            executeExternal(cmd, args);
            return;
        }

        // unknown command
        System.err.println(cmd + ": command not found");
    }

    /**
     * Helper for executing a builtin command, like <code>cd</code> or <code>echo</code>.
     *
     * @param builtIn The builtin command to execute
     * @param args    a single string containing all relevant command arguments
     */
    private void executeBuiltIn(BuiltIn builtIn, String args) {
        switch (builtIn) {
            case EXIT:
                System.exit(0);
            case ECHO:
                System.out.println(String.join(" ", parseArgs(args)));
                return;
            case TYPE:
                printType(args);
                return;
            case PWD:
                System.out.println(currentWorkingDirectory.toAbsolutePath());
                return;
            case CD:
                changeDirectory(args);
                return;
            default:
                System.err.println(builtIn + ": command not found");
        }
    }

    /**
     * Helper for executing an external command, like <code>git</code> or <code>docker</code>.
     *
     * @param command The external command to execute
     * @param args    A single string containing all relevant command arguments
     */
    private void executeExternal(String command, String args) {
        List<String> commandList = new ArrayList<>();
        commandList.add(command);
        if (args != null && args.length() > 1) {
            final List<String> arguments = parseArgs(args);
            commandList.addAll(arguments);
        }
        try {
            Process process = new ProcessBuilder(commandList)
                    .directory(currentWorkingDirectory.toFile()) // runs command from current directory
                    .redirectErrorStream(true) // sends errors to input stream so they can be read with a single reader
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) System.out.println(line);
            process.waitFor(); // blocks until process finishes. returns an exit code that can be used later maybe
        } catch (Exception e) {
            System.err.println("Error executing " + command + ": " + e.getMessage());
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
                case '\\':
                    if (withinDoubleQuotes && index + 1 < argsString.length()) {
                        char nextChar = argsString.charAt(index + 1);
                        switch (nextChar) {
                            case '\\':
                            case '$':
                            case '\"':
                            case '\n':
                                builder.append(nextChar);
                                index++;
                                break;
                            default:
                                builder.append(charAtIndex);
                        }
                    } else builder.append(charAtIndex);
                    break;
                case '\'':
                    if (withinDoubleQuotes) builder.append(charAtIndex);
                    else withinSingleQuotes = !withinSingleQuotes;
                    break;
                case '\"':
                    if (withinSingleQuotes) builder.append(charAtIndex);
                    else withinDoubleQuotes = !withinDoubleQuotes;
                    break;
                case ' ':
                    if (withinSingleQuotes || withinDoubleQuotes) {
                        builder.append(charAtIndex);
                    } else if (!builder.isEmpty()) {
                        arguments.add(builder.toString().trim());
                        builder = new StringBuilder();
                    }
                    break;
                default:
                    builder.append(charAtIndex);
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
        directory = directory.trim();

        // only argument should be target directory
        String[] args = directory.split(" ");
        if (args.length != 1) {
            System.err.println("cd: too many arguments");
            return;
        }

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

        // if provided with arguments for some reason, ignore them
        String cmd = command.split(" ")[0];

        // shell builtins
        if (BuiltIn.parse(cmd) != null) {
            System.out.println(cmd + " is a shell builtin");
            return;
        }

        // external programs
        String executablePath = PATH_SCANNER.findExecutablePath(cmd);
        if (executablePath != null) {
            System.out.println(cmd + " is " + executablePath);
            return;
        }

        System.err.println(cmd + ": not found");
    }
}

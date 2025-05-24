import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
     * General-purpose method for executing a shell command.
     *
     * @param commandString The raw input string containing commands, subcommands, and any relevant arguments
     * @param debug         Flag for printing commands in pipeline before execution
     */
    public void execute(String commandString, boolean debug) {
        final List<Command> commands = parseArgs(commandString);
        if (debug) commands.forEach(System.out::println);
        executePipeline(commands);
    }

    /**
     * Executes a single shell command.
     *
     * @param command The command to be executed
     */
    private void executeCommand(Command command) {
        if (command == null || command.arguments().isEmpty()) return;
        final String cmd = command.arguments().getFirst();

        // check for and execute builtin
        final BuiltIn builtIn = BuiltIn.parse(cmd);
        if (builtIn != null) {
            executeBuiltIn(command);
            return;
        }

        // check for and execute external program
        if (PATH_SCANNER.findExecutablePath(cmd) != null) {
            executeExternal(command);
            return;
        }

        // unknown command
        System.err.println(cmd + ": command not found");
    }

    /**
     * Executes a series of commands, feeding the output of one command as input to the next.
     * Redirections override this behavior.
     *
     * @param commands A list of commands to be executed as a pipeline
     */
    private void executePipeline(List<Command> commands) {
        for (int i = 0; i < commands.size(); i++) {
            Command command = commands.get(i);
            if (command.outRedirect() != null && i < commands.size() - 1) {
                Command nextCommand = commands.get(i + 1);
                // TODO handle redirects here
            }
            executeCommand(command);
        }
    }

    /**
     * Helper for executing a builtin command, like <code>cd</code>, <code>echo</code>, and <code>exit</code>.
     *
     * @param command An object containing the command and any relevant arguments
     */
    private void executeBuiltIn(Command command) {
        final PrintStream stdOut = System.out;
        final PrintStream stdErr = System.err;

        if (command.outRedirect() != null) {
            try {
                String outPath = resolvePath(command.outRedirect());
                FileOutputStream outFos = new FileOutputStream(outPath, command.outAppend());
                System.setOut(new PrintStream(outFos));
            } catch (Exception ignored) {
                System.out.println("Unable to redirect stdout to " + command.outRedirect());
                return;
            }
        }

        if (command.errRedirect() != null) {
            try {
                String errPath = resolvePath(command.errRedirect());
                FileOutputStream errFos = new FileOutputStream(errPath, command.errAppend());
                System.setErr(new PrintStream(errFos));
            } catch (Exception ignored) {
                System.out.println("Unable to redirect stderr to " + command.errRedirect());
                return;
            }
        }

        final BuiltIn builtIn = BuiltIn.parse(command.arguments().removeFirst());
        switch (builtIn) {
            case EXIT -> System.exit(0);
            case ECHO -> System.out.println(String.join(" ", command.arguments()));
            case TYPE -> command.arguments().forEach(this::printType);
            case PWD -> System.out.println(currentWorkingDirectory.toAbsolutePath());
            case CD -> {
                if (command.arguments().isEmpty()) changeDirectory("~");
                else changeDirectory(command.arguments().getFirst());
            }
            case null, default -> System.err.println(builtIn + ": no handler for builtin");
        }

        // reset stdOut and stdErr
        System.setOut(stdOut);
        System.setErr(stdErr);
    }

    /**
     * Helper for executing external commands, like <code>ls</code>, <code>cat</code>, and <code>git</code>.
     *
     * @param command An object containing the command and any relevant arguments
     */
    private void executeExternal(Command command) {
        try {
            // setup process according to inputs
            final ProcessBuilder processBuilder = new ProcessBuilder(command.arguments())
                    .directory(currentWorkingDirectory.toFile()); // runs command from current directory
            if (command.outRedirect() != null) {
                File outFile = Paths.get(resolvePath(command.outRedirect())).toFile();
                if (command.outAppend()) processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                else processBuilder.redirectOutput(outFile);
            }
            if (command.errRedirect() != null) {
                File errFile = Paths.get(resolvePath(command.errRedirect())).toFile();
                if (command.errAppend()) processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                else processBuilder.redirectError(errFile);
            }
            final Process process = processBuilder
                    .redirectErrorStream(command.outRedirect() == null && command.errRedirect() == null)
                    .start();

            // print output and errors
            String line;
            final BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = outputReader.readLine()) != null) System.out.println(line);
            while ((line = errorReader.readLine()) != null) System.err.println(line);
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Error executing external program: " + e.getMessage());
        }
    }

    /**
     * Helper used to break down a single string of arguments into a list of tokens.
     *
     * @param commandString A string containing all command arguments
     * @return A list of commands representing a pipeline
     */
    private List<Command> parseArgs(String commandString) {
        if (commandString == null || commandString.isEmpty()) return null;

        final List<String> parsedArgs = new ArrayList<>();
        int index = 0;
        StringBuilder builder = new StringBuilder();
        boolean withinSingleQuotes = false;
        boolean withinDoubleQuotes = false;
        while (index < commandString.length()) {
            final char charAtIndex = commandString.charAt(index);
            switch (charAtIndex) {
                case '\\' -> {
                    if (withinDoubleQuotes && index + 1 < commandString.length()) {
                        final char nextChar = commandString.charAt(index + 1);
                        switch (nextChar) {
                            case '\\', '$', '\"', '\n' -> {
                                builder.append(nextChar);
                                index++;
                            }
                            default -> builder.append(charAtIndex);
                        }
                    } else if (!withinSingleQuotes && index + 1 < commandString.length()) {
                        builder.append(commandString.charAt(index + 1));
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
                        parsedArgs.add(builder.toString().trim());
                        builder = new StringBuilder();
                    }
                }
                default -> builder.append(charAtIndex);
            }
            index++;
        }
        if (!builder.isEmpty()) parsedArgs.add(builder.toString());
        return parsePipeline(parsedArgs);
    }

    /**
     * Groups individual tokens into a list of commands
     *
     * @param parsedArgs A list of command arguments
     * @return A list of commands representing a pipeline
     */
    private List<Command> parsePipeline(List<String> parsedArgs) {
        List<Command> commands = new ArrayList<>();
        List<String> commandArgs = new ArrayList<>();
        for (String arg : parsedArgs) {
            if (arg.equals("|")) {
                commands.add(parseCommand(commandArgs));
                commandArgs.clear();
            } else commandArgs.add(arg);
        }
        if (!commandArgs.isEmpty()) commands.add(parseCommand(commandArgs));
        return commands;
    }

    /**
     * Parses a list of arguments into a single command object.
     *
     * @param parsed A list of strings representing the command and its arguments
     * @return A command object formed from the given argument tokens
     */
    private Command parseCommand(List<String> parsed) {
        final List<String> arguments = new ArrayList<>();
        String outRedirect = null;
        boolean outAppend = false;
        String errRedirect = null;
        boolean errAppend = false;
        for (int i = 0; i < parsed.size(); i++) {
            final String arg = parsed.get(i);
            if (i == parsed.size() - 1) {
                arguments.add(arg);
                break;
            }

            switch (arg) {
                case ">>":
                case "1>>":
                    outAppend = true;
                case ">":
                case "1>":
                    outRedirect = parsed.get(++i);
                    break;

                case "2>>":
                    errAppend = true;
                case "2>":
                    errRedirect = parsed.get(++i);
                    break;

                default:
                    arguments.add(arg);
            }
        }

        return new Command(arguments, outRedirect, outAppend, errRedirect, errAppend);
    }

    /**
     * Helper used to validate a string representing an absolute or relative path before updating the current working
     * directory.
     *
     * @param directory A string representation of the proposed new working directory
     */
    private void changeDirectory(String directory) {
        directory = resolvePath(directory);

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
     * Helper for resolving a path against the current working directory.
     *
     * @param path A string representing an absolute or relative path
     * @return A string representing the resolved path
     */
    private String resolvePath(String path) {
        // interpolate with home directory when applicable
        path = path.replaceAll("^~", System.getenv("HOME"));

        // not a requirement but a nice QOL
        if (path.equals("..")) {
            path = currentWorkingDirectory.toAbsolutePath().getParent().toString();
        }

        // handle navigating backwards relative to cwd
        if (path.startsWith("../")) {
            Path tempWorkingDirectory = Path.of(currentWorkingDirectory.toUri());
            while (path.startsWith("../")) {
                path = path.replaceFirst("\\.\\./", "");
                tempWorkingDirectory = tempWorkingDirectory.getParent();
                if (tempWorkingDirectory == null) tempWorkingDirectory = Path.of("/");
            }
            path = tempWorkingDirectory.toAbsolutePath() + path;
        }

        // handle relative paths
        if (path.startsWith("./")) {
            path = path.replaceFirst("\\.", currentWorkingDirectory.toString());
        }

        return path;
    }

    /**
     * Helper used to determine and print the type of a given command.
     *
     * @param commandString the command of which to print the type
     */
    private void printType(String commandString) {
        // shell builtins
        if (BuiltIn.parse(commandString) != null) {
            System.out.println(commandString + " is a shell builtin");
            return;
        }

        // external programs
        final String executablePath = PATH_SCANNER.findExecutablePath(commandString);
        if (executablePath != null) {
            System.out.println(commandString + " is " + executablePath);
            return;
        }

        System.err.println(commandString + ": not found");
    }
}

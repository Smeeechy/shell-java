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
     * General purpose method for executing a shell command.
     *
     * @param command The raw input string containing commands, subcommands, and any relevant arguments.
     */
    public void execute(String command) {
        final List<String> arguments = new ArrayList<>();
        String outRedirect = null;
        boolean outAppend = false;
        String errRedirect = null;
        boolean errAppend = false;

        // check for redirects
        final List<String> parsed = parseArgs(command);
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

        final String cmd = arguments.getFirst();

        // check for and execute builtin
        final BuiltIn builtIn = BuiltIn.parse(cmd);
        if (builtIn != null) {
            executeBuiltIn(arguments, outRedirect, outAppend, errRedirect, errAppend);
            return;
        }

        // check for and execute external program
        if (PATH_SCANNER.findExecutablePath(cmd) != null) {
            executeExternal(arguments, outRedirect, outAppend, errRedirect, errAppend);
            return;
        }

        // unknown command
        System.err.println(cmd + ": command not found");
    }

    /**
     * Helper for executing a builtin command, like <code>cd</code>, <code>echo</code>, and <code>exit</code>.
     *
     * @param argList     a list of strings containing all relevant command arguments
     * @param outRedirect the location to redirect stdout
     * @param outAppend   flag for appending to outRedirect
     * @param errRedirect the location to redirect stderr
     * @param errAppend   flag for appending to errRedirect
     */
    private void executeBuiltIn(List<String> argList, String outRedirect, boolean outAppend, String errRedirect, boolean errAppend) {
        final PrintStream stdOut = System.out;
        final PrintStream stdErr = System.err;
        if (outRedirect != null) {
            try (FileOutputStream fos = new FileOutputStream(outRedirect, outAppend)) {
                System.setOut(new PrintStream(fos));
            } catch (Exception ignored) {
                System.out.println("Unable to redirect stdout to " + outRedirect);
                return;
            }
        }
        if (errRedirect != null) {
            try (FileOutputStream fos = new FileOutputStream(errRedirect, errAppend)) {
                System.setErr(new PrintStream(fos));
            } catch (Exception ignored) {
                System.out.println("Unable to redirect stderr to " + errRedirect);
                return;
            }
        }

        final BuiltIn builtIn = BuiltIn.parse(argList.removeFirst());
        switch (builtIn) {
            case EXIT -> System.exit(0);
            case ECHO -> System.out.println(String.join(" ", argList));
            case TYPE -> argList.forEach(this::printType);
            case PWD -> System.out.println(currentWorkingDirectory.toAbsolutePath());
            case CD -> {
                if (argList.isEmpty()) changeDirectory("~");
                else changeDirectory(argList.getFirst());
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
     * @param argList     A list of strings representing the command and all relevant arguments
     * @param outRedirect the location to redirect stdout
     * @param outAppend   flag for appending to outRedirect
     * @param errRedirect the location to redirect stderr
     * @param errAppend   flag for appending to errRedirect
     */
    private void executeExternal(List<String> argList, String outRedirect, boolean outAppend, String errRedirect, boolean errAppend) {
        try {
            // setup process according to inputs
            final ProcessBuilder processBuilder = new ProcessBuilder(argList)
                    .directory(currentWorkingDirectory.toFile()); // runs command from current directory
            if (outRedirect != null) {
                File outFile = Paths.get(currentWorkingDirectory.toString(), outRedirect).toFile();
                if (outAppend) processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(outFile));
                else processBuilder.redirectOutput(outFile);
            }
            if (errRedirect != null) {
                File errFile = Paths.get(currentWorkingDirectory.toString(), errRedirect).toFile();
                if (errAppend) processBuilder.redirectError(ProcessBuilder.Redirect.appendTo(errFile));
                else processBuilder.redirectError(errFile);
            }
            final Process process = processBuilder
                    .redirectErrorStream(outRedirect == null && errRedirect == null)
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
     * @param command A string containing all command arguments
     * @return A list of individual argument strings
     */
    private List<String> parseArgs(String command) {
        if (command == null || command.isEmpty()) return new ArrayList<>();

        final List<String> arguments = new ArrayList<>();
        int index = 0;
        StringBuilder builder = new StringBuilder();
        boolean withinSingleQuotes = false;
        boolean withinDoubleQuotes = false;
        while (index < command.length()) {
            final char charAtIndex = command.charAt(index);
            switch (charAtIndex) {
                case '\\' -> {
                    if (withinDoubleQuotes && index + 1 < command.length()) {
                        final char nextChar = command.charAt(index + 1);
                        switch (nextChar) {
                            case '\\', '$', '\"', '\n' -> {
                                builder.append(nextChar);
                                index++;
                            }
                            default -> builder.append(charAtIndex);
                        }
                    } else if (!withinSingleQuotes && index + 1 < command.length()) {
                        builder.append(command.charAt(index + 1));
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
        // shell builtins
        if (BuiltIn.parse(command) != null) {
            System.out.println(command + " is a shell builtin");
            return;
        }

        // external programs
        final String executablePath = PATH_SCANNER.findExecutablePath(command);
        if (executablePath != null) {
            System.out.println(command + " is " + executablePath);
            return;
        }

        System.err.println(command + ": not found");
    }
}

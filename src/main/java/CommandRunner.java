import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CommandRunner {
    private final PathScanner PATH_SCANNER;
    private final Command command;
    private final Shell shell;
    private final BuiltIn builtIn;
    private final ProcessBuilder processBuilder;
    private InputStream inputStream;
    private OutputStream outputStream;
    private OutputStream errorStream;
    private Thread inputThread;
    private Thread outputThread;
    private Thread errorThread;
    private Thread builtInThread;
    private Process process;

    public CommandRunner(Command command, Shell shell) {
        this.PATH_SCANNER = new PathScanner();
        this.command = command;
        this.shell = shell;
        this.builtIn = BuiltIn.parse(command.arguments().getFirst());
        this.processBuilder = new ProcessBuilder().command(command.arguments()).directory(shell.getCwd().toFile());
        this.inputStream = System.in;
        this.outputStream = System.out;
        this.errorStream = System.err;

        // handle redirects when applicable
        try {
            if (command.inRedirect() != null)
                this.inputStream = new FileInputStream(command.inRedirect());
            if (command.outRedirect() != null)
                this.outputStream = new FileOutputStream(command.outRedirect(), command.outAppend());
            if (command.errRedirect() != null)
                this.errorStream = new FileOutputStream(command.errRedirect(), command.errAppend());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Starts a number of threads to handle command execution.
     *
     * @throws IOException if error occurs during process execution
     * @implNote some shells--including this one--print the calling history command as the last entry,
     * while others omit it
     */
    public void start() throws IOException {
        if (builtIn != null) {
            builtInThread = new Thread(() -> {
                PrintStream out = new PrintStream(outputStream, true);
                PrintStream err = new PrintStream(errorStream, true);
                // skip the command itself
                List<String> args = command.arguments().subList(1, command.arguments().size());
                switch (builtIn) {
                    case EXIT -> System.exit(0);
                    case ECHO -> out.println(String.join(" ", args));
                    case TYPE -> args.forEach(this::printType);
                    case PWD -> out.println(shell.getCwd().toAbsolutePath());
                    case CD -> {
                        if (args.isEmpty()) changeDirectory("~");
                        else changeDirectory(args.getFirst());
                    }
                    case HISTORY -> {
                        List<String> history = shell.getHistory();
                        int n = history.size();
                        if (args.getFirst().matches("^\\d+$")) {
                            n = Integer.parseInt(args.getFirst());
                            if (n > history.size()) n = history.size();
                        }
                        for (int i = history.size() - n; i < history.size(); i++)
                            out.println("    " + (i + 1) + "  " + history.get(i));
                    }
                    default -> err.println(builtIn + ": no handler for builtin");
                }
            });
            builtInThread.start();
            return;
        }

        // handles unknown commands
        if (PATH_SCANNER.findExecutablePath(command.arguments().getFirst()) == null) {
            String message = command.arguments().getFirst() + ": command not found\n";
            errorStream.write(message.getBytes(), 0, message.length());
            return;
        }

        process = processBuilder.start();

        // redirect input (if non-standard)
        if (inputStream != System.in) {
            inputThread = new Thread(() -> {
                try (OutputStream processOut = process.getOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        processOut.write(buffer, 0, bytesRead);
                        processOut.flush();
                    }
                } catch (IOException ignored) {
                }
            });
            inputThread.start();
        } else {
            // TODO this is not a good solution. some commands still need System.in
            process.getOutputStream().close();
        }

        // redirect output
        outputThread = new Thread(() -> {
            try (InputStream processIn = process.getInputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = processIn.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                }
            } catch (IOException ignored) {
            }
        });
        outputThread.start();

        // redirect errors
        errorThread = new Thread(() -> {
            try (InputStream processError = process.getErrorStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = processError.read(buffer)) != -1) {
                    errorStream.write(buffer, 0, bytesRead);
                    errorStream.flush();
                }
            } catch (IOException ignored) {
            }
        });
        errorThread.start();
    }

    public int waitFor() throws InterruptedException {
        if (builtInThread != null) {
            builtInThread.join();
            if (outputStream != System.out) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    System.err.println(e.getMessage());
                }
            }
            return 0;
        }

        // return exit code for 'command not found'
        if (process == null) return 127;

        int exitCode = process.waitFor();

        // wait for threads to complete
        if (inputThread != null) inputThread.join();
        if (outputThread != null) outputThread.join();
        if (errorThread != null) errorThread.join();

        // close piped/redirected streams
        try {
            if (inputStream != System.in) inputStream.close();
            if (outputStream != System.out) outputStream.close();
            if (errorStream != System.err) errorStream.close();
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        return exitCode;
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
        PrintStream err = new PrintStream(errorStream, true);
        try {
            directoryPath = Path.of(directory);
        } catch (Exception ignored) {
            err.println("Could not resolve directory: " + directory);
            return;
        }
        if (!Files.exists(directoryPath)) {
            err.println("cd: " + directory + ": No such file or directory");
            return;
        }
        if (!Files.isDirectory(directoryPath)) {
            err.println("cd: " + directory + ": Not a directory");
            return;
        }

        shell.setCwd(directoryPath);
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
            path = shell.getCwd().toAbsolutePath().getParent().toString();
        }

        // handle navigating backwards relative to cwd
        if (path.startsWith("../")) {
            Path tempWorkingDirectory = Path.of(shell.getCwd().toUri());
            while (path.startsWith("../")) {
                path = path.replaceFirst("\\.\\./", "");
                tempWorkingDirectory = tempWorkingDirectory.getParent();
                if (tempWorkingDirectory == null) tempWorkingDirectory = Path.of("/");
            }
            path = tempWorkingDirectory.toAbsolutePath() + path;
        }

        // handle relative paths
        if (path.startsWith("./")) {
            path = path.replaceFirst("\\.", shell.getCwd().toString());
        }

        return path;
    }

    /**
     * Helper used to determine and print the type of a given command.
     *
     * @param commandString the command of which to print the type
     */
    private void printType(String commandString) {
        PrintStream out = new PrintStream(outputStream, true);
        PrintStream err = new PrintStream(errorStream, true);

        // shell builtins
        if (BuiltIn.parse(commandString) != null) {
            out.println(commandString + " is a shell builtin");
            return;
        }

        // external programs
        final String executablePath = PATH_SCANNER.findExecutablePath(commandString);
        if (executablePath != null) {
            out.println(commandString + " is " + executablePath);
            return;
        }

        err.println(commandString + ": not found");
    }

    public Command getCommand() {
        return command;
    }

    public InputStream getInputStream() {
        return inputStream;
    }

    public void setInputStream(InputStream inputStream) {
        if (command.inRedirect() == null) this.inputStream = inputStream;
    }

    public OutputStream getOutputStream() {
        return outputStream;
    }

    public void setOutputStream(OutputStream outputStream) {
        if (command.outRedirect() == null) this.outputStream = outputStream;
    }

    public OutputStream getErrorStream() {
        return errorStream;
    }

    public void setErrorStream(OutputStream errorStream) {
        if (command.errRedirect() == null) this.errorStream = errorStream;
    }
}

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
            if (command.inRedirect() != null) this.inputStream = new FileInputStream(command.inRedirect());
            if (command.outRedirect() != null) this.outputStream = new FileOutputStream(command.outRedirect());
            if (command.errRedirect() != null) this.errorStream = new FileOutputStream(command.errRedirect());
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    public void start() throws IOException {
        String cmdName = command.arguments().get(0);

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
                    default -> err.println(builtIn + ": no handler for builtin");
                }
            });
            builtInThread.start();
            return;
        }

        // Debug: Log process start
        System.err.println("[DEBUG] Starting process: " + String.join(" ", command.arguments()));
        System.err.println("[DEBUG] Input stream type: " + inputStream.getClass().getSimpleName());
        System.err.println("[DEBUG] Output stream type: " + outputStream.getClass().getSimpleName());

        process = processBuilder.start();

        // redirect input (if non-standard)
        if (inputStream != System.in) {
            inputThread = new Thread(() -> {
                try (OutputStream processOut = process.getOutputStream()) {
                    System.err.println("[DEBUG] " + cmdName + " input thread started, reading from " + inputStream.getClass().getSimpleName());

                    // Use a buffer and flush frequently for better data flow
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        processOut.write(buffer, 0, bytesRead);
                        processOut.flush(); // Force flush after each write
                        System.err.println("[DEBUG] " + cmdName + " transferred " + bytesRead + " bytes");
                    }
                    System.err.println("[DEBUG] " + cmdName + " input thread finished");
                } catch (IOException e) {
                    System.err.println("[DEBUG] " + cmdName + " input thread error: " + e.getMessage());
                }
            });
            inputThread.start();
        } else {
            // TODO this is not a good solution. some commands still need System.in
            System.err.println("[DEBUG] " + cmdName + " closing stdin (no input redirect)");
            process.getOutputStream().close();
        }

        // redirect output
        outputThread = new Thread(() -> {
            try (InputStream processIn = process.getInputStream()) {
                System.err.println("[DEBUG] " + cmdName + " output thread started, writing to " + outputStream.getClass().getSimpleName());

                // Use a buffer and flush frequently
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = processIn.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    if (outputStream instanceof PrintStream) {
                        ((PrintStream) outputStream).flush();
                    } else {
                        outputStream.flush();
                    }
                    System.err.println("[DEBUG] " + cmdName + " output " + bytesRead + " bytes");
                }
                System.err.println("[DEBUG] " + cmdName + " output thread finished");
            } catch (IOException e) {
                System.err.println("[DEBUG] " + cmdName + " output thread error: " + e.getMessage());
            }
        });
        outputThread.start();

        // redirect errors
        errorThread = new Thread(() -> {
            try (InputStream processError = process.getErrorStream()) {
                processError.transferTo(errorStream);
            } catch (IOException ignored) {
            }
        });
        errorThread.start();
    }

    public int waitFor() throws InterruptedException {
        String cmdName = command.arguments().get(0);

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

        System.err.println("[DEBUG] Waiting for " + cmdName + " to complete");
        int exitCode = process.waitFor();
        System.err.println("[DEBUG] " + cmdName + " exited with code " + exitCode);

        // wait for threads to complete
        if (inputThread != null) {
            System.err.println("[DEBUG] Waiting for " + cmdName + " input thread");
            inputThread.join();
        }
        if (outputThread != null) {
            System.err.println("[DEBUG] Waiting for " + cmdName + " output thread");
            outputThread.join();
        }
        if (errorThread != null) {
            errorThread.join();
        }

        // close piped/redirected streams
        try {
            if (inputStream != System.in) {
                System.err.println("[DEBUG] Closing input stream for " + cmdName);
                inputStream.close();
            }
            if (outputStream != System.out) {
                System.err.println("[DEBUG] Closing output stream for " + cmdName);
                outputStream.close();
            }
            if (errorStream != System.err) {
                errorStream.close();
            }
        } catch (IOException e) {
            System.err.println("[DEBUG] Error closing streams for " + cmdName + ": " + e.getMessage());
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

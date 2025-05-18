import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Shell {
    private Path currentWorkingDirectory;
    private PathScanner pathScanner;

    public Shell() {
        this.currentWorkingDirectory = Path.of(System.getProperty("user.dir"));
        this.pathScanner = new PathScanner();
    }

    public void execute(String command) {
        String[] tokens = command.split(" ", 2);
        String cmd = tokens[0];
        String args = tokens.length > 1 ? tokens[1] : null;

        // try to execute builtin
        BuiltIn builtIn = BuiltIn.parse(cmd);
        if (builtIn != null) {
            executeBuiltIn(builtIn, args);
            return;
        }

        // try to execute external program
        if (pathScanner.findExecutablePath(cmd) != null) {
            executeExternal(cmd, args);
            return;
        }

        // unknown command
        System.err.println(cmd + ": command not found");
    }

    private void executeBuiltIn(BuiltIn builtIn, String args) {
        switch (builtIn) {
            case EXIT:
                System.exit(0);
            case ECHO:
                System.out.println(args != null ? args : "");
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

    private void changeDirectory(String directory) {
        String[] args = directory.split(" ");
        if (args.length != 1) {
            System.err.println("cd: too many arguments");
            return;
        }

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

    private void printType(String args) {
        if (args == null || args.isBlank()) return;

        String cmd = args.split(" ")[0];

        if (BuiltIn.parse(cmd) != null) {
            System.out.println(cmd + " is a shell builtin");
            return;
        }

        String executablePath = pathScanner.findExecutablePath(cmd);
        if (executablePath != null) {
            System.out.println(cmd + " is " + executablePath);
            return;
        }

        System.err.println(cmd + ": not found");
    }

    private void executeExternal(String command, String args) {
        List<String> commandList = new ArrayList<>();
        commandList.add(command);
        if (args != null && args.length() > 1) {
            final List<String> arguments = parseArgs(args);
            commandList.addAll(arguments);
        }
        try {
            Process process = new ProcessBuilder(commandList)
                    .directory(currentWorkingDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) System.out.println(line);
            process.waitFor();
        } catch (Exception e) {
            System.err.println("Error executing " + command + ": " + e.getMessage());
        }
    }

    private List<String> parseArgs(String argsString) {
        argsString = argsString.trim().replace('\'', '\"');
        List<String> arguments = new ArrayList<>();
        int index = 0;
        StringBuilder builder = new StringBuilder();
        boolean insideString = false;
        while (index < argsString.length()) {
            char charAtIndex = argsString.charAt(index);
            switch (charAtIndex) {
                case '\"':
                    if (insideString) {
                        arguments.add(builder.toString());
                        builder = new StringBuilder();
                    } else {
                        insideString = true;
                    }
                    break;
                case ' ':
                    if (insideString) {
                        builder.append(charAtIndex);
                    } else {
                        arguments.add(builder.toString());
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
}

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("$ ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();
            if (input.isBlank()) continue;
            String[] tokens = input.split(" ", 2);

            // check if command is built-in
            BuiltIn builtIn = BuiltIn.parse(tokens[0]);
            if (builtIn != null) {
                String arguments = tokens.length > 1 ? tokens[1] : null;
                BuiltIn.execute(builtIn, arguments);
                continue;
            }

            // check if command is external
            String executablePath = PathScanner.findExecutablePath(tokens[0]);
            if (executablePath != null) {
                List<String> command = new ArrayList<>();
                command.add(executablePath);
                if (tokens.length > 1) {
                    final List<String> arguments = parseArgs(tokens[1]);
                    command.addAll(arguments);
                }
                try {
                    Process process = new ProcessBuilder(command).start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        BufferedReader error = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String errorLine;
                        while ((errorLine = error.readLine()) != null) {
                            System.err.println(errorLine);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error executing " + tokens[0] + ": " + e.getMessage());
                }
                continue;
            }

            System.out.println("Unknown command: " + tokens[0]);
        }
    }

    private static List<String> parseArgs(String argsString) {
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

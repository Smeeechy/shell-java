import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for parsing shell arguments into a sequence of executable commands.
 */
public class CommandParser {
    /**
     * Helper used to break down a single string of arguments into a sequence of Command objects.
     *
     * @param commandString A string containing all command arguments
     * @return A list of commands representing an ordered pipeline
     */
    public static List<Command> parse(String commandString) {
        if (commandString == null || commandString.isEmpty()) return new ArrayList<>();

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
    private static List<Command> parsePipeline(List<String> parsedArgs) {
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
    private static Command parseCommand(List<String> parsed) {
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

                // TODO handle input redirects?

                default:
                    arguments.add(arg);
            }
        }

        return new Command(arguments, outRedirect, outAppend, errRedirect, errAppend, null);
    }
}

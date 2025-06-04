import org.jline.reader.LineReader;
import org.jline.reader.Widget;
import org.jline.terminal.Terminal;

import java.util.List;
import java.util.Set;

public class CompleteWordWidget implements Widget {
    private final Terminal terminal;
    private final LineReader lineReader;
    private final Set<String> commands;

    private int tabCount = 0;

    public CompleteWordWidget(Terminal terminal, LineReader lineReader, Set<String> commands) {
        this.terminal = terminal;
        this.lineReader = lineReader;
        this.commands = commands;
    }

    @Override
    public boolean apply() {
        // find prefix using current buffer and cursor position
        String buffer = lineReader.getBuffer().toString();
        int cursor = lineReader.getBuffer().cursor();
        int wordStart = cursor;
        while (wordStart > 0 && buffer.charAt(wordStart - 1) != ' ') wordStart--;
        String prefix = buffer.substring(wordStart, cursor);

        List<String> matches = commands.stream()
                .filter(command -> command.startsWith(prefix))
                .sorted()
                .toList();

        if (++tabCount == 1 && !matches.isEmpty()) {
            String shortestMatch = matches.getFirst();

            // autocomplete if only one match or all matches share common prefix
            boolean isCommonPrefix = matches.size() > 1 &&
                    matches.stream().allMatch(match -> match.startsWith(shortestMatch));
            if (matches.size() == 1 || isCommonPrefix) {
                lineReader.getBuffer().clear();
                lineReader.getBuffer().write(shortestMatch + (isCommonPrefix ? "" : " "));
                tabCount = 0;
                return true;
            }

            // otherwise just print bell character on first tab
            terminal.writer().print('\007');
            terminal.writer().flush();
            return true;
        }

        if (matches.isEmpty()) {
            terminal.writer().print('\007');
            terminal.writer().flush();
            tabCount = 0;
            return true;
        }

        // prints the matches on their own line, then the prompt
        terminal.writer().println();
        terminal.writer().println(String.join("  ", matches));
        terminal.writer().print("$ " + prefix);
        terminal.writer().flush();

        // updates the line buffer to only contain the prefix
        lineReader.getBuffer().clear();
        lineReader.getBuffer().write(prefix);

        tabCount = 0;
        return true;
    }

    public void resetTabCount() {
        tabCount = 0;
    }
}

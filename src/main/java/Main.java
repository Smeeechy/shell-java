import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) throws Exception {
        // disable warning logging because it breaks tests
        Logger.getLogger("org.jline").setLevel(Level.SEVERE);

        // enables keyboard inputs for searching history and autocompletion
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            // prevents jline from modifying input strings before passing them to the shell
            final DefaultParser parser = new DefaultParser();
            parser.setEscapeChars(new char[0]);

            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(parser)
                    // these options prevent default tab completion functionality
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option(LineReader.Option.AUTO_MENU, false)
                    .option(LineReader.Option.AUTO_LIST, false)
                    .build();

//            Set<String> commands = new HashSet<>();
            Set<String> commands = new HashSet<>(List.of("xyz", "xyz_foo", "xyz_foo_bar", "xyz_foo_bar_baz"));
            List<String> builtIns = Arrays.stream(BuiltIn.values())
                    .map(BuiltIn::toString)
                    .map(String::toLowerCase)
                    .toList();
            Set<String> externals = new PathScanner().getPathMap().keySet();
            commands.addAll(builtIns);
            commands.addAll(externals);
            CompleteWordWidget completeWordWidget = new CompleteWordWidget(terminal, lineReader, commands);

            // displays autocomplete options as required for tests
            lineReader.getWidgets().put(LineReader.COMPLETE_WORD, completeWordWidget);
            lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference(LineReader.COMPLETE_WORD), "\t");

            // resets tab count when user types anything but a tab
            lineReader.getWidgets().put(LineReader.SELF_INSERT, () -> {
                completeWordWidget.resetTabCount();
                return lineReader.getBuiltinWidgets().get(LineReader.SELF_INSERT).apply();
            });

            // main REPL loop
            final Shell shell = new Shell();
            while (true) {
                String input = lineReader.readLine("$ ");
                if (input.isBlank()) continue;
                shell.execute(input);
            }
        } catch (Exception ignored) {
        }
    }
}

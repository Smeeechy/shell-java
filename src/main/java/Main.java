import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.EnumCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    public static void main(String[] args) throws Exception {
        // disable warning logging. it breaks tests
        Logger.getLogger("org.jline").setLevel(Level.SEVERE);

        // prevents jline from modifying input strings before passing them to the shell
        final DefaultParser parser = new DefaultParser();
        parser.setEscapeChars(new char[0]);

        // enables builtin autocomplete
        final Completer builtInCompleter = new EnumCompleter(BuiltIn.class);

        // enables external autocomplete
        final Map<String, String> executables = new PathScanner().getPathMap();
        final Completer externalCompleter = new StringsCompleter(executables.keySet());

        // combines previous two
        final Completer completer = new AggregateCompleter(builtInCompleter, externalCompleter);

        // enables keyboard inputs for searching history and autocompletion
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            LineReader lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .parser(parser)
                    .completer(completer)
//                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
//                    .option(LineReader.Option.AUTO_MENU, false)
//                    .option(LineReader.Option.AUTO_LIST, false)
                    .build();

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

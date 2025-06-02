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
    private static final Shell SHELL = new Shell();
    private static final DefaultParser PARSER = new DefaultParser();

    public static void main(String[] args) throws Exception {
        Logger.getLogger("org.jline").setLevel(Level.SEVERE);
        PARSER.setEscapeChars(new char[0]);
        final Map<String, String> executables = new PathScanner().getPathMap();

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            Completer builtInCompleter = new EnumCompleter(BuiltIn.class);
            Completer externalCompleter = new StringsCompleter(executables.keySet());
            Completer completer = new AggregateCompleter(builtInCompleter, externalCompleter);
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).parser(PARSER).completer(completer).build();
            while (true) {
                String input = lineReader.readLine("$ ");
                if (input.isBlank()) continue;
                SHELL.execute(input);
            }
        } catch (Exception ignored) {
        }
    }
}

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static final Shell SHELL = new Shell();
    private static final DefaultParser PARSER = new DefaultParser();

    public static void main(String[] args) throws Exception {
        Logger.getLogger("org.jline").setLevel(Level.SEVERE);
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            PARSER.setEscapeChars(new char[0]);
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).parser(PARSER).build();
            while (true) {
                String input = lineReader.readLine("$ ");
                if (input.isBlank()) continue;
                SHELL.execute(input);
            }
        }
    }
}

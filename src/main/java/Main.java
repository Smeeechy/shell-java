import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class Main {
    private static final Shell SHELL = new Shell();

    public static void main(String[] args) throws Exception {
        try (Terminal terminal = TerminalBuilder.terminal()) {
            LineReader lineReader = LineReaderBuilder.builder().terminal(terminal).build();
            while (true) {
                String input = lineReader.readLine("$ ");
                if (input.isBlank()) continue;
                SHELL.execute(input);
            }
        }
    }
}

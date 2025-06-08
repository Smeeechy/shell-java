import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An object that executes shell commands and tracks relevant state. Able to execute builtins (<code>exit</code>,
 * <code>echo</code>, <code>type</code>, etc.) and external programs (<code>git</code>, <code>ls</code>,
 * <code>cat</code>, etc.).
 */
public class Shell {
    private Path cwd;
    private List<String> history;
    private String histFile;
    private int historyCursor;

    public Shell() {
        this.cwd = Path.of(System.getProperty("user.dir"));
        this.history = new ArrayList<>();
        this.histFile = System.getenv("HISTFILE");
        this.historyCursor = 0;

        if (histFile != null) readHistoryFromFile(histFile);
    }

    /**
     * General-purpose method for executing a shell command.
     *
     * @param commandString The raw input string containing commands, subcommands, and any relevant arguments
     */
    public void execute(String commandString) {
        history.add(commandString);
        final List<Command> commands = CommandParser.parse(commandString);
        try {
            executePipeline(commands);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    /**
     * Executes a series of commands, feeding the output of one command as input to the next.
     * Redirections override this behavior.
     *
     * @param commands A list of commands to be executed as a pipeline
     */
    private void executePipeline(List<Command> commands) throws IOException, InterruptedException {
        List<CommandRunner> runners = commands.stream()
                .map(command -> new CommandRunner(command, this))
                .toList();

        // chain pipeline streams together
        for (int i = 1; i < commands.size(); i++) {
            CommandRunner prev = runners.get(i - 1);
            CommandRunner current = runners.get(i);
            if (prev.getCommand().outRedirect() != null || current.getCommand().inRedirect() != null) continue;

            PipedInputStream pis = new PipedInputStream(4096);
            PipedOutputStream pos = new PipedOutputStream(pis);
            prev.setOutputStream(pos);
            current.setInputStream(pis);
        }

        // start all commands
        for (CommandRunner runner : runners) runner.start();

        // wait in reverse for each command to finish
        for (int i = runners.size() - 1; i >= 0; i--) runners.get(i).waitFor();
    }

    public void readHistoryFromFile(String fileName) {
        File file = new File(fileName);
        if (!file.exists() || !file.isFile() || !file.canRead()) return;

        List<String> newHistory = new ArrayList<>();
        if (!history.isEmpty()) {
            String lastCommand = history.getLast();
            newHistory.add(lastCommand);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) if (!line.isBlank()) newHistory.add(line);
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }

        history = newHistory;
        historyCursor = 0;
    }

    public void writeHistoryToFile(String fileName, boolean append) {
        File file = new File(fileName);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, append))) {
            List<String> historyDiff = history.subList(historyCursor, history.size());
            for (String line : historyDiff) writer.write(line + '\n');
            writer.flush();
            historyCursor = history.size();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Path getCwd() {
        return cwd;
    }

    public void setCwd(Path cwd) {
        this.cwd = cwd;
    }

    public List<String> getHistory() {
        return history;
    }

    public String getHistFile() {
        return histFile;
    }
}

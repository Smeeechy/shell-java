import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
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

    public Shell() {
        this.cwd = Path.of(System.getProperty("user.dir"));
        this.history = new ArrayList<>();
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
        for (CommandRunner runner : runners) {
            runner.start();
        }

        // wait in reverse for each command to finish
        for (int i = runners.size() - 1; i >= 0; i--) {
            runners.get(i).waitFor();
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

    public void setHistory(List<String> history) {
        this.history = history;
    }
}

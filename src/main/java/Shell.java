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

    public Shell() {
        this.cwd = Path.of(System.getProperty("user.dir"));
    }

    /**
     * General-purpose method for executing a shell command.
     *
     * @param commandString The raw input string containing commands, subcommands, and any relevant arguments
     * @param debug         Flag for printing commands in pipeline before execution
     */
    public void execute(String commandString) {
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
        // collect piped output streams for later EOF signaling
        List<PipedOutputStream> pipes = new ArrayList<>();

        // chain pipeline streams together
        for (int i = 1; i < commands.size(); i++) {
            CommandRunner prev = runners.get(i - 1);
            CommandRunner current = runners.get(i);
            if (prev.getCommand().outRedirect() != null || current.getCommand().inRedirect() != null) continue;
            PipedInputStream pis = new PipedInputStream();
            PipedOutputStream pos = new PipedOutputStream(pis);
            pipes.add(pos);
            prev.setOutputStream(pos);
            current.setInputStream(pis);
        }

        // start all commands
        for (CommandRunner runner : runners) {
            runner.start();
        }

        // signal EOF to downstream by closing all piped output streams
        for (PipedOutputStream pos : pipes) {
            try {
                pos.close();
            } catch (IOException ignored) {
            }
        }
        // now wait for the last command in the pipeline to finish
        runners.get(runners.size() - 1).waitFor();
    }

    Path getCwd() {
        return cwd;
    }

    void setCwd(Path cwd) {
        this.cwd = cwd;
    }
}

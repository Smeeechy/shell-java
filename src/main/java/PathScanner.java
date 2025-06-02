import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class used to crawl directories in the PATH and build a map of executables to their paths.
 */
public class PathScanner {
    private final Map<String, String> CMD_TO_EXECUTABLE_PATH = new HashMap<>();

    /**
     * Prepares a map of valid executables from the PATH environment variable.
     */
    public PathScanner() {
        String[] pathDirs = System.getenv("PATH").split(":");
        for (String dir : pathDirs) {
            final Path path = Path.of(dir);
            if (!Files.isDirectory(path)) continue;
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path, Files::isExecutable)) {
                for (Path file : directoryStream) {
                    if (Files.isExecutable(file))
                        CMD_TO_EXECUTABLE_PATH.putIfAbsent(file.getFileName().toString(), file.toString());
                }
            } catch (IOException ignored) {
                // TODO maybe handle this better
            }
        }
    }

    /**
     * @param command A string representing a potential shell command
     * @return the path to the executable, if it exists
     */
    public String findExecutablePath(String command) {
        return CMD_TO_EXECUTABLE_PATH.get(command);
    }

    public Map<String, String> getPathMap() {
        return CMD_TO_EXECUTABLE_PATH;
    }
}

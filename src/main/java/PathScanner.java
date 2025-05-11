import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PathScanner {
    private static final String[] PATH_DIRS = System.getenv("PATH").split(":");
    private static final Map<String, String> CMD_TO_EXECUTABLE_PATH = new HashMap<>();

    static {
        for (String dir : PATH_DIRS) {
            final Path path = Path.of(dir);
            if (!Files.isDirectory(path)) continue;
            try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(path, Files::isExecutable)) {
                for (Path file : directoryStream) {
                    if (Files.isExecutable(file))
                        CMD_TO_EXECUTABLE_PATH.putIfAbsent(file.getFileName().toString(), file.toString());
                }
            } catch (IOException ignored) {
            }
        }
    }

    public static String findExecutablePath(String command) {
        return CMD_TO_EXECUTABLE_PATH.get(command);
    }
}

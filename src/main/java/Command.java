import java.util.List;

/**
 * A utility class for holding relevant data related to a shell command.
 *
 * @param arguments   A list of strings representing individual commands, subcommands, and flags
 * @param outRedirect The location to redirect stdout, if any
 * @param outAppend   When true, will append to stdout, and will overwrite otherwise
 * @param errRedirect The location to redirect stderr, if any
 * @param errAppend   When true, will append to stderr, and will overwrite otherwise
 */
public record Command(List<String> arguments,
                      String outRedirect,
                      boolean outAppend,
                      String errRedirect,
                      boolean errAppend) {
}

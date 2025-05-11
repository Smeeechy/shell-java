import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("$ ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();

            if (input.isBlank()) continue;

            String[] tokens = input.split(" ", 2);
            BuiltIn command = BuiltIn.parse(tokens[0]);

            switch (command) {
                case EXIT:
                    System.exit(0);
                case ECHO:
                    System.out.println(tokens[1]);
                    continue;
                case TYPE:
                    String cmd = tokens[1].split(" ")[0];
                    System.out.println(BuiltIn.parse(cmd) == null ? cmd + ": not found" : cmd + " is a shell builtin");
                    continue;
                case null:
                default:
                    System.out.println(tokens[0] + ": command not found");
            }
        }
    }
}

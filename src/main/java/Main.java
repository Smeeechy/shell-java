import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("$ ");
            Scanner scanner = new Scanner(System.in);
            String input = scanner.nextLine();

            if (input.isBlank()) continue;

            String[] tokens = input.split(" ", 2);
            String command = tokens[0];

            switch (command) {
                case "exit":
                    System.exit(0);
                case "echo":
                    System.out.println(tokens[1]);
                    continue;
                default:
                    System.out.println(command + ": command not found");
            }
        }
    }
}

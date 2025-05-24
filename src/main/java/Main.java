import java.util.Scanner;

public class Main {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static final Shell SHELL = new Shell();

    public static void main(String[] args) throws Exception {
        while (true) {
            System.out.print("$ ");
            String input = SCANNER.nextLine();
            if (input.isBlank()) continue;
            SHELL.execute(input, false);
        }
    }
}

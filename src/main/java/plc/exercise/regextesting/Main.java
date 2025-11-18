package plc.exercise.regextesting;

import java.util.Scanner;
import java.util.regex.Pattern;

public final class Main {

    private static final Pattern CURRENT_REGEX = Regex.EMAIL; //edit for manual testing

    public static void main(String[] args) {
        var scanner = new Scanner(System.in);
        while (true) {
            var input = scanner.nextLine();
            var matcher = CURRENT_REGEX.matcher(input);
            System.out.println("Matches: " + matcher.matches());
            if (matcher.hasMatch() && matcher.groupCount() > 0) {
                for (var group : Regex.getGroups(matcher).entrySet()) {
                    System.out.println(" - Group " + group.getKey() + ": " + group.getValue());
                }
            }
        }
    }

}

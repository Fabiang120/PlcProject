package plc.exercise.regextesting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

public final class RegexFileFilterTests {

    @Test
    public void test() {
        //TODO: Uncomment to test class creation.
        var first = new RegexFileFilter(Pattern.compile("first"), true);
        var second = new RegexFileFilter(Pattern.compile("second"), false);
        Assertions.assertNotEquals(first, second);
        Assertions.assertNotEquals(first.hashCode(), second.hashCode());
        Assertions.assertNotEquals(first.toString(), second.toString());
    }
}

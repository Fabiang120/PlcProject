package plc.exercise.regextesting;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class RegexTests {

    @ParameterizedTest
    @MethodSource
    public void testEmail(String test, String input, boolean matches) {
        test(input, Regex.EMAIL, matches);
    }

    public static Stream<Arguments> testEmail() {
        Stream<Arguments> argumentsStream = Stream.of(
            Arguments.of("Alphanumeric", "thelegend27@gmail.com", true),
            Arguments.of("UF Domain", "otherdomain@ufl.edu", true),
            Arguments.of("Subdomain", "otherdomain@cise.ufl.edu", false),
            Arguments.of("Another Subdomain", "otherdomain@eng.ufl.edu", false),
            Arguments.of("Another Another Subdomain", "otherdomain@admin.ufl.edu", false),
            Arguments.of("Missing Domain Dot", "missingdot@gmailcom", false),
            Arguments.of("Symbols", "symbols#$%@gmail.com", false),
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)

            // Matching
            Arguments.of("Baseline", "normal@normal.com", true),
            Arguments.of("TwoLetterDomain", "normal@normal.co", true),
            Arguments.of("NumbersInDomain", "normal@normal123.com", true),
            Arguments.of("UnderscoreName", "normal_normal@normal.com", true),
            Arguments.of("DotInName", "normal.normal@normal.com", true),
            Arguments.of("DashInDomain", "normal@my-normal.com", true),
            Arguments.of("DigitsName", "normal123@normal.com", true),
            Arguments.of("EmptyDomain", "normal@.co", true),
            Arguments.of("Mixed", "normal.hello-123@normal.net", true),
            Arguments.of("TwoCharTld", "normal@normal.de", true),
            Arguments.of("MixedCaseName", "NoRMal@normal.com", true),
            Arguments.of("MixedCaseDomain", "normal@NoRMal.com", true),
            Arguments.of("SingleLetterDomain", "normal@n.com", true),
            Arguments.of("SingleCharName", "a@normal.com", true),
            Arguments.of("HyphenDomainAtEnd", "normal@normal-.com", true),
            Arguments.of("HyphenInName", "normal-normal@normal.com", true),
            Arguments.of("LeadingDomainHyphen", "normal@-normal.com", true),

            //Non Matching
            Arguments.of("DigitTld", "normal@normal.c0m", false),
            Arguments.of("HyphenTld", "normal@normal.c-m", false),
            Arguments.of("NoName", "@normal.com", false),
            Arguments.of("Missing@", "normalnormal.com", false),
            Arguments.of("MissingDomain", "normal@normal.", false),
            Arguments.of("SpaceInDomain", "normal@normal.c om", false),
            Arguments.of("OnlyDomain", "com", false),
            Arguments.of("UppercaseTld", "normal@normal.COM", false),
            Arguments.of("LongTld", "normal@normal.comm", false),
            Arguments.of("DoubleDot", "normal@normal..com", false),
            Arguments.of("InvalidChar", "normal+name@normal.com", false),
            Arguments.of("TrailingDot", "normal@normal.com.", false),
            Arguments.of("MixedCaseTLD", "normal@normal.cOm", false),
            Arguments.of("OneLetterTld", "normal@normal.c", false),
            Arguments.of("UnderscoreInTLD", "normal@normal.c_om", false),
            Arguments.of("UnderscoreInDomain", "normal@nor_mal.com", false),
            Arguments.of("DoubleAt", "normal@@normal.com", false),
            Arguments.of("InvalidPlusName", "normal+normal@normal.com", false),
            Arguments.of("InvalidHashName", "normal#normal@normal.com", false),
            Arguments.of("InvalidSpaceName", "normal normal@normal.com", false),
            Arguments.of("FourLetterTld", "normal@normal.comm", false),
            Arguments.of("FiveLetterTld", "normal@normal.commm", false),
            Arguments.of("DollarName", "normal$normal@normal.com", false),
            Arguments.of("PercentName", "normal%normal@normal.com", false),
            Arguments.of("ExclamationName", "normal!normal@normal.com", false),
            Arguments.of("AsteriskName", "normal*normal@normal.com", false),
            Arguments.of("SpecialCharInTLD", "normalnormal@normal.c!om", false),
            Arguments.of("QuestionInDomain", "normal@no?mal.com", false),
            Arguments.of("TildeInDomain", "normal@no~mal.com", false),
            Arguments.of("BackslashInDomain", "normal@no\\mal.com", false),
            Arguments.of("AmpersandName", "normal&normal@normal.com", false),
            Arguments.of("TrailingSpace", "normal@normal.com ", false),
            Arguments.of("SpaceAfterAt", "normal@ normal.com", false),
            Arguments.of("TabInName", "nor\tmal@normal.com", false),
            Arguments.of("NewlineInName", "nor\nmal@normal.com", false),
            Arguments.of("TabBeforeAt", "normal\t@normal.com", false),
            Arguments.of("TabAfterAt", "normal@\tnormal.com", false),
            Arguments.of("NewlineBeforeAt", "normal\n@normal.com", false),
            Arguments.of("NewlineAfterAt", "normal@\nnormal.com", false),
            Arguments.of("TabInDomain", "normal@nor\tmal.com", false),
            Arguments.of("NewlineInDomain", "normal@nor\nmal.com", false),
            Arguments.of("TabDot", "normal@normal\tcom", false),
            Arguments.of("NewlineDot", "normal@normal\ncom", false),
            Arguments.of("TabBeforeDot", "normal@normal\t.com", false),
            Arguments.of("NewlineBeforeDot", "normal@normal\n.com", false),
            Arguments.of("TabInTld", "normal@normal.c\tom", false),
            Arguments.of("NewlineInTld", "normal@normal.c\nom", false),
            Arguments.of("LeadingTab", "\tnormal@normal.com", false),
            Arguments.of("TrailingTab", "normal@normal.com\t", false),
            Arguments.of("LeadingNewline", "\nnormal@normal.com", false),
            Arguments.of("TrailingNewline", "normal@normal.com\n", false)
        );
        return argumentsStream;
    }

    @ParameterizedTest
    @MethodSource
    public void testSearchTerm(String test, String input, boolean matches) {
        test(input, Regex.SEARCH_TERM, matches);
    }

    public static Stream<Arguments> testSearchTerm() {
        return Stream.of(
            Arguments.of("Equal", "search", true),
            Arguments.of("Substring", "google search \"lmgtfy\"", true),
            Arguments.of("Spaces", "use arch", false),
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
            // Matching
            Arguments.of("SearchCombination", "research now", true),
            Arguments.of("Numbers", "search123", true),
            Arguments.of("SpecialChars", "!!search!!", true),

            Arguments.of("TabBefore", "\tsearch", true),
            Arguments.of("TabAfter", "search\t", true),
            Arguments.of("MultipleTabs", "\t\tsearch\t\t", true),
            Arguments.of("RSpecialBefore", "\rsearch", true),
            Arguments.of("RSpecialAfter", "search\r", true),
            Arguments.of("R&NSpecial", "\r\nsearch\r\n", true),
            Arguments.of("FSpecial", "\fsearch\f", true),
            Arguments.of("Math", "+-*/=search=/*-+", true),
            Arguments.of("Quotes", "'search'", true),
            Arguments.of("DoubleQuotes", "\"search\"", true),
            Arguments.of("Paranthesis", "(search)", true),
            Arguments.of("Braces", "{search}", true),
            Arguments.of("Tabs", "\tsearch\t", true),
            Arguments.of("NewlineBefore", "\nsearch", true),
            Arguments.of("NewlineAfter", "search\n", true),
            Arguments.of("InsideNewlines", "foo\nsearch\nbar", true),

            //Non Matching
            Arguments.of("Blank", "", false),
            Arguments.of("MixedCase", "SeArCh", false),
            Arguments.of("CaseInsensitive", "SEARCH", false),
            Arguments.of("WhiteSpace", " ", false),
            Arguments.of("ManySpaces", "s e a r c h", false),
            Arguments.of("MisSpelled", "siarch", false),
            Arguments.of("NoSearch", "hello I am fabian", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDiscountCsv(String test, String input, boolean matches) {
        test(input, Regex.DISCOUNT_CSV, matches);
    }

    public static Stream<Arguments> testDiscountCsv() {
        return Stream.of(
            Arguments.of("Single", "single", true),
            Arguments.of("Multiple", "one,two,three", true),
            Arguments.of("Spaces", "first , second", true),
            Arguments.of("Missing Value", "first,,second", false),
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
            // Matching
            Arguments.of("FourVals", "one,two,three,four", true),
            Arguments.of("LotsOfVals", "one,two,three,four,five,six,seven,eight", true),
            Arguments.of("ThreeValsWithSpaces", "a , b , c", true),
            Arguments.of("AlphaNumericVals", "val1,val2,val3", true),
            Arguments.of("TwoVals", "one,two", true),
            Arguments.of("SurroundedCommaSpaces", "a , b , c", true),
            Arguments.of("NewLine", "one,\ntwo", true),
            Arguments.of("TabCharacter", "one,\ttwo", true),
            // Non Matching
            Arguments.of("OnlyWhitespace", " ", false),
            Arguments.of("Blank", "", false),
            Arguments.of("ThreeCommas", "first,,,second", false),
            Arguments.of("Whitespace", "hello ", false),
            Arguments.of("UnneededComma", "hello,", false)

        );
    }

    @ParameterizedTest
    @MethodSource
    public void testDateNotation(String test, String input, boolean matches, Map<String, String> groups) {
        var matcher = Regex.DATE_NOTATION.matcher(input);
        Assertions.assertEquals(matches, matcher.matches());
        if (matches) {
            Assertions.assertEquals(groups, Regex.getGroups(matcher));
        }
    }

    public static Stream<Arguments> testDateNotation() {
        return Stream.of(
            Arguments.of("Month/Day", "8/25", true, Map.of(
                "month", "8", "day", "25"
            )),
            Arguments.of("Month/Day/Year", "8/25/2025", true, Map.of(
                "month", "8", "day", "25", "year", "2025"
            )),
            Arguments.of("Month Leading Zero", "08/25", false, Map.of()),
            Arguments.of("Missing Year", "8/25/", false, Map.of()),
            //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
            // Matching
            Arguments.of("Baseline", "3/3/4444", true, Map.of(
                "month", "3", "day", "3", "year", "4444"
            )),
            Arguments.of("NoYear", "3/25", true, Map.of(
                "month", "3", "day", "25"
            )),
            Arguments.of("Month&Day31", "3/31/4444", true, Map.of(
                "month", "3", "day", "31", "year", "4444"
            )),
            Arguments.of("Month12&Day", "12/3/4444", true, Map.of(
                "month", "12", "day", "3", "year", "4444"
            )),
            Arguments.of("BigYear", "3/3/9999", true, Map.of(
                "month", "3", "day", "3", "year", "9999"
            )),
            // Non Matching
            Arguments.of("Blank", "", false, Map.of()),
            Arguments.of("MonthWithBackslash", "3/", false, Map.of()),
            Arguments.of("Month", "3", false, Map.of()),
            Arguments.of("ThreeDigitMonth", "333/25", false, Map.of()),
            Arguments.of("ThreeDigitDay", "3/253", false, Map.of())

        );
    }

    @ParameterizedTest
    @MethodSource
    public void testNumber(String test, String input, boolean matches) {
        test(input, Regex.NUMBER, matches);
    }

    public static Stream<Arguments> testNumber() {
        //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
        return Stream.of(
            //Matching
            Arguments.of("Baseline", "1", true),
            Arguments.of("NegativeDecimal", "-1.000", true),
            Arguments.of("ExponentPositive", "1e5", true),
            Arguments.of("DecimalExponent", "1.00e-2", true),
            Arguments.of("LeadingZeros", "0001", true),

            //Non Matching
            Arguments.of("MissingExponentDigits", "10e", false),
            Arguments.of("Letters", "1a3", false),
            Arguments.of("MissingIntegerPart", ".1", false),
            Arguments.of("Empty", "", false),
            Arguments.of("Symbols", "+-/!", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testString(String test, String input, boolean matches) {
        test(input, Regex.STRING, matches);
    }

    public static Stream<Arguments> testString() {
        //TODO: Test coverage (*minimum* 5 matching, 5 non-matching)
        return Stream.of(
            //Matching
            Arguments.of("Baseline", "\"\"", true),
            Arguments.of("EscapedQuote", "\"hello\\\"hello\\\"\"", true),
            Arguments.of("EscapedBackslash", "\"hellooo\\\\hello\"", true),
            Arguments.of("Text", "\"hello\"", true),
            Arguments.of("EscapedNewline", "\"hello\\n\"", true),
            // Non matching
            Arguments.of("NoQuotes", "hello", false),
            Arguments.of("BadEscape", "\"hi\\hello\"", false),
            Arguments.of("UnescapedQuote", "\"hel\"lo\"", false),
            Arguments.of("NoTwoQuotes", "\"hello", false),
            Arguments.of("Newline", "\"hello\nhello\"", false)
        );
    }

    private static void test(String input, Pattern pattern, boolean matches) {
        Assertions.assertEquals(matches, pattern.matcher(input).matches());
    }

}

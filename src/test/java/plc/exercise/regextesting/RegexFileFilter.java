package plc.exercise.regextesting;

import java.io.File;
import java.util.regex.Pattern;

public final class RegexFileFilter implements java.io.FileFilter {

    private final Pattern regex;
    private final boolean filename;

    public RegexFileFilter(Pattern regex, boolean filename) {
        this.regex = java.util.Objects.requireNonNull(regex);
        this.filename = filename;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        RegexFileFilter other = (RegexFileFilter) obj;
        return filename == other.filename
            && regex.pattern().equals(other.regex.pattern());
    }

    @Override
    public String toString() {
        return "Regex is " + regex.pattern() + ". Only applied to filename: " + filename;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(regex.pattern(), filename);
    }

    @Override
    public boolean accept(File file) {
        if (file == null || !file.isFile()) return false;
        return filename
            ? regex.matcher(file.getName()).matches()
            : regex.matcher(file.getPath()).matches();
    }


    public static File[] listJavaFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return new File[0];
        RegexFileFilter filter = new RegexFileFilter(Pattern.compile(".*\\.java$"), true);
        File[] files = dir.listFiles(filter);
        return files != null ? files : new File[0];
    }
}

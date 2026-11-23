package mcphone1165fixture;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Collects fixture assertions, echoes them to the server console and writes one report file. */
final class Report {

    private final List<String> lines = new ArrayList<String>();
    private final File file;
    private int pass;
    private int fail;
    private int skip;
    private boolean flushed;

    Report(File file) {
        this.file = file;
    }

    void header(String title) {
        emit("");
        emit("==== " + title + " ====");
    }

    void section(String title) {
        emit("");
        emit("-- " + title);
    }

    void info(String id, String detail) {
        emit("INFO  " + id + " | " + detail);
    }

    void pass(String id, String detail) {
        pass++;
        emit("PASS  " + id + " | " + detail);
    }

    void fail(String id, String detail) {
        fail++;
        emit("FAIL  " + id + " | " + detail);
    }

    void skip(String id, String detail) {
        skip++;
        emit("SKIP  " + id + " | " + detail);
    }

    void threw(String id, Throwable t) {
        t.printStackTrace();
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        fail++;
        emit("FAIL  " + id + " | threw " + describe(t));
    }

    static String describe(Throwable t) {
        if (t == null) return "null";
        StringWriter text = new StringWriter();
        PrintWriter printer = new PrintWriter(text);
        t.printStackTrace(printer);
        printer.flush();
        String[] rows = text.toString().split("\\R");
        StringBuilder out = new StringBuilder();
        int frames = 0;
        for (String row : rows) {
            if (out.length() > 0) out.append(" <- ");
            out.append(row.trim());
            if (row.startsWith("\tat ")) {
                frames++;
                if (frames >= 6) break;
            }
        }
        return out.toString();
    }

    private void emit(String line) {
        lines.add(line);
        System.out.println("[mcphone1165fixture] " + line);
    }

    void flush() {
        if (flushed) return;
        flushed = true;

        emit("");
        emit("FIXTURE_SUMMARY pass=" + pass + " fail=" + fail + " skip=" + skip);
        emit("FIXTURE_RESULT " + (fail == 0 ? "PASS" : "FAIL"));

        if (file == null) return;
        PrintWriter out = null;
        try {
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            out = new PrintWriter(new BufferedWriter(new FileWriter(file, false)));
            out.println("# MCphone 1.16.5 / Forge 36 phone-slot fixture, TEST ONLY");
            out.println("# " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            for (String line : lines) out.println(line);
        } catch (Throwable t) {
            System.out.println("[mcphone1165fixture] WARN cannot write " + file + ": " + t);
        } finally {
            if (out != null) out.close();
        }
    }
}

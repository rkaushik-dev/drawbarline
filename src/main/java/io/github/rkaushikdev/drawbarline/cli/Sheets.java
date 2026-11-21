package io.github.rkaushikdev.drawbarline.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Comma separated text split into records and fields.
 *
 * <p>The sheets a clerk exports from a spreadsheet: fields parted by commas, a field in double quotes
 * when it holds a comma, a line break or a quote, and a quote inside one written twice. Lines may
 * end in a line feed, a carriage return or both. A quote that opens a field and closes before its
 * end leaves the rest of the field as it was written, so {@code "4312"a} reads {@code 4312a}. A
 * blank line is a record with no fields, which whoever reads the records skips.
 */
final class Sheets {

    private enum State {
        START_RECORD,
        START_FIELD,
        IN_FIELD,
        IN_QUOTED_FIELD,
        QUOTE_IN_QUOTED_FIELD,
        EAT_LINE_END
    }

    private final List<List<String>> records = new ArrayList<>();
    private List<String> fields = new ArrayList<>();
    private final StringBuilder field = new StringBuilder();
    private State state = State.START_RECORD;

    private Sheets() {}

    /** Every record in the text, in order. */
    static List<List<String>> records(String text) {
        Sheets reader = new Sheets();
        for (String line : lines(text)) {
            for (int at = 0; at < line.length(); at++) {
                reader.take(line.charAt(at));
            }
            reader.endOfLine();
        }
        reader.endOfText();
        return List.copyOf(reader.records);
    }

    /** The text cut into lines, each keeping what ended it. */
    private static List<String> lines(String text) {
        List<String> found = new ArrayList<>();
        int start = 0;
        for (int at = 0; at < text.length(); at++) {
            char one = text.charAt(at);
            if (one == '\n' || (one == '\r' && (at + 1 >= text.length() || text.charAt(at + 1) != '\n'))) {
                found.add(text.substring(start, at + 1));
                start = at + 1;
            }
        }
        if (start < text.length()) {
            found.add(text.substring(start));
        }
        return found;
    }

    private static boolean isLineEnd(char one) {
        return one == '\n' || one == '\r';
    }

    private void take(char one) {
        switch (state) {
            case START_RECORD -> {
                if (isLineEnd(one)) {
                    state = State.EAT_LINE_END;
                } else {
                    state = State.START_FIELD;
                    take(one);
                }
            }
            case START_FIELD -> {
                if (isLineEnd(one)) {
                    saveField();
                    state = State.EAT_LINE_END;
                } else if (one == '"') {
                    state = State.IN_QUOTED_FIELD;
                } else if (one == ',') {
                    saveField();
                } else {
                    field.append(one);
                    state = State.IN_FIELD;
                }
            }
            case IN_FIELD -> {
                if (isLineEnd(one)) {
                    saveField();
                    state = State.EAT_LINE_END;
                } else if (one == ',') {
                    saveField();
                    state = State.START_FIELD;
                } else {
                    field.append(one);
                }
            }
            case IN_QUOTED_FIELD -> {
                if (one == '"') {
                    state = State.QUOTE_IN_QUOTED_FIELD;
                } else {
                    field.append(one);
                }
            }
            case QUOTE_IN_QUOTED_FIELD -> {
                if (one == '"') {
                    field.append(one);
                    state = State.IN_QUOTED_FIELD;
                } else if (one == ',') {
                    saveField();
                    state = State.START_FIELD;
                } else if (isLineEnd(one)) {
                    saveField();
                    state = State.EAT_LINE_END;
                } else {
                    field.append(one);
                    state = State.IN_FIELD;
                }
            }
            case EAT_LINE_END -> {
                // A carriage return and a line feed end one line together.
            }
        }
    }

    private void endOfLine() {
        switch (state) {
            case START_RECORD -> saveRecord();
            case START_FIELD, IN_FIELD, QUOTE_IN_QUOTED_FIELD -> {
                saveField();
                saveRecord();
            }
            case EAT_LINE_END -> saveRecord();
            case IN_QUOTED_FIELD -> {
                // A quoted field runs on into the next line.
            }
        }
    }

    private void endOfText() {
        if (state == State.IN_QUOTED_FIELD || !field.isEmpty()) {
            saveField();
            saveRecord();
        }
    }

    private void saveField() {
        fields.add(field.toString());
        field.setLength(0);
    }

    private void saveRecord() {
        records.add(List.copyOf(fields));
        fields = new ArrayList<>();
        state = State.START_RECORD;
    }
}

package io.github.rkaushikdev.drawbarline.interchange;

/**
 * One line of an interchange sheet that would not read, and why.
 *
 * @param line the line number in the file, counting from one
 * @param text the line as it arrived
 * @param why the refusal, with the values that caused it
 */
public record Problem(int line, String text, String why) {

    /** {@code line 3: no such kind code (code='ZZ')}. */
    @Override
    public String toString() {
        return "line " + line + ": " + why;
    }
}

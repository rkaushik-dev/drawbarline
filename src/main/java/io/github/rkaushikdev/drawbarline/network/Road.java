package io.github.rkaushikdev.drawbarline.network;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.util.Objects;

/** A railroad, ours or somebody else's, named by its reporting mark. */
public record Road(ReportingMark mark, String name, boolean home) {

    public Road {
        Objects.requireNonNull(mark, "mark");
        if (Text.isBlank(name)) {
            throw new ConfigurationError("a road needs a name", Details.of("mark", mark.toString()));
        }
        if (mark.isPrivate()) {
            throw new ConfigurationError("a road does not carry a private mark", Details.of("mark", mark.toString()));
        }
    }

    /** A foreign road. */
    public Road(ReportingMark mark, String name) {
        this(mark, name, false);
    }

    /** {@code CN Canadian National}. */
    @Override
    public String toString() {
        return mark + " " + name;
    }
}

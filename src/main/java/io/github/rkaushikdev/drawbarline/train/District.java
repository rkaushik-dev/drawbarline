package io.github.rkaushikdev.drawbarline.train;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import java.util.Objects;

/** What a stretch of railroad will take: the tonnage rating and the longest train its sidings hold. */
public record District(String line, String name, int maxTons, Length maxLength) {

    public District {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(maxLength, "maxLength");
        if (!Mileage.MAIN.equals(line) && !Mileage.BRANCH.equals(line)) {
            throw new ConsistError("this road has a main and a branch", Details.of("line", line));
        }
        if (maxTons < 1) {
            throw new ConsistError("a district takes at least a ton", Details.of("name", name));
        }
    }
}

package io.github.rkaushikdev.drawbarline.settlement;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A month opened again after it was closed, and why.
 *
 * <p>Reopening a month after a statement has gone out means the statement was wrong, and somebody
 * has to be able to find out why later, so a reopening without a reason is refused.
 */
public record Reopening(Period period, LocalDate on, String reason) {

    public Reopening {
        Objects.requireNonNull(period, "period");
        Objects.requireNonNull(on, "on");
        if (Text.isBlank(reason)) {
            throw new SettlementError("reopening a month needs a reason", Details.of("period", period.toString()));
        }
    }
}

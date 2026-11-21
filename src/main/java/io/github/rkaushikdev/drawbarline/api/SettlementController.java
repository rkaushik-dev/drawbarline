package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.SettlementError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.service.Service;
import io.github.rkaushikdev.drawbarline.settlement.CloseResult;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import io.github.rkaushikdev.drawbarline.settlement.Posting;
import io.github.rkaushikdev.drawbarline.settlement.Statement;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Settlement routes.
 *
 * <p>The close is a post rather than a get because it changes a great deal and because running it
 * twice has to be refused. Everything else in here reads.
 *
 * <p>A month arrives in the path as {@code 2025-01}. That is what everybody writes on paper and it
 * is unambiguous, unlike a date, which invites somebody to close the fourteenth and then ask why
 * the answer is odd.
 */
@RestController
@Tag(name = "settlement")
final class SettlementController {

    /** A year of four digits and a month of one or two. */
    private static final Pattern MONTH = Pattern.compile("(\\d{4})-(1[0-2]|0[1-9]|[1-9])");

    private final Work work;

    SettlementController(Work work) {
        this.work = work;
    }

    /** A month as it is written in a path, or a refusal saying how to write one. */
    static Period aMonth(String text) {
        Matcher found = MONTH.matcher(Text.strip(text));
        if (!found.matches()) {
            throw new SettlementError("a month reads as 2025-01", Details.of("given", text));
        }
        return new Period(Integer.parseInt(found.group(1)), Integer.parseInt(found.group(2)));
    }

    /** What a close needs beyond the month itself. */
    record CloseIn(@NotNull LocalDate issued) {}

    /** Opening a closed month again, and why. */
    record ReopenIn(@NotNull LocalDate on, @NotNull String reason) {}

    /** What a close did, in cents. */
    record CloseOut(
            String month,
            long carHire,
            long reclaimed,
            long demurrage,
            int postings,
            List<String> statements,
            List<String> invoices) {}

    /** A month and whether it is open to posting. */
    record PeriodOut(String month, boolean open) {}

    /** One kind of posting on a statement, in cents. */
    record StatementLineOut(String kind, long amount) {}

    /** A statement as it goes to the other road, in cents. */
    record StatementOut(
            int number,
            String road,
            String month,
            long opening,
            List<StatementLineOut> lines,
            long closing,
            String direction) {

        static StatementOut of(Statement found) {
            return new StatementOut(
                    found.number(),
                    found.road().toString(),
                    found.period().label(),
                    found.opening().cents(),
                    found.lines().stream()
                            .map(one -> new StatementLineOut(
                                    one.kind().text(), one.amount().cents()))
                            .toList(),
                    found.closing().cents(),
                    found.direction());
        }
    }

    /** Every month that has been closed. */
    @GetMapping("/periods")
    List<PeriodOut> periods() {
        return work.read(books -> books.periods().closedPeriods().stream()
                .map(one -> new PeriodOut(one.label(), false))
                .toList());
    }

    /** Work a month through and shut it. */
    @PostMapping("/periods/{month}/close")
    @ResponseStatus(HttpStatus.CREATED)
    CloseOut close(@PathVariable("month") String month, @Valid @RequestBody CloseIn filing) {
        return work.apply(books -> {
            Period period = aMonth(month);
            if (filing.issued().isBefore(period.lastDay())) {
                throw new SettlementError("a month is closed after it ends", Details.of("month", period.label()));
            }
            CloseResult result = Service.runClose(books, period, filing.issued());
            return new CloseOut(
                    result.period().label(),
                    result.carHire().cents(),
                    result.reclaimed().cents(),
                    result.demurrage().cents(),
                    result.posted(),
                    result.statements().stream().map(Statement::toString).toList(),
                    result.invoices().stream().map(Object::toString).toList());
        });
    }

    /** Open a closed month again, with the reason on file. */
    @PostMapping("/periods/{month}/reopen")
    PeriodOut reopen(@PathVariable("month") String month, @Valid @RequestBody ReopenIn filing) {
        return work.apply(books -> {
            Period period = aMonth(month);
            books.periods().reopen(period, filing.on(), filing.reason());
            return new PeriodOut(period.label(), true);
        });
    }

    /** The statement for one road and one month. */
    @GetMapping("/statements/{road}/{month}")
    StatementOut statement(
            @PathVariable("road") String road,
            @PathVariable("month") String month,
            @RequestParam(name = "number", defaultValue = "1") int number) {
        return work.read(books -> {
            Period period = aMonth(month);
            return StatementOut.of(Statement.cut(
                    books.ledger().ledger(), ReportingMark.parse(road), period, number, period.lastDay()));
        });
    }

    /** Every posting against one road, oldest first. */
    @GetMapping("/ledger/{road}")
    List<Map<String, Object>> account(@PathVariable("road") String road) {
        return work.read(books -> books.ledger().postings(ReportingMark.parse(road)).stream()
                .map(SettlementController::lineOf)
                .toList());
    }

    private static Map<String, Object> lineOf(Posting one) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("month", one.period().label());
        line.put("kind", one.kind().text());
        line.put("amount", one.amount().cents());
        line.put("on", one.on().toString());
        line.put("reference", one.reference());
        return line;
    }
}

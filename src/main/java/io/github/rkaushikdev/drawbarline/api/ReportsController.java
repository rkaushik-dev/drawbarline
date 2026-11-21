package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.reporting.Reporting;
import io.github.rkaushikdev.drawbarline.reports.Crossing;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Report routes.
 *
 * <p>Every report already knows how to lay itself out for a printer, so each of these serves the
 * same lines the command line prints, as an array of strings. That is deliberately unfashionable.
 * The office reads these in a terminal and pastes them into mail, and a client that wants the
 * numbers rather than the page has the data routes beside them.
 *
 * <p>Nothing is computed here. The reporting layer does that and the command line calls the same
 * methods, so the page and the terminal cannot drift apart.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "reports")
final class ReportsController {

    private final Work work;

    ReportsController(Work work) {
        this.work = work;
    }

    /** Car hire liability, by road or car by car. */
    @GetMapping("/carhire/{month}")
    List<String> carhire(
            @PathVariable("month") String month, @RequestParam(name = "road", required = false) String road) {
        return work.read(books -> road == null
                ? Reporting.carHire(books, SettlementController.aMonth(month))
                : Reporting.carHire(books, SettlementController.aMonth(month), road));
    }

    /** The same run as data, in cents a road. */
    @GetMapping("/carhire/{month}/totals")
    Map<String, Long> carhireTotals(@PathVariable("month") String month) {
        return work.read(books -> {
            Map<String, Long> totals = new LinkedHashMap<>();
            Reporting.carHireTotals(books, SettlementController.aMonth(month))
                    .forEach((road, amount) -> totals.put(road, amount.cents()));
            return totals;
        });
    }

    /** Detention, car by car or customer by customer. */
    @GetMapping("/demurrage/{month}")
    List<String> demurrage(
            @PathVariable("month") String month,
            @RequestParam(name = "by_shipper", defaultValue = "false") boolean byShipper) {
        return work.read(books -> Reporting.demurrage(books, SettlementController.aMonth(month), byShipper));
    }

    /** Turn time against standing time, car by car. */
    @GetMapping("/utilisation/{month}")
    List<String> utilisation(@PathVariable("month") String month) {
        return work.read(books -> Reporting.utilisation(books, SettlementController.aMonth(month)));
    }

    /** What crossed each junction, both ways. */
    @GetMapping("/interchange/{month}")
    List<String> interchange(@PathVariable("month") String month) {
        return work.read(books -> Reporting.interchange(books, SettlementController.aMonth(month)));
    }

    /** The same counts as data, one row a junction and road. */
    @GetMapping("/interchange/{month}/crossings")
    List<Map<String, Object>> interchangeCrossings(@PathVariable("month") String month) {
        return work.read(books -> Reporting.interchangeCrossings(books, SettlementController.aMonth(month)).stream()
                .map(ReportsController::rowOf)
                .toList());
    }

    /** Every car we have taken and not handed back. */
    @GetMapping("/onhand")
    List<String> onhand(@RequestParam(name = "at", required = false) Optional<String> at) {
        return work.read(books -> Reporting.onHand(books, Moments.orNow(Moments.query("at", at))));
    }

    /** How old the fleet is and what leaves interchange service soon. */
    @GetMapping("/age")
    List<String> age(@RequestParam(name = "on", required = false) LocalDate on) {
        return work.read(books -> Reporting.fleetAge(books, on == null ? Clock.today() : on));
    }

    /** The age profile as data, cars in each decade. */
    @GetMapping("/age/profile")
    Map<String, Integer> ageData(@RequestParam(name = "on", required = false) LocalDate on) {
        return work.read(books -> Reporting.fleetAgeProfile(books, on == null ? Clock.today() : on));
    }

    /** Repair billing for a month, or what is still under exception. */
    @GetMapping("/repairs/{month}")
    List<String> repairs(
            @PathVariable("month") String month,
            @RequestParam(name = "exceptions", defaultValue = "false") boolean exceptions) {
        return work.read(books -> Reporting.repairs(books, SettlementController.aMonth(month), exceptions));
    }

    /** Which cars are bad ordered and how long they have been down. */
    @GetMapping("/shop")
    List<String> shop(@RequestParam(name = "at", required = false) Optional<String> at) {
        return work.read(books -> Reporting.shop(books, Moments.orNow(Moments.query("at", at))));
    }

    /** What the cars standing here cost a day if nothing moves. */
    @GetMapping("/forecast")
    List<String> forecast(
            @RequestParam(name = "at", required = false) Optional<String> at,
            @RequestParam(name = "days", defaultValue = "7") int days) {
        return work.read(books -> Reporting.forecast(books, Moments.orNow(Moments.query("at", at)), days));
    }

    /** The trial balance, which proves the other reports. */
    @GetMapping("/balance/{month}")
    List<String> balance(
            @PathVariable("month") String month,
            @RequestParam(name = "by_kind", defaultValue = "false") boolean byKind) {
        return work.read(books -> Reporting.trialBalance(books, SettlementController.aMonth(month), byKind));
    }

    /** The year on one page. */
    @GetMapping("/annual/{year}")
    List<String> annual(@PathVariable("year") int year, @RequestParam(name = "road", required = false) String road) {
        return work.read(books -> road == null ? Reporting.annual(books, year) : Reporting.annual(books, year, road));
    }

    /** Everything that goes out after a close, in one document. */
    @GetMapping("/package/{month}")
    List<String> closePackage(@PathVariable("month") String month) {
        return work.read(books -> Reporting.closePackage(books, SettlementController.aMonth(month)));
    }

    /** What is in the package, for anybody checking one arrived whole. Any month has the same. */
    @GetMapping("/package/{month}/sections")
    List<String> packageSections(@PathVariable("month") String month) {
        return Reporting.packageSections();
    }

    private static Map<String, Object> rowOf(Crossing one) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("junction", one.junction());
        row.put("road", one.road().toString());
        row.put("received", one.received());
        row.put("delivered", one.delivered());
        row.put("net", one.net());
        return row;
    }
}

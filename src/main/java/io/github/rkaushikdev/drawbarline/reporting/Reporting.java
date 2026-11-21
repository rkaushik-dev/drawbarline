package io.github.rkaushikdev.drawbarline.reporting;

import io.github.rkaushikdev.drawbarline.carhire.Liability;
import io.github.rkaushikdev.drawbarline.demurrage.Detention;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.distribution.Empty;
import io.github.rkaushikdev.drawbarline.events.EventLog;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.reference.Reference;
import io.github.rkaushikdev.drawbarline.repairs.LaborRates;
import io.github.rkaushikdev.drawbarline.repairs.PriceMaster;
import io.github.rkaushikdev.drawbarline.repairs.PricedCard;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.reports.AnnualReport;
import io.github.rkaushikdev.drawbarline.reports.CarHireReport;
import io.github.rkaushikdev.drawbarline.reports.Crossing;
import io.github.rkaushikdev.drawbarline.reports.DailyReport;
import io.github.rkaushikdev.drawbarline.reports.DemurrageReport;
import io.github.rkaushikdev.drawbarline.reports.FleetReport;
import io.github.rkaushikdev.drawbarline.reports.ForecastReport;
import io.github.rkaushikdev.drawbarline.reports.InterchangeReport;
import io.github.rkaushikdev.drawbarline.reports.LedgerReport;
import io.github.rkaushikdev.drawbarline.reports.RepairsReport;
import io.github.rkaushikdev.drawbarline.reports.ShopReport;
import io.github.rkaushikdev.drawbarline.reports.StandingCar;
import io.github.rkaushikdev.drawbarline.service.Service;
import io.github.rkaushikdev.drawbarline.settlement.Books;
import io.github.rkaushikdev.drawbarline.settlement.Close;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Running a report against the books.
 *
 * <p>The command line and the HTTP interface both wanted the same reports, and both were doing the
 * same three things to get each one: load the registers, run the computation, lay it out. Two
 * copies of that is two places to forget that the demurrage report needs the tariff in force at the
 * end of the month rather than at the start.
 *
 * <p>So it lives here once, beside the service layer and for the same reason. The layout functions
 * in the reports package still know nothing about a database and the repositories still know
 * nothing about a page. Nothing here writes: a report opens no transaction it commits.
 */
public final class Reporting {

    /** What separates two sections of the close package. */
    static final String RULE = "-".repeat(60);

    /** The sections of the close package, in the order they go out. */
    private static final List<Section> CLOSE_PACKAGE = List.of(
            new Section("trial balance", Reporting::trialBalance),
            new Section("car hire", Reporting::carHire),
            new Section("demurrage", (work, period) -> demurrage(work, period, true)),
            new Section("repairs", Reporting::repairs),
            new Section("interchange", Reporting::interchange),
            new Section("utilisation", Reporting::utilisation));

    private Reporting() {}

    /** One section of the close package and how it is run. */
    private record Section(String name, BiFunction<UnitOfWork, Period, List<String>> run) {}

    private static Liability owed(UnitOfWork work, Period period) {
        Books books = Service.booksOf(work);
        return Liability.run(books.fleet(), books.log(), books.rates(), period.window(), Reference.DRAWBAR_LINE.mark());
    }

    /** Car hire liability for a month, road by road. */
    public static List<String> carHire(UnitOfWork work, Period period) {
        return CarHireReport.liabilityReport(owed(work, period), period);
    }

    /** Car hire for a month car by car for one road, or road by road when no road is named. */
    public static List<String> carHire(UnitOfWork work, Period period, String road) {
        if (road.isEmpty()) {
            return carHire(work, period);
        }
        return CarHireReport.detailReport(owed(work, period), ReportingMark.parse(road), period);
    }

    /** The same run as data, a road at a time. */
    public static Map<String, Money> carHireTotals(UnitOfWork work, Period period) {
        return CarHireReport.owedTo(owed(work, period));
    }

    /** Detention for a month, car by car. */
    public static List<String> demurrage(UnitOfWork work, Period period) {
        return demurrage(work, period, false);
    }

    /** Detention for a month, car by car or customer by customer, priced on the tariff in force at its end. */
    public static List<String> demurrage(UnitOfWork work, Period period, boolean byShipper) {
        Books books = Service.booksOf(work);
        List<Detention> found = Close.detentionsIn(books, period);
        Tariff tariff = books.tariffs().inEffectOn(period.lastDay());
        if (byShipper) {
            return DemurrageReport.shipperReport(found, tariff, books.customers(), period.label());
        }
        return DemurrageReport.detentionReport(found, tariff, period.label());
    }

    /** Turn time against standing time, car by car. */
    public static List<String> utilisation(UnitOfWork work, Period period) {
        return FleetReport.utilisationReport(work.events().log(), period.window(), period.label());
    }

    /** What crossed each junction, both ways. */
    public static List<String> interchange(UnitOfWork work, Period period) {
        return InterchangeReport.balanceReport(work.events().log(), period.window(), period.label());
    }

    /** The same counts as data. */
    public static List<Crossing> interchangeCrossings(UnitOfWork work, Period period) {
        return InterchangeReport.crossings(work.events().log(), period.window());
    }

    /** Every car we have taken and not handed back. */
    public static List<String> onHand(UnitOfWork work, Instant at) {
        return InterchangeReport.onHandReport(work.events().log(), at);
    }

    /** How old the fleet is and what leaves interchange service soon. */
    public static List<String> fleetAge(UnitOfWork work, LocalDate on) {
        return FleetReport.ageReport(work.fleet().register(), on);
    }

    /** The age profile as data. */
    public static Map<String, Integer> fleetAgeProfile(UnitOfWork work, LocalDate on) {
        return FleetReport.ageProfile(work.fleet().register(), on);
    }

    /** Repair billing for a month. */
    public static List<String> repairs(UnitOfWork work, Period period) {
        return repairs(work, period, false);
    }

    /** Repair billing for a month, or what is still under exception at its end. */
    public static List<String> repairs(UnitOfWork work, Period period, boolean exceptions) {
        PriceMaster master = work.repairPricing().master();
        LaborRates rates = work.repairPricing().laborRates();
        List<PricedCard> priced = new ArrayList<>();
        for (RepairCard card : work.cards().between(period.firstDay(), period.lastDay())) {
            priced.add(PricedCard.price(card, master, rates));
        }
        if (exceptions) {
            Map<Integer, PricedCard> byNumber = new LinkedHashMap<>();
            for (PricedCard one : priced) {
                byNumber.put(one.card().number(), one);
            }
            return RepairsReport.exceptionReport(work.cards().openObjections(), byNumber, period.lastDay());
        }
        return RepairsReport.billingReport(priced, period.label());
    }

    /** What is in the shop and how long it has been there. */
    public static List<String> shop(UnitOfWork work, Instant at) {
        return ShopReport.shopReport(work.events().log(), at);
    }

    /** The morning sheet, which is every other report boiled down to one page. */
    public static List<String> daily(UnitOfWork work, LocalDate day, Instant at) {
        EventLog log = work.events().log();
        List<CarOrder> orders = work.orders().openOn(day);
        List<Empty> empties = Distribution.emptiesOnHand(work.fleet().register(), log, at);
        Distribution found =
                Distribution.distribute(orders, empties, work.orders().stations());
        return DailyReport.dailySummary(log, work.embargoes().register(), found, day, at);
    }

    /** What the cars standing here cost a day, and over the usual run of days. */
    public static List<String> forecast(UnitOfWork work, Instant at) {
        return forecast(work, at, ForecastReport.DAYS);
    }

    /** What the cars standing here cost a day, and over a run of days. */
    public static List<String> forecast(UnitOfWork work, Instant at, int days) {
        List<StandingCar> cars = ForecastReport.standingCars(
                work.fleet().register(), work.events().log(), work.rates().table(), at, Reference.DRAWBAR_LINE.mark());
        return ForecastReport.forecastReport(cars, at, days);
    }

    /** The trial balance for a month. */
    public static List<String> trialBalance(UnitOfWork work, Period period) {
        return trialBalance(work, period, false);
    }

    /** The trial balance for a month, or the same month split by kind. */
    public static List<String> trialBalance(UnitOfWork work, Period period, boolean byKind) {
        if (byKind) {
            return LedgerReport.byKindReport(work.ledger().ledger(), period);
        }
        return LedgerReport.trialBalanceReport(work.ledger().ledger(), period);
    }

    /** The year on one page, for the whole pool. */
    public static List<String> annual(UnitOfWork work, int year) {
        return AnnualReport.annualReport(work.ledger().ledger(), year);
    }

    /** The year on one page for one road, or for the whole pool when no road is named. */
    public static List<String> annual(UnitOfWork work, int year, String road) {
        if (road.isEmpty()) {
            return annual(work, year);
        }
        return AnnualReport.annualReport(work.ledger().ledger(), year, ReportingMark.parse(road));
    }

    /**
     * Everything that goes out after a close, in one document.
     *
     * <p>The office used to run six commands and staple the output together, in a different order
     * each month. The order is fixed here and the trial balance is first, because a month that does
     * not balance is a month nobody should read the rest of.
     */
    public static List<String> closePackage(UnitOfWork work, Period period) {
        List<String> lines = new ArrayList<>();
        for (Section section : CLOSE_PACKAGE) {
            if (!lines.isEmpty()) {
                lines.add("");
                lines.add(RULE);
                lines.add("");
            }
            lines.addAll(section.run().apply(work, period));
        }
        return List.copyOf(lines);
    }

    /** What is in the package, for anybody checking one arrived whole. */
    public static List<String> packageSections() {
        return CLOSE_PACKAGE.stream().map(Section::name).toList();
    }
}

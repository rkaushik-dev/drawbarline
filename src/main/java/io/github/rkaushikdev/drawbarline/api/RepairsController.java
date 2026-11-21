package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.repairs.Defect;
import io.github.rkaushikdev.drawbarline.repairs.Ground;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.LaborRates;
import io.github.rkaushikdev.drawbarline.repairs.Objection;
import io.github.rkaushikdev.drawbarline.repairs.Outcome;
import io.github.rkaushikdev.drawbarline.repairs.PriceMaster;
import io.github.rkaushikdev.drawbarline.repairs.PricedCard;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.repairs.Resolution;
import io.github.rkaushikdev.drawbarline.repairs.Responsibility;
import io.github.rkaushikdev.drawbarline.repairs.WhyMade;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Repair billing routes.
 *
 * <p>Filing a card and pricing it are separate here for the same reason they are separate on the
 * command line: the price master moves and a card filed today may be priced against a filing made
 * last week. So the post returns the card as it prices right now and the get returns it priced as
 * of whenever it is asked for.
 *
 * <p>The exception routes carry the ninety day clock with them. A late exception is refused
 * because the body was fine and the world had moved on.
 */
@RestController
@RequestMapping("/repairs")
@Tag(name = "repairs")
final class RepairsController {

    private final Work work;

    RepairsController(Work work) {
        this.work = work;
    }

    /** One line of a card as it is filed. */
    record DefectIn(
            @NotNull String job,
            @NotNull String whyMade,
            Integer quantity,
            Integer partYears,
            String overridden,
            String note) {

        DefectIn {
            quantity = quantity == null ? 1 : quantity;
            partYears = partYears == null ? 0 : partYears;
            note = note == null ? "" : note;
        }
    }

    /** A repair card as it is filed. */
    record CardIn(
            @NotNull Integer number,
            @NotNull String car,
            @NotNull String performedBy,
            @NotNull LocalDate performedOn,
            @NotNull String station,
            @NotNull List<@Valid DefectIn> defects) {}

    /** One priced line of a card, in cents. */
    record CardLineOut(
            String job, String whyMade, int quantity, long labor, long material, long amount, String answeredBy) {}

    /** An exception against one line. */
    record ObjectionIn(
            @NotNull Integer line,
            @NotNull String ground,
            @NotNull Long disputed,
            @NotNull LocalDate filedOn,
            String note) {

        ObjectionIn {
            note = note == null ? "" : note;
        }
    }

    /** An exception as it reads back, with how it was answered once it was. */
    record ObjectionOut(
            int cardNumber, int line, String ground, long disputed, LocalDate filedOn, Optional<String> outcome) {

        static ObjectionOut of(Objection objection) {
            return new ObjectionOut(
                    objection.cardNumber(),
                    objection.line(),
                    objection.ground().text(),
                    objection.disputed().cents(),
                    objection.filedOn(),
                    Optional.empty());
        }
    }

    /** How much of an exception is allowed, in cents. */
    record AnswerIn(@NotNull Long allowed) {}

    /** A card with the money on it. */
    record CardOut(
            int number,
            String car,
            String performedBy,
            LocalDate performedOn,
            String station,
            List<CardLineOut> lines,
            long total,
            long toOwner,
            long borneByTheRoad) {

        static CardOut of(PricedCard priced) {
            RepairCard card = priced.card();
            return new CardOut(
                    card.number(),
                    card.car().toString(),
                    card.performedBy().toString(),
                    card.on(),
                    card.station(),
                    priced.lines().stream()
                            .map(one -> new CardLineOut(
                                    one.defect().job().toString(),
                                    one.defect().why().toString(),
                                    one.defect().quantity(),
                                    one.labor().cents(),
                                    one.material().cents(),
                                    one.amount().cents(),
                                    one.answeredBy().text()))
                            .toList(),
                    priced.total().cents(),
                    priced.billedToOwner().cents(),
                    priced.borneByTheRoad().cents());
        }
    }

    static Defect defectOf(DefectIn filing) {
        Optional<Responsibility> who = Optional.empty();
        if (filing.overridden() != null && !filing.overridden().isEmpty()) {
            who = Optional.of(responsibilityNamed(filing.overridden()));
        }
        return new Defect(
                new JobCode(Text.strip(filing.job())),
                new WhyMade(Text.strip(filing.whyMade())),
                filing.quantity(),
                who,
                filing.partYears(),
                filing.note());
    }

    private static Responsibility responsibilityNamed(String text) {
        String wanted = Text.upper(Text.strip(text));
        for (Responsibility one : Responsibility.values()) {
            if (one.name().equals(wanted)) {
                return one;
            }
        }
        throw new BillingError("no such responsibility", Details.of("given", text));
    }

    static Ground groundOf(String text) {
        String wanted = Text.upper(Text.strip(text));
        for (Ground one : Ground.values()) {
            if (one.name().equals(wanted)) {
                return one;
            }
        }
        throw new BillingError("no such ground", Details.of("given", text));
    }

    private static PricedCard priced(UnitOfWork books, RepairCard card) {
        return PricedCard.price(
                card, books.repairPricing().master(), books.repairPricing().laborRates());
    }

    /** File a repair card. */
    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    CardOut fileCard(@Valid @RequestBody CardIn filing) {
        return work.apply(books -> {
            RepairCard card = new RepairCard(
                    filing.number(),
                    CarMark.parse(filing.car()),
                    ReportingMark.parse(filing.performedBy()),
                    filing.performedOn(),
                    Text.upper(Text.strip(filing.station())),
                    filing.defects().stream().map(RepairsController::defectOf).toList());
            books.cards().file(card);
            return CardOut.of(priced(books, card));
        });
    }

    /** Cards for one car or one road. */
    @GetMapping("/cards")
    List<CardOut> listCards(
            @RequestParam(name = "car", required = false) String car,
            @RequestParam(name = "road", required = false) String road) {
        return work.read(books -> {
            List<RepairCard> cards;
            if (car != null && !car.isEmpty()) {
                cards = books.cards().forCar(CarMark.parse(car));
            } else if (road != null && !road.isEmpty()) {
                cards = books.cards().byRoad(ReportingMark.parse(road));
            } else {
                throw new BillingError("ask for one car or one road");
            }
            PriceMaster master = books.repairPricing().master();
            LaborRates rates = books.repairPricing().laborRates();
            return cards.stream()
                    .map(one -> CardOut.of(PricedCard.price(one, master, rates)))
                    .toList();
        });
    }

    /** Everything filed and not answered. */
    @GetMapping("/exceptions")
    List<ObjectionOut> openExceptions() {
        return work.read(books ->
                books.cards().openObjections().stream().map(ObjectionOut::of).toList());
    }

    /** One card, priced as of now. */
    @GetMapping("/cards/{number}")
    CardOut showCard(@PathVariable("number") int number) {
        return work.read(books -> CardOut.of(priced(books, books.cards().get(number))));
    }

    /** Take exception to one line of a card. */
    @PostMapping("/cards/{number}/exceptions")
    @ResponseStatus(HttpStatus.CREATED)
    ObjectionOut takeException(@PathVariable("number") int number, @Valid @RequestBody ObjectionIn filing) {
        return work.apply(books -> {
            Objection objection = new Objection(
                    number,
                    filing.line(),
                    groundOf(filing.ground()),
                    new Money(filing.disputed()),
                    filing.filedOn(),
                    filing.note());
            books.cards().takeException(objection);
            return ObjectionOut.of(objection);
        });
    }

    /** Answer an exception by allowing some or none of it. */
    @PostMapping("/cards/{number}/exceptions/{line}/answer")
    ObjectionOut answer(
            @PathVariable("number") int number, @PathVariable("line") int line, @Valid @RequestBody AnswerIn filing) {
        return work.apply(books -> {
            Objection objection = books.cards().openObjections().stream()
                    .filter(one -> one.cardNumber() == number && one.line() == line)
                    .findFirst()
                    .orElseThrow(() -> new BillingError(
                            "no open exception on that line", Details.of("card", number, "line", line)));
            Money allowed = new Money(filing.allowed());
            Outcome outcome;
            if (allowed.isZero()) {
                outcome = Outcome.DECLINED;
            } else if (allowed.equals(objection.disputed())) {
                outcome = Outcome.ALLOWED;
            } else {
                outcome = Outcome.PART_ALLOWED;
            }
            books.cards().answer(objection, new Resolution(objection, outcome, allowed));
            return new ObjectionOut(
                    number,
                    line,
                    objection.ground().text(),
                    objection.disputed().cents(),
                    objection.filedOn(),
                    Optional.of(outcome.value()));
        });
    }
}

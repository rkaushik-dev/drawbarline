package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.UnknownCar;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.models.ObjectionRow;
import io.github.rkaushikdev.drawbarline.persistence.models.RepairCardRow;
import io.github.rkaushikdev.drawbarline.repairs.Defect;
import io.github.rkaushikdev.drawbarline.repairs.Ground;
import io.github.rkaushikdev.drawbarline.repairs.JobCode;
import io.github.rkaushikdev.drawbarline.repairs.Objection;
import io.github.rkaushikdev.drawbarline.repairs.Outcome;
import io.github.rkaushikdev.drawbarline.repairs.RepairCard;
import io.github.rkaushikdev.drawbarline.repairs.Resolution;
import io.github.rkaushikdev.drawbarline.repairs.Responsibility;
import io.github.rkaushikdev.drawbarline.repairs.WhyMade;
import io.github.rkaushikdev.drawbarline.support.Cars;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoryCardsTest {

    private static final JobCode WHEELSET = new JobCode("4312");
    private static final JobCode COUPLER = new JobCode("5104");
    private static final ReportingMark CN = new ReportingMark("CN");
    private static final ReportingMark ELS = new ReportingMark("ELS");
    private static final CarMark CAR = CarMark.parse("DBL 104207");
    private static final CarMark OTHER = CarMark.parse("DBL 104208");
    private static final LocalDate PERFORMED = LocalDate.of(2025, 1, 14);
    private static final LocalDate FILED = LocalDate.of(2025, 2, 3);

    private Database books;

    @BeforeEach
    void twoCarsOnFile() {
        books = TestDatabase.seeded(work -> {
            work.fleet().add(Cars.aCar("DBL 104207"));
            work.fleet().add(Cars.aCar("DBL 104208"));
        });
    }

    private static List<Defect> twoLines() {
        return List.of(
                new Defect(WHEELSET, new WhyMade("01")).withPartYears(5), new Defect(COUPLER, new WhyMade("03")));
    }

    private static RepairCard aCard() {
        return aCard(88104);
    }

    private static RepairCard aCard(int number) {
        return aCard(number, CAR, CN, PERFORMED, twoLines());
    }

    private static RepairCard aCard(
            int number, CarMark car, ReportingMark performedBy, LocalDate on, List<Defect> defects) {
        return new RepairCard(number, car, performedBy, on, "ESC", defects);
    }

    private static Objection anObjection() {
        return anObjection(88104, 0, Ground.DEPRECIATION, FILED);
    }

    private static Objection anObjection(int cardNumber, int line, Ground ground, LocalDate filedOn) {
        return new Objection(cardNumber, line, ground, new Money(20000), filedOn);
    }

    private void filed(RepairCard... cards) {
        try (UnitOfWork work = books.unitOfWork()) {
            for (RepairCard card : cards) {
                work.cards().file(card);
            }
            work.commit();
        }
    }

    private RepairCard read(int number) {
        try (UnitOfWork work = books.unitOfWork()) {
            return work.cards().get(number);
        }
    }

    @Test
    void theCardTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("repair_card", "defect", "objection", "resolution");
    }

    @Test
    void aCardRowReprNamesTheNumberAndCar() {
        RepairCardRow row = new RepairCardRow(88104, CAR, CN, PERFORMED, "ESC");
        assertThat(row).hasToString("<RepairCardRow 88104 DBL 104207>");
    }

    @Test
    void aCardGoesInAndComesBackWhole() {
        filed(aCard());
        assertThat(read(88104)).isEqualTo(aCard());
    }

    @Test
    void theLinesKeepTheirOrder() {
        filed(aCard());
        assertThat(read(88104).jobs()).containsExactly(WHEELSET, COUPLER);
    }

    @Test
    void aPartAgeSurvivesTheTrip() {
        filed(aCard());
        assertThat(read(88104).defects().get(0).partYears()).isEqualTo(5);
    }

    @Test
    void anOverriddenResponsibilitySurvivesTheTrip() {
        Defect overridden = new Defect(WHEELSET, new WhyMade("01"))
                .withOverridden(Responsibility.HANDLING_LINE, "flat spot from a hand brake");
        filed(aCard(88104, CAR, CN, PERFORMED, List.of(overridden)));
        Defect found = read(88104).defects().get(0);
        assertThat(found.answeredBy()).isSameAs(Responsibility.HANDLING_LINE);
        assertThat(found.note()).isEqualTo("flat spot from a hand brake");
    }

    @Test
    void theSameCardTwiceIsRefused() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.cards().file(aCard())).isInstanceOf(BillingError.class);
        }
    }

    @Test
    void aCardAgainstACarNobodyFiledIsRefused() {
        RepairCard nobody = aCard(88104, CarMark.parse("WC 000001"), CN, PERFORMED, twoLines());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.cards().file(nobody)).isInstanceOf(UnknownCar.class);
        }
    }

    @Test
    void aCardNobodyFiledIsRefused() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.cards().get(99999)).isInstanceOf(BillingError.class);
        }
    }

    @Test
    void cardsComeBackForOneCar() {
        filed(aCard(), aCard(88105, OTHER, CN, PERFORMED, twoLines()));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().forCar(CAR)).extracting(RepairCard::number).containsExactly(88104);
        }
    }

    @Test
    void cardsComeBackByRoad() {
        filed(aCard(), aCard(88105, CAR, ELS, PERFORMED, twoLines()));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().byRoad(ELS)).extracting(RepairCard::number).containsExactly(88105);
        }
    }

    @Test
    void cardsComeBackForASpanOfDays() {
        filed(aCard(), aCard(88105, CAR, CN, LocalDate.of(2025, 3, 4), twoLines()));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().between(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31)))
                    .extracting(RepairCard::number)
                    .containsExactly(88104);
        }
    }

    @Test
    void anExceptionIsFiledAndStaysOpen() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            work.cards().takeException(anObjection());
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().openObjections())
                    .extracting(Objection::line)
                    .containsExactly(0);
        }
    }

    @Test
    void theSameLineTwiceIsRefused() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            work.cards().takeException(anObjection());
            assertThatThrownBy(() -> work.cards().takeException(anObjection(88104, 0, Ground.PRICE, FILED)))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void anExceptionAgainstALineTheCardHasNotGotIsRefused() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.cards().takeException(anObjection(88104, 7, Ground.DEPRECIATION, FILED)))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void anExceptionAgainstACardNobodyFiledIsRefused() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.cards().takeException(anObjection(99999, 0, Ground.DEPRECIATION, FILED)))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void answeringAnExceptionClosesIt() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            Objection objection = work.cards().takeException(anObjection());
            work.cards().answer(objection, new Resolution(objection, Outcome.ALLOWED, new Money(20000)));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().openObjections()).isEmpty();
            assertThat(work.cards().outcomeOf(88104, 0)).containsSame(Outcome.ALLOWED);
        }
    }

    @Test
    void answeringTwiceIsRefused() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            Objection objection = work.cards().takeException(anObjection());
            work.cards().answer(objection, new Resolution(objection, Outcome.ALLOWED, new Money(20000)));
            assertThatThrownBy(() ->
                            work.cards().answer(objection, new Resolution(objection, Outcome.DECLINED, new Money(0))))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void answeringSomethingNeverFiledIsRefused() {
        filed(aCard());
        Objection objection = anObjection();
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() ->
                            work.cards().answer(objection, new Resolution(objection, Outcome.ALLOWED, new Money(1))))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void aLineWithNoExceptionHasNoOutcome() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().outcomeOf(88104, 1)).isEmpty();
        }
    }

    @Test
    void aDisputedAmountOfNothingIsRefusedByTheTable() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new ObjectionRow(88104, 1, "PRICE", new Money(0), FILED, ""));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void theRepositoryCountsItsCards() {
        filed(aCard(), aCard(88105));
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().size()).isEqualTo(2);
        }
    }

    @Test
    void anExceptionOutOfTimeIsRefusedAtTheRepository() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.cards()
                            .takeException(anObjection(88104, 0, Ground.DEPRECIATION, LocalDate.of(2025, 6, 3))))
                    .isInstanceOf(BillingError.class);
        }
    }

    @Test
    void anExceptionOnTheLastDayIsInTime() {
        filed(aCard());
        try (UnitOfWork work = books.unitOfWork()) {
            work.cards().takeException(anObjection(88104, 0, Ground.DEPRECIATION, LocalDate.of(2025, 4, 14)));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.cards().openObjections()).hasSize(1);
        }
    }
}

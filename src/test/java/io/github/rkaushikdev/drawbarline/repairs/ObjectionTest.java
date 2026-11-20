package io.github.rkaushikdev.drawbarline.repairs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.BillingError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ObjectionTest {

    static final JobCode WHEELSET = new JobCode("4312");
    static final JobCode COUPLER = new JobCode("5104");
    static final ReportingMark CN = new ReportingMark("CN");
    static final LocalDate DAY = LocalDate.of(2025, 1, 14);
    static final LocalDate FILED = LocalDate.of(2025, 2, 3);

    static PricedCard aPricedCard() {
        PriceMaster master = new PriceMaster();
        master.add(new PriceLine(WHEELSET, LocalDate.of(2024, 7, 1), 250, 128000));
        master.add(new PriceLine(COUPLER, LocalDate.of(2024, 7, 1), 125, 84000));
        LaborRates rates = new LaborRates();
        rates.file(new LaborRate(CN, LocalDate.of(2024, 7, 1), 4200));
        RepairCard card = new RepairCard(
                88104,
                CarMark.parse("DBL 104207"),
                CN,
                DAY,
                "ESC",
                List.of(new Defect(WHEELSET, new WhyMade("01")), new Defect(COUPLER, new WhyMade("01"))));
        return PricedCard.price(card, master, rates);
    }

    static Objection objection(int cardNumber, int line, Ground ground, Money disputed, LocalDate filedOn) {
        return new Objection(cardNumber, line, ground, disputed, filedOn);
    }

    static Objection anObjection() {
        return objection(88104, 0, Ground.DEPRECIATION, new Money(20000), FILED);
    }

    static Objection onLine(int line, Money disputed) {
        return objection(88104, line, Ground.DEPRECIATION, disputed, FILED);
    }

    static Objection filedOn(LocalDate day) {
        return objection(88104, 0, Ground.DEPRECIATION, new Money(20000), day);
    }

    @Test
    void theFilingClockRunsNinetyDays() {
        assertThat(Objection.mustFileBy(DAY)).isEqualTo(LocalDate.of(2025, 4, 14));
    }

    @Test
    void anObjectionPrintsTheCardLineAndGround() {
        assertThat(anObjection()).hasToString("card 88104 line 0 depreciation not allowed for $200.00");
    }

    @Test
    void anObjectionDisputingNothingIsRefused() {
        assertThatThrownBy(() -> onLine(0, new Money(0))).isInstanceOf(BillingError.class);
    }

    @Test
    void anObjectionOnANegativeLineIsRefused() {
        assertThatThrownBy(() -> onLine(-1, new Money(20000))).isInstanceOf(BillingError.class);
    }

    @Test
    void everyGroundHasReadableText() {
        assertThat(Ground.values())
                .allSatisfy(ground -> assertThat(ground.text()).isNotEmpty());
    }

    @Test
    void anObjectionInTimeIsFiled() {
        ExceptionRegister register = new ExceptionRegister();
        register.file(aPricedCard(), anObjection());
        assertThat(register.size()).isEqualTo(1);
    }

    @Test
    void anObjectionOnTheLastDayIsInTime() {
        ExceptionRegister register = new ExceptionRegister();
        register.file(aPricedCard(), filedOn(LocalDate.of(2025, 4, 14)));
        assertThat(register.size()).isEqualTo(1);
    }

    @Test
    void anObjectionOutOfTimeIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        assertThatThrownBy(() -> register.file(aPricedCard(), filedOn(LocalDate.of(2025, 4, 15))))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void anObjectionAgainstAnotherCardIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        Objection against = objection(99999, 0, Ground.DEPRECIATION, new Money(20000), FILED);
        assertThatThrownBy(() -> register.file(aPricedCard(), against)).isInstanceOf(BillingError.class);
    }

    @Test
    void anObjectionAgainstALineTheCardHasNotGotIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        assertThatThrownBy(() -> register.file(aPricedCard(), onLine(7, new Money(20000))))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void disputingMoreThanTheLineWasBilledIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        assertThatThrownBy(() -> register.file(aPricedCard(), onLine(0, new Money(500000))))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void theSameLineTwiceIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        PricedCard card = aPricedCard();
        register.file(card, anObjection());
        Objection again = objection(88104, 0, Ground.PRICE, new Money(20000), FILED);
        assertThatThrownBy(() -> register.file(card, again)).isInstanceOf(BillingError.class);
    }

    @Test
    void aSecondLineOfTheSameCardIsAllowed() {
        ExceptionRegister register = new ExceptionRegister();
        PricedCard card = aPricedCard();
        register.file(card, anObjection());
        register.file(card, onLine(1, new Money(1000)));
        assertThat(register.size()).isEqualTo(2);
    }

    @Test
    void anObjectionStartsOpen() {
        ExceptionRegister register = new ExceptionRegister();
        register.file(aPricedCard(), anObjection());
        assertThat(register.openObjections()).hasSize(1);
    }

    @Test
    void allowingTheWholeAmountClosesIt() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        Resolution found = register.settle(objection, new Money(20000));
        assertThat(found.outcome()).isSameAs(Outcome.ALLOWED);
        assertThat(found.stillOwed()).isEqualTo(new Money(0));
        assertThat(register.openObjections()).isEmpty();
    }

    @Test
    void allowingPartOfItSaysSo() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        Resolution found = register.settle(objection, new Money(8000));
        assertThat(found.outcome()).isSameAs(Outcome.PART_ALLOWED);
        assertThat(found.stillOwed()).isEqualTo(new Money(12000));
    }

    @Test
    void allowingNothingDeclinesIt() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        assertThat(register.settle(objection, new Money(0)).outcome()).isSameAs(Outcome.DECLINED);
    }

    @Test
    void allowingMoreThanWasDisputedIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        assertThatThrownBy(() -> register.settle(objection, new Money(30000))).isInstanceOf(BillingError.class);
    }

    @Test
    void settlingSomethingNeverFiledIsRefused() {
        assertThatThrownBy(() -> new ExceptionRegister().settle(anObjection(), new Money(100)))
                .isInstanceOf(BillingError.class);
    }

    @Test
    void settlingTwiceIsRefused() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        register.settle(objection, new Money(8000));
        assertThatThrownBy(() -> register.settle(objection, new Money(9000))).isInstanceOf(BillingError.class);
    }

    @Test
    void referringToArbitrationClosesOurFile() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        Resolution found = register.refer(objection);
        assertThat(found.outcome()).isSameAs(Outcome.ARBITRATION);
        assertThat(register.openObjections()).isEmpty();
    }

    @Test
    void referringSomethingNeverFiledIsRefused() {
        assertThatThrownBy(() -> new ExceptionRegister().refer(anObjection())).isInstanceOf(BillingError.class);
    }

    @Test
    void theAllowancesAgainstACardAddUp() {
        ExceptionRegister register = new ExceptionRegister();
        PricedCard card = aPricedCard();
        Objection first = register.file(card, anObjection());
        Objection second = register.file(card, onLine(1, new Money(1000)));
        register.settle(first, new Money(8000));
        register.settle(second, new Money(1000));
        assertThat(register.allowedAgainst(88104)).isEqualTo(new Money(9000));
    }

    @Test
    void aCardWithNothingAllowedShowsNothing() {
        assertThat(new ExceptionRegister().allowedAgainst(88104)).isEqualTo(new Money(0));
    }

    @Test
    void resolutionsComeBackInCardAndLineOrder() {
        ExceptionRegister register = new ExceptionRegister();
        PricedCard card = aPricedCard();
        Objection second = register.file(card, onLine(1, new Money(1000)));
        Objection first = register.file(card, anObjection());
        register.settle(second, new Money(500));
        register.settle(first, new Money(500));
        assertThat(register.resolutions())
                .extracting(one -> one.objection().line())
                .containsExactly(0, 1);
    }

    @Test
    void aResolutionPrintsItsOutcome() {
        ExceptionRegister register = new ExceptionRegister();
        Objection objection = register.file(aPricedCard(), anObjection());
        assertThat(register.settle(objection, new Money(20000)).toString()).endsWith("allowed in full $200.00");
    }
}

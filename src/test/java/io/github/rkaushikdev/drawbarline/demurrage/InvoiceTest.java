package io.github.rkaushikdev.drawbarline.demurrage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InvoiceTest {

    static final Tariff TARIFF =
            new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));
    static final LocalDate JANUARY = LocalDate.of(2025, 1, 1);

    static Detention aDetention(int number) {
        return aDetention(number, 3);
    }

    static Detention aDetention(int number, int chargeable) {
        return new Detention(
                CarMark.parse("CN " + number),
                "ESCPAP1",
                LocalDate.of(2025, 1, 14),
                LocalDate.of(2025, 1, 15),
                Optional.of(LocalDate.of(2025, 1, 16)),
                LocalDate.of(2025, 1, 22),
                chargeable,
                true,
                false);
    }

    static Invoice anInvoice() {
        return anInvoice(List.of(aDetention(1)));
    }

    static Invoice anInvoice(List<Detention> detentions) {
        return Invoice.assemble(
                4101, "ESCPAP", JANUARY, detentions.isEmpty() ? List.of(aDetention(1)) : detentions, TARIFF);
    }

    static Settlement aSettlement() {
        return new Settlement("PWRGRN", 2, 5, new Money(36000));
    }

    @Test
    void anInvoicePrintsItsNumberShipperMonthAndTotal() {
        assertThat(anInvoice()).hasToString("demurrage invoice 4101 ESCPAP 2025-01 $400.00");
    }

    @Test
    void aLinePrintsTheCarTheDaysAndTheMoney() {
        Line line = anInvoice().lines().get(0);
        assertThat(line).hasToString("CN 000001 ESCPAP1 3 days $400.00");
    }

    @Test
    void anInvoiceNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> new Invoice(0, "ESCPAP", JANUARY, List.of())).isInstanceOf(TariffError.class);
    }

    @Test
    void anInvoiceWithNoShipperIsRefused() {
        assertThatThrownBy(() -> new Invoice(1, "  ", JANUARY, List.of())).isInstanceOf(TariffError.class);
    }

    @Test
    void anInvoiceThatDoesNotStartAMonthIsRefused() {
        assertThatThrownBy(() -> new Invoice(1, "ESCPAP", LocalDate.of(2025, 1, 15), List.of()))
                .isInstanceOf(TariffError.class);
    }

    @Test
    void theTotalAddsTheLines() {
        Invoice invoice = anInvoice(List.of(aDetention(1), aDetention(2, 1)));
        assertThat(invoice.total()).isEqualTo(new Money(40000 + 10000));
    }

    @Test
    void theDaysAddUpToo() {
        Invoice invoice = anInvoice(List.of(aDetention(1), aDetention(2, 1)));
        assertThat(invoice.days()).isEqualTo(4);
    }

    @Test
    void aCarInsideFreeTimeIsOnTheInvoiceAndNotCharged() {
        Invoice invoice = anInvoice(List.of(aDetention(1), aDetention(2, 0)));
        assertThat(invoice.cars()).isEqualTo(2);
        assertThat(invoice.chargedCars()).isEqualTo(1);
        assertThat(invoice.freeCars()).isEqualTo(1);
    }

    @Test
    void aFreeLineCarriesNoMoney() {
        Invoice invoice = anInvoice(List.of(aDetention(2, 0)));
        assertThat(invoice.lines().get(0).charged()).isFalse();
        assertThat(invoice.total()).isEqualTo(new Money(0));
    }

    @Test
    void anInvoiceOfNothingButFreeCarsIsEmpty() {
        assertThat(anInvoice(List.of(aDetention(2, 0))).isEmpty()).isTrue();
    }

    @Test
    void anInvoiceWithAChargeIsNotEmpty() {
        assertThat(anInvoice().isEmpty()).isFalse();
    }

    @Test
    void linesComeBackInCarOrder() {
        Invoice invoice = anInvoice(List.of(aDetention(9), aDetention(2), aDetention(5)));
        assertThat(invoice.lines())
                .extracting(line -> line.car().orElseThrow().toString())
                .containsExactly("CN 000002", "CN 000005", "CN 000009");
    }

    @Test
    void aLineCanBeFoundByCar() {
        Invoice invoice = anInvoice(List.of(aDetention(1), aDetention(2)));
        assertThat(invoice.forCar(CarMark.parse("CN 2")).orElseThrow().days()).isEqualTo(3);
    }

    @Test
    void aCarNotOnTheInvoiceHasNoLine() {
        assertThat(anInvoice().forCar(CarMark.parse("WC 1"))).isEmpty();
    }

    @Test
    void aLineCarriesTheDatesTheDetentionWasCountedOver() {
        Line line = anInvoice().lines().get(0);
        assertThat(line.placedOn()).isEqualTo(LocalDate.of(2025, 1, 14));
        assertThat(line.countedTo()).isEqualTo(LocalDate.of(2025, 1, 22));
    }

    @Test
    void anInvoiceWithNoCarsTotalsNothing() {
        assertThat(Invoice.assemble(4101, "ESCPAP", JANUARY, List.of(), TARIFF).total())
                .isEqualTo(new Money(0));
    }

    @Test
    void anAverageInvoiceCarriesOneLine() {
        Invoice invoice = Invoice.assembleAverage(4102, aSettlement(), JANUARY);
        assertThat(invoice.cars()).isEqualTo(1);
        assertThat(invoice.average()).isTrue();
    }

    @Test
    void anAverageInvoiceShowsTheNetDebitsAsItsDays() {
        assertThat(Invoice.assembleAverage(4102, aSettlement(), JANUARY).days()).isEqualTo(3);
    }

    @Test
    void anAverageInvoiceNamesNoCar() {
        assertThat(Invoice.assembleAverage(4102, aSettlement(), JANUARY)
                        .lines()
                        .get(0)
                        .car())
                .isEmpty();
    }

    @Test
    void anAverageLinePrintsWithoutACar() {
        Line line =
                Invoice.assembleAverage(4102, aSettlement(), JANUARY).lines().get(0);
        assertThat(line).hasToString("all cars average agreement 3 days $360.00");
    }

    @Test
    void anAverageInvoiceSaysSoWhenItPrints() {
        assertThat(Invoice.assembleAverage(4102, aSettlement(), JANUARY).toString())
                .startsWith("average invoice");
    }

    @Test
    void aSettledMonthWithNothingOwedMakesAnEmptyInvoice() {
        Settlement settled = new Settlement("PWRGRN", 5, 2, new Money(0));
        assertThat(Invoice.assembleAverage(4102, settled, JANUARY).isEmpty()).isTrue();
    }

    @Test
    void aLineCarriesTheAmountTheTariffGives() {
        assertThat(new Line(Optional.empty(), "x", JANUARY, JANUARY, 2, new Money(500)).amount())
                .isEqualTo(new Money(500));
    }
}

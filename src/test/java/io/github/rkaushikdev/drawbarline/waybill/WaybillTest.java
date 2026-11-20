package io.github.rkaushikdev.drawbarline.waybill;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.SODIUM_HYDROXIDE;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WaybillTest {

    static final Lading GRAIN = new Lading(WHEAT, new Weight(198000));

    /** The waybill every test starts from; a test changes the field it is about. */
    static final class Fields {
        int number = 41207;
        LocalDate dated = LocalDate.of(2025, 1, 14);
        CarMark car = CarMark.parse("DBL 104207");
        String shipper = "PWRGRN";
        String consignee = "ESCPAP";
        List<String> route = List.of("PWR", "HRM", "BKR", "ESC");
        Optional<Lading> lading = Optional.of(GRAIN);

        Waybill build() {
            return new Waybill(number, dated, car, shipper, consignee, route, lading);
        }
    }

    static Waybill aWaybill() {
        return new Fields().build();
    }

    static Waybill anEmpty() {
        Fields changed = new Fields();
        changed.lading = Optional.empty();
        return changed.build();
    }

    static Waybill routed(String... stations) {
        Fields changed = new Fields();
        changed.route = List.of(stations);
        return changed.build();
    }

    @Test
    void aWaybillPrintsCarRouteAndLading() {
        assertThat(aWaybill()).hasToString("wb 41207 DBL 104207 PWR to ESC wheat");
    }

    @Test
    void anEmptyWaybillSaysEmpty() {
        assertThat(anEmpty().toString()).endsWith("empty");
    }

    @Test
    void theRouteGivesTheOriginAndDestination() {
        Waybill bill = aWaybill();
        assertThat(bill.origin()).isEqualTo("PWR");
        assertThat(bill.destination()).isEqualTo("ESC");
    }

    @Test
    void aLoadedWaybillKnowsIt() {
        assertThat(aWaybill().loaded()).isTrue();
    }

    @Test
    void anEmptyWaybillKnowsIt() {
        assertThat(anEmpty().loaded()).isFalse();
    }

    @Test
    void aWaybillTakesItsHazardFromTheLading() {
        Fields changed = new Fields();
        changed.lading = Optional.of(new Lading(SODIUM_HYDROXIDE, new Weight(180000)));
        assertThat(changed.build().hazardous()).isTrue();
    }

    @Test
    void anEmptyWaybillIsNotHazardous() {
        assertThat(anEmpty().hazardous()).isFalse();
    }

    @Test
    void aWaybillNumberedBelowOneIsRefused() {
        Fields changed = new Fields();
        changed.number = 0;
        assertThatThrownBy(changed::build).isInstanceOf(WaybillError.class);
    }

    @Test
    void aRouteOfOnePlaceIsRefused() {
        assertThatThrownBy(() -> routed("ESC")).isInstanceOf(WaybillError.class);
    }

    @Test
    void aRouteThroughAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> routed("PWR", "CHI")).isInstanceOf(MileageError.class);
    }

    @Test
    void aRouteThatRepeatsAStationRunningIsRefused() {
        assertThatThrownBy(() -> routed("PWR", "PWR", "ESC")).isInstanceOf(WaybillError.class);
    }

    @Test
    void aRouteThatComesBackLaterIsAllowed() {
        assertThat(routed("PWR", "ESC", "PWR", "IMT").destination()).isEqualTo("IMT");
    }

    @Test
    void aWaybillWithNoShipperIsRefused() {
        Fields changed = new Fields();
        changed.shipper = "  ";
        assertThatThrownBy(changed::build).isInstanceOf(WaybillError.class);
    }

    @Test
    void aWaybillWithNoConsigneeIsRefused() {
        Fields changed = new Fields();
        changed.consignee = "";
        assertThatThrownBy(changed::build).isInstanceOf(WaybillError.class);
    }

    @Test
    void theMileageRunsAlongTheRoute() {
        assertThat(aWaybill().tenths()).isEqualTo(336);
    }

    @Test
    void theMileageReadsAsDecimalMiles() {
        assertThat(aWaybill().miles()).isEqualTo(33.6);
    }

    @Test
    void aRouteDownTheBranchMeasuresThroughPowers() {
        assertThat(routed("IMT", "PWR", "ESC").tenths()).isEqualTo(286 + 336);
    }

    @Test
    void aWaybillKnowsWhatItPasses() {
        Waybill bill = aWaybill();
        assertThat(bill.passes("HRM")).isTrue();
        assertThat(bill.passes("GRB")).isFalse();
    }

    @Test
    void aDiversionCutsTheRouteAndAddsTheNewPlace() {
        Waybill diverted = aWaybill().divert("HRM", "GRB", 41208);
        assertThat(diverted.route()).containsExactly("PWR", "HRM", "GRB");
        assertThat(diverted.number()).isEqualTo(41208);
    }

    @Test
    void aDiversionKeepsTheLadingAndTheCar() {
        Waybill diverted = aWaybill().divert("HRM", "GRB", 41208);
        assertThat(diverted.lading()).contains(GRAIN);
        assertThat(diverted.car()).isEqualTo(aWaybill().car());
    }

    @Test
    void aDiversionChangesTheMileage() {
        assertThat(aWaybill().divert("HRM", "GRB", 41208).tenths()).isEqualTo(65 + 818);
    }

    @Test
    void aDiversionAtAPlaceNotOnTheRouteIsRefused() {
        assertThatThrownBy(() -> aWaybill().divert("GRB", "MRN", 41208)).isInstanceOf(WaybillError.class);
    }

    @Test
    void aDiversionAtTheOriginIsRefused() {
        assertThatThrownBy(() -> aWaybill().divert("PWR", "GRB", 41208)).isInstanceOf(WaybillError.class);
    }

    @Test
    void aDiversionToWhereTheCarAlreadyIsIsRefused() {
        assertThatThrownBy(() -> aWaybill().divert("HRM", "HRM", 41208)).isInstanceOf(WaybillError.class);
    }

    @Test
    void twoWaybillsWithTheSameDetailsCompareEqual() {
        assertThat(aWaybill()).isEqualTo(aWaybill());
    }
}

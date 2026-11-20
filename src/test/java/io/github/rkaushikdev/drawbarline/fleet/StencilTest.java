package io.github.rkaushikdev.drawbarline.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class StencilTest {

    static Car aCar() {
        return aCar("LO", "L351");
    }

    static Car aCar(String designation, String typeCode) {
        return new Car(
                CarMark.parse("DBL 104207"),
                new MechanicalDesignation(designation),
                new CarTypeCode(typeCode),
                LocalDate.of(1998, 6, 1),
                new Weight(63500),
                new Weight(286000),
                Length.of(59, 6));
    }

    /** The weighing every test starts from; a test changes the field it is about. */
    static final class Fields {
        Weight pounds = new Weight(63500);
        LocalDate on = LocalDate.of(2015, 3, 4);
        String where = "ESC";

        Weighing build() {
            return new Weighing(pounds, on, where);
        }
    }

    static Weighing aWeighing() {
        return new Fields().build();
    }

    static Weighing weighedAt(String where) {
        Fields changed = new Fields();
        changed.where = where;
        return changed.build();
    }

    static Weighing weighedOn(LocalDate on) {
        Fields changed = new Fields();
        changed.on = on;
        return changed.build();
    }

    static Weighing weighing(Weight pounds) {
        Fields changed = new Fields();
        changed.pounds = pounds;
        return changed.build();
    }

    @Test
    void aWeighingPrintsThePlaceAndTheStamp() {
        assertThat(aWeighing()).hasToString("ESC 3-15");
    }

    @Test
    void aStampCarriesTwoDigitsOfYear() {
        assertThat(weighedOn(LocalDate.of(2005, 11, 1)).stamp()).isEqualTo("11-05");
    }

    @Test
    void aBuildersWeightSaysNew() {
        assertThat(weighedAt("NEW").asBuilt()).isTrue();
    }

    @Test
    void aScaleWeighingIsNotAsBuilt() {
        assertThat(aWeighing().asBuilt()).isFalse();
    }

    @Test
    void aPlaceIsTakenUpperCase() {
        assertThat(weighedAt("esc").where()).isEqualTo("ESC");
    }

    @Test
    void aWeighingWithNoPlaceIsRefused() {
        assertThatThrownBy(() -> weighedAt("  ")).isInstanceOf(StencilError.class);
    }

    @Test
    void aPlaceThatIsNotAStationCodeIsRefused() {
        assertThatThrownBy(() -> weighedAt("the scale at powers")).isInstanceOf(StencilError.class);
    }

    @Test
    void aHopperIsDueBackInTenYears() {
        assertThat(Stencil.dueBy(aCar(), aWeighing())).contains(LocalDate.of(2025, 3, 4));
    }

    @Test
    void aLeapDayWeighingComesDueAtTheEndOfFebruary() {
        assertThat(Stencil.dueBy(aCar(), weighedOn(LocalDate.of(2016, 2, 29)))).contains(LocalDate.of(2026, 2, 28));
    }

    @Test
    void aTankCarIsExemptFromReweighing() {
        Car tank = aCar("T", "T104");
        assertThat(Stencil.exempt(tank)).isTrue();
        assertThat(Stencil.dueBy(tank, aWeighing())).isEmpty();
    }

    @Test
    void maintenanceEquipmentIsExempt() {
        assertThat(Stencil.exempt(aCar("M", "M930"))).isTrue();
    }

    @Test
    void aHopperIsNotExempt() {
        assertThat(Stencil.exempt(aCar())).isFalse();
    }

    @Test
    void aCarInsideItsCycleIsNotOverdue() {
        assertThat(Stencil.overdueOn(aCar(), aWeighing(), LocalDate.of(2025, 3, 4)))
                .isFalse();
    }

    @Test
    void aCarPastItsCycleIsOverdue() {
        assertThat(Stencil.overdueOn(aCar(), aWeighing(), LocalDate.of(2025, 3, 5)))
                .isTrue();
    }

    @Test
    void anExemptCarIsNeverOverdue() {
        assertThat(Stencil.overdueOn(aCar("T", "T104"), aWeighing(), LocalDate.of(2099, 1, 1)))
                .isFalse();
    }

    @Test
    void theLoadLimitUsesTheWeighedLightWeight() {
        assertThat(Stencil.loadLimitFrom(aCar(), weighing(new Weight(64200)))).isEqualTo(new Weight(221800));
    }

    @Test
    void aHeavierCarCarriesLess() {
        Weight lighter = Stencil.loadLimitFrom(aCar(), weighing(new Weight(63500)));
        Weight heavier = Stencil.loadLimitFrom(aCar(), weighing(new Weight(64200)));
        assertThat(heavier).isLessThan(lighter);
    }

    @Test
    void aScaleReadingAtTheGrossRailLoadIsRefused() {
        assertThatThrownBy(() -> Stencil.loadLimitFrom(aCar(), weighing(new Weight(286000))))
                .isInstanceOf(StencilError.class);
    }

    @Test
    void theStencilBlockReadsTheWayTheCarIsPainted() {
        assertThat(Stencil.block(aCar(), aWeighing()))
                .isEqualTo(List.of(
                        "DBL 104207", "LO L351", "CAPY 286000", "LD LMT 222500", "LT WT 63500", "RWT ESC 3-15"));
    }

    @Test
    void aCarThatHasNeverBeenReweighedCarriesItsBuildStamp() {
        Fields changed = new Fields();
        changed.where = "NEW";
        changed.on = LocalDate.of(1998, 6, 1);
        List<String> lines = Stencil.block(aCar(), changed.build());
        assertThat(lines.get(lines.size() - 1)).isEqualTo("NEW 6-98");
    }
}

package io.github.rkaushikdev.drawbarline.embargo;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.KRAFT_PAPER;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EmbargoTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");

    /** The embargo every test starts from: number 101, bridge out at Hermansville. */
    static final class Fields {
        int number = 101;
        LocalDate effective = LocalDate.of(2025, 1, 6);
        String reason = "bridge out at hermansville";
        Optional<String> station = Optional.of("HRM");
        Optional<String> commodityFamily = Optional.empty();
        Optional<ReportingMark> road = Optional.empty();
        Optional<LocalDate> expires = Optional.empty();
        boolean permitsAllowed = false;

        Fields(int number) {
            this.number = number;
        }

        Fields() {}

        Fields noStation() {
            station = Optional.empty();
            return this;
        }

        Embargo build() {
            return new Embargo(number, effective, reason, station, commodityFamily, road, expires, permitsAllowed);
        }
    }

    static Embargo anEmbargo() {
        return new Fields().build();
    }

    static Embargo onFamily(String family) {
        Fields changed = new Fields().noStation();
        changed.commodityFamily = Optional.of(family);
        return changed.build();
    }

    static Embargo withRoad(ReportingMark road) {
        Fields changed = new Fields().noStation();
        changed.road = Optional.of(road);
        return changed.build();
    }

    static Embargo expiring(LocalDate expires) {
        Fields changed = new Fields();
        changed.expires = Optional.of(expires);
        return changed.build();
    }

    static Embargo at(int number, String station) {
        Fields changed = new Fields(number);
        changed.station = Optional.of(station);
        return changed.build();
    }

    static Embargo permitting() {
        Fields changed = new Fields();
        changed.permitsAllowed = true;
        return changed.build();
    }

    static Movement at(String station) {
        return Movement.anywhere().atStation(station);
    }

    @Test
    void anEmbargoPrintsWhatItStops() {
        assertThat(anEmbargo()).hasToString("embargo 101 at HRM");
    }

    @Test
    void anEmbargoOnACommodityPrintsTheFamily() {
        assertThat(onFamily("01")).hasToString("embargo 101 on 01");
    }

    @Test
    void anEmbargoAgainstARoadPrintsIt() {
        assertThat(withRoad(CN)).hasToString("embargo 101 with CN");
    }

    @Test
    void anEmbargoNumberedBelowOneIsRefused() {
        assertThatThrownBy(() -> new Fields(0).build()).isInstanceOf(EmbargoError.class);
    }

    @Test
    void anEmbargoWithNoReasonIsRefused() {
        Fields changed = new Fields();
        changed.reason = "  ";
        assertThatThrownBy(changed::build).isInstanceOf(EmbargoError.class);
    }

    @Test
    void anEmbargoThatRestrictsNothingIsRefused() {
        EmbargoError caught = catchThrowableOfType(
                EmbargoError.class, () -> new Fields().noStation().build());
        assertThat(caught.message()).contains("whole railroad");
    }

    @Test
    void anEmbargoAtAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> at(101, "CHI")).isInstanceOf(MileageError.class);
    }

    @Test
    void aCommodityFamilyThatIsNotDigitsIsRefused() {
        assertThatThrownBy(() -> onFamily("grain")).isInstanceOf(EmbargoError.class);
    }

    @Test
    void anEmbargoThatExpiresBeforeItStartsIsRefused() {
        assertThatThrownBy(() -> expiring(LocalDate.of(2025, 1, 1))).isInstanceOf(EmbargoError.class);
    }

    @Test
    void anOpenEmbargoRunsOn() {
        Embargo found = anEmbargo();
        assertThat(found.inForceOn(LocalDate.of(2025, 1, 6))).isTrue();
        assertThat(found.inForceOn(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void anEmbargoIsNotInForceBeforeItStarts() {
        assertThat(anEmbargo().inForceOn(LocalDate.of(2025, 1, 5))).isFalse();
    }

    @Test
    void anEmbargoStopsOnTheDayAfterItExpires() {
        Embargo found = expiring(LocalDate.of(2025, 1, 20));
        assertThat(found.inForceOn(LocalDate.of(2025, 1, 20))).isTrue();
        assertThat(found.inForceOn(LocalDate.of(2025, 1, 21))).isFalse();
    }

    @Test
    void aStationEmbargoCatchesThatStation() {
        assertThat(anEmbargo().catches(at("HRM"))).isTrue();
    }

    @Test
    void aStationEmbargoLetsOtherStationsBy() {
        assertThat(anEmbargo().catches(at("ESC"))).isFalse();
    }

    @Test
    void aCommodityEmbargoCatchesTheFamily() {
        Embargo found = onFamily("01");
        assertThat(found.catches(Movement.anywhere().carrying(WHEAT))).isTrue();
        assertThat(found.catches(Movement.anywhere().carrying(KRAFT_PAPER))).isFalse();
    }

    @Test
    void aCommodityEmbargoCatchesNothingWithoutACommodity() {
        assertThat(onFamily("01").catches(at("ESC"))).isFalse();
    }

    @Test
    void aRoadEmbargoCatchesOnlyThatRoad() {
        Embargo found = withRoad(CN);
        assertThat(found.catches(Movement.anywhere().byRoad(CN))).isTrue();
        assertThat(found.catches(Movement.anywhere().byRoad(ELS))).isFalse();
    }

    @Test
    void everyStatedFieldHasToMatch() {
        Fields changed = new Fields();
        changed.commodityFamily = Optional.of("01");
        Embargo found = changed.build();
        assertThat(found.catches(at("HRM").carrying(WHEAT))).isTrue();
        assertThat(found.catches(at("HRM").carrying(KRAFT_PAPER))).isFalse();
        assertThat(found.catches(at("ESC").carrying(WHEAT))).isFalse();
    }

    @Test
    void anEmbargoGoesOnTheRegister() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(anEmbargo());
        assertThat(register.get(101).station()).contains("HRM");
        assertThat(register.size()).isEqualTo(1);
    }

    @Test
    void theSameNumberTwiceIsRefused() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(anEmbargo());
        assertThatThrownBy(() -> register.issue(anEmbargo())).isInstanceOf(EmbargoError.class);
    }

    @Test
    void anEmbargoNobodyIssuedIsRefused() {
        assertThatThrownBy(() -> new EmbargoRegister().get(101)).isInstanceOf(EmbargoError.class);
    }

    @Test
    void liftingAnEmbargoEndsIt() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(anEmbargo());
        register.lift(101, LocalDate.of(2025, 1, 20));
        assertThat(register.get(101).inForceOn(LocalDate.of(2025, 1, 21))).isFalse();
    }

    @Test
    void liftingSomethingWithAnEndAlreadyIsRefused() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(expiring(LocalDate.of(2025, 1, 20)));
        assertThatThrownBy(() -> register.lift(101, LocalDate.of(2025, 1, 15))).isInstanceOf(EmbargoError.class);
    }

    @Test
    void liftingBeforeItTookEffectIsRefused() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(anEmbargo());
        assertThatThrownBy(() -> register.lift(101, LocalDate.of(2025, 1, 1))).isInstanceOf(EmbargoError.class);
    }

    @Test
    void liftingSomethingNeverIssuedIsRefused() {
        assertThatThrownBy(() -> new EmbargoRegister().lift(101, LocalDate.of(2025, 1, 20)))
                .isInstanceOf(EmbargoError.class);
    }

    @Test
    void theLiveEmbargoesComeBackInNumberOrder() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(at(102, "ESC"));
        register.issue(anEmbargo());
        assertThat(register.inForceOn(LocalDate.of(2025, 1, 10)))
                .extracting(Embargo::number)
                .containsExactly(101, 102);
    }

    @Test
    void anExpiredEmbargoIsNotLive() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(expiring(LocalDate.of(2025, 1, 8)));
        assertThat(register.inForceOn(LocalDate.of(2025, 1, 10))).isEmpty();
    }

    @Test
    void theEmbargoesAgainstOneMovementComeBack() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(anEmbargo());
        register.issue(at(102, "ESC"));
        Fields family = new Fields(103).noStation();
        family.commodityFamily = Optional.of("01");
        register.issue(family.build());
        List<Embargo> found =
                register.against(LocalDate.of(2025, 1, 10), at("HRM").carrying(WHEAT));
        assertThat(found).extracting(Embargo::number).containsExactly(101, 103);
    }

    @Test
    void aMovementThatRunsIntoNothingComesBackClear() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(anEmbargo());
        assertThat(register.against(LocalDate.of(2025, 1, 10), at("ESC"))).isEmpty();
    }

    @Test
    void permitsAreOffUnlessTheEmbargoAllowsThem() {
        assertThat(anEmbargo().permitsAllowed()).isFalse();
        assertThat(permitting().permitsAllowed()).isTrue();
    }

    @Test
    void aBuilderFillsInOnlyTheFieldsItIsGiven() {
        Embargo found = Embargo.builder(104, LocalDate.of(2025, 1, 6), "mill full")
                .station("ESC")
                .commodityFamily("26")
                .road(CN)
                .expires(LocalDate.of(2025, 1, 31))
                .permitsAllowed(true)
                .build();
        assertThat(found).hasToString("embargo 104 at ESC on 26 with CN");
        assertThat(found.expires()).contains(LocalDate.of(2025, 1, 31));
        assertThat(found.permitsAllowed()).isTrue();
        assertThat(Embargo.builder(105, LocalDate.of(2025, 1, 6), "x")
                        .road(ELS)
                        .build()
                        .station())
                .isEmpty();
    }

    @Test
    void aLiftedEmbargoKeepsWhetherItAllowedPermits() {
        EmbargoRegister register = new EmbargoRegister();
        register.issue(permitting());
        assertThat(register.lift(101, LocalDate.of(2025, 1, 20)).permitsAllowed())
                .isTrue();
    }
}

package io.github.rkaushikdev.drawbarline.agreements;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgreementsTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final CarKind HOPPER = CarKind.COVERED_HOPPER;
    static final CarKind TANK = CarKind.TANK;
    static final LocalDateTime MORNING = LocalDateTime.of(2025, 1, 14, 9, 0);

    /** The agreement every test starts from; a test changes the fields it is about. */
    static final class Fields {
        ReportingMark road = CN;
        String junction = "ESC";
        LocalDate effective = LocalDate.of(2024, 7, 1);
        Set<CarKind> kinds = Set.of(HOPPER, CarKind.BOX);
        int dailyCap = 40;
        LocalTime opens = LocalTime.of(7, 0);
        LocalTime closes = LocalTime.of(19, 0);
        boolean hazardous = false;
        Optional<LocalDate> expires = Optional.empty();

        InterchangeAgreement build() {
            return new InterchangeAgreement(
                    road, junction, effective, kinds, dailyCap, opens, closes, hazardous, expires);
        }
    }

    static InterchangeAgreement anAgreement() {
        return new Fields().build();
    }

    static InterchangeAgreement expiring(LocalDate expires) {
        Fields changed = new Fields();
        changed.expires = Optional.of(expires);
        return changed.build();
    }

    @Test
    void anAgreementPrintsItsHoursAndCap() {
        assertThat(anAgreement()).hasToString("ESC/CN 07:00 to 19:00 cap 40");
    }

    @Test
    void anAgreementAtAPlaceWeDoNotServeIsRefused() {
        Fields changed = new Fields();
        changed.junction = "CHI";
        assertThatThrownBy(changed::build).isInstanceOf(MileageError.class);
    }

    @Test
    void anAgreementTakingNoKindIsRefused() {
        Fields changed = new Fields();
        changed.kinds = Set.of();
        assertThatThrownBy(changed::build).isInstanceOf(AgreementError.class);
    }

    @Test
    void aCapOfNothingIsRefused() {
        Fields changed = new Fields();
        changed.dailyCap = 0;
        assertThatThrownBy(changed::build).isInstanceOf(AgreementError.class);
    }

    @Test
    void aJunctionThatClosesBeforeItOpensIsRefused() {
        Fields changed = new Fields();
        changed.opens = LocalTime.of(19, 0);
        changed.closes = LocalTime.of(7, 0);
        assertThatThrownBy(changed::build).isInstanceOf(AgreementError.class);
    }

    @Test
    void anAgreementThatExpiresFirstIsRefused() {
        assertThatThrownBy(() -> expiring(LocalDate.of(2024, 1, 1))).isInstanceOf(AgreementError.class);
    }

    @Test
    void anOpenAgreementRunsOn() {
        assertThat(anAgreement().inForceOn(LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void anAgreementIsNotInForceBeforeItStarts() {
        assertThat(anAgreement().inForceOn(LocalDate.of(2024, 1, 1))).isFalse();
    }

    @Test
    void anExpiredAgreementIsNotInForce() {
        assertThat(expiring(LocalDate.of(2024, 12, 31)).inForceOn(LocalDate.of(2025, 1, 14)))
                .isFalse();
    }

    @Test
    void theJunctionIsMannedInsideItsHours() {
        assertThat(anAgreement().mannedAt(MORNING)).isTrue();
    }

    @Test
    void theJunctionIsClosedBeforeItOpens() {
        assertThat(anAgreement().mannedAt(LocalDateTime.of(2025, 1, 14, 6, 0))).isFalse();
    }

    @Test
    void theJunctionIsClosedOnTheClosingHour() {
        assertThat(anAgreement().mannedAt(LocalDateTime.of(2025, 1, 14, 19, 0))).isFalse();
    }

    @Test
    void anAgreementTakesTheKindsItNames() {
        assertThat(anAgreement().takesKind(HOPPER)).isTrue();
        assertThat(anAgreement().takesKind(TANK)).isFalse();
    }

    @Test
    void aCarInsideTheAgreementGoesAcross() {
        assertThat(Agreements.willTake(anAgreement(), HOPPER, MORNING)).isTrue();
    }

    @Test
    void aKindTheRoadDoesNotTakeIsRefused() {
        List<String> found = Agreements.checkDelivery(anAgreement(), TANK, MORNING);
        assertThat(found).anySatisfy(one -> assertThat(one).contains("does not take a tank car"));
    }

    @Test
    void hazardousMaterialIsRefusedUnlessTheAgreementAllowsIt() {
        List<String> found = Agreements.checkDelivery(anAgreement(), HOPPER, MORNING, true, 0);
        assertThat(found).anySatisfy(one -> assertThat(one).contains("hazardous"));
    }

    @Test
    void anAgreementThatAllowsHazardousTakesIt() {
        Fields changed = new Fields();
        changed.hazardous = true;
        assertThat(Agreements.willTake(changed.build(), HOPPER, MORNING, true, 0))
                .isTrue();
    }

    @Test
    void aCarOfferedWhenTheJunctionIsShutIsRefused() {
        List<String> found = Agreements.checkDelivery(anAgreement(), HOPPER, LocalDateTime.of(2025, 1, 14, 22, 0));
        assertThat(found).anySatisfy(one -> assertThat(one).contains("closed at 22:00"));
    }

    @Test
    void aCarPastTheDailyCapIsRefused() {
        List<String> found = Agreements.checkDelivery(anAgreement(), HOPPER, MORNING, false, 40);
        assertThat(found).anySatisfy(one -> assertThat(one).contains("taken its 40 cars"));
    }

    @Test
    void theCarThatMakesTheCapStillGoes() {
        assertThat(Agreements.willTake(anAgreement(), HOPPER, MORNING, false, 39))
                .isTrue();
    }

    @Test
    void anAgreementOutOfForceRefusesEverything() {
        List<String> found = Agreements.checkDelivery(expiring(LocalDate.of(2024, 12, 31)), HOPPER, MORNING);
        assertThat(found).anySatisfy(one -> assertThat(one).contains("no agreement in force"));
    }

    @Test
    void severalReasonsComeBackTogether() {
        List<String> found =
                Agreements.checkDelivery(anAgreement(), TANK, LocalDateTime.of(2025, 1, 14, 22, 0), true, 40);
        assertThat(found).hasSize(4);
    }

    @Test
    void aNegativeCountOfCarsIsRefused() {
        assertThatThrownBy(() -> Agreements.checkDelivery(anAgreement(), HOPPER, MORNING, false, -1))
                .isInstanceOf(AgreementError.class);
    }

    @Test
    void theRoomLeftComesOffTheCap() {
        assertThat(Agreements.roomLeft(anAgreement(), 12)).isEqualTo(28);
    }

    @Test
    void aJunctionAtItsCapHasNoRoom() {
        assertThat(Agreements.roomLeft(anAgreement(), 40)).isZero();
    }

    @Test
    void aJunctionPastItsCapHasNoNegativeRoom() {
        assertThat(Agreements.roomLeft(anAgreement(), 55)).isZero();
    }

    @Test
    void aNegativeCountIsRefusedWhenAskingForRoom() {
        assertThatThrownBy(() -> Agreements.roomLeft(anAgreement(), -1)).isInstanceOf(AgreementError.class);
    }
}

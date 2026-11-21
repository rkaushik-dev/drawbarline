package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.persistence.AgreementRepository.kindsFromText;
import static io.github.rkaushikdev.drawbarline.persistence.AgreementRepository.kindsToText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.agreements.AgreementError;
import io.github.rkaushikdev.drawbarline.agreements.InterchangeAgreement;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.fleet.EquipmentError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.persistence.models.AgreementRow;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepositoryAgreementsTest {

    private static final ReportingMark CN = new ReportingMark("CN");
    private static final ReportingMark ELS = new ReportingMark("ELS");
    private static final CarKind HOPPER = CarKind.COVERED_HOPPER;
    private static final CarKind BOX = CarKind.BOX;
    private static final LocalDate JULY_2024 = LocalDate.of(2024, 7, 1);
    private static final LocalDate MID_JANUARY = LocalDate.of(2025, 1, 14);
    private static final LocalTime SEVEN = LocalTime.of(7, 0);
    private static final LocalTime NINETEEN = LocalTime.of(19, 0);

    private static InterchangeAgreement anAgreement() {
        return anAgreement(CN, Set.of(HOPPER, BOX), JULY_2024, 40, Optional.empty());
    }

    private static InterchangeAgreement anAgreement(
            ReportingMark road, Set<CarKind> kinds, LocalDate effective, int dailyCap, Optional<LocalDate> expires) {
        return new InterchangeAgreement(road, "ESC", effective, kinds, dailyCap, SEVEN, NINETEEN, false, expires);
    }

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database signed() {
        return TestDatabase.seeded(work -> {
            work.agreements().sign(anAgreement());
            work.agreements().sign(anAgreement(ELS, Set.of(HOPPER), JULY_2024, 40, Optional.empty()));
        });
    }

    private static AgreementRow aRow(int dailyCap, LocalTime opens, LocalTime closes) {
        return new AgreementRow(CN, "ESC", JULY_2024, "XL", dailyCap, opens, closes, false, null);
    }

    @Test
    void theAgreementTableIsOnTheMetadata() {
        assertThat(Database.modelTables()).contains("interchange_agreement");
    }

    @Test
    void aRowReprNamesTheJunctionAndRoad() {
        assertThat(aRow(40, SEVEN, NINETEEN)).hasToString("<AgreementRow ESC/CN from 2024-07-01>");
    }

    @Test
    void theKindsPackInThePublishedOrder() {
        assertThat(kindsToText(Set.of(BOX, HOPPER))).isEqualTo("XL");
    }

    @Test
    void theKindsReadBackAsASet() {
        assertThat(kindsFromText("XL")).isEqualTo(Set.of(BOX, HOPPER));
    }

    @Test
    void aLetterNobodyPublishesIsRefusedOnTheWayBack() {
        assertThatThrownBy(() -> kindsFromText("XZ")).isInstanceOf(EquipmentError.class);
    }

    @Test
    void anAgreementGoesInAndComesBackWhole() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThat(work.agreements().at("ESC", CN, MID_JANUARY)).isEqualTo(anAgreement());
        }
    }

    @Test
    void theHoursSurviveTheTrip() {
        InterchangeAgreement found;
        try (UnitOfWork work = signed().unitOfWork()) {
            found = work.agreements().at("ESC", CN, MID_JANUARY);
        }
        assertThat(found.opens()).isEqualTo(SEVEN);
        assertThat(found.closes()).isEqualTo(NINETEEN);
    }

    @Test
    void theSameAgreementTwiceIsRefused() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThatThrownBy(() -> work.agreements().sign(anAgreement())).isInstanceOf(AgreementError.class);
        }
    }

    @Test
    void aJunctionWithNoAgreementIsRefused() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThatThrownBy(() -> work.agreements().at("GRB", CN, MID_JANUARY))
                    .isInstanceOf(AgreementError.class);
        }
    }

    @Test
    void anAgreementBeforeItTakesEffectIsNotInForce() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThatThrownBy(() -> work.agreements().at("ESC", CN, LocalDate.of(2024, 1, 1)))
                    .isInstanceOf(AgreementError.class);
        }
    }

    @Test
    void aLaterAgreementTakesOver() {
        Database books = empty();
        try (UnitOfWork work = books.unitOfWork()) {
            work.agreements().sign(anAgreement());
            work.agreements()
                    .sign(anAgreement(CN, Set.of(HOPPER, BOX), LocalDate.of(2025, 7, 1), 60, Optional.empty()));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.agreements().at("ESC", CN, LocalDate.of(2025, 8, 1)).dailyCap())
                    .isEqualTo(60);
            assertThat(work.agreements().at("ESC", CN, LocalDate.of(2025, 1, 1)).dailyCap())
                    .isEqualTo(40);
        }
    }

    @Test
    void anExpiredAgreementIsSkipped() {
        Database books = empty();
        try (UnitOfWork work = books.unitOfWork()) {
            work.agreements()
                    .sign(anAgreement(CN, Set.of(HOPPER, BOX), JULY_2024, 40, Optional.of(LocalDate.of(2024, 12, 31))));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.agreements().at("ESC", CN, MID_JANUARY))
                    .isInstanceOf(AgreementError.class);
        }
    }

    @Test
    void theLiveAgreementsComeBack() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThat(work.agreements().inForceOn(MID_JANUARY))
                    .extracting(one -> one.road().toString())
                    .containsExactly("CN", "ELS");
        }
    }

    @Test
    void nothingIsLiveBeforeAnythingWasSigned() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThat(work.agreements().inForceOn(LocalDate.of(2024, 1, 1))).isEmpty();
        }
    }

    @Test
    void aCapOfNothingIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(aRow(0, SEVEN, NINETEEN));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aJunctionThatClosesFirstIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(aRow(40, NINETEEN, SEVEN));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void theRepositoryCountsWhatItSigned() {
        try (UnitOfWork work = signed().unitOfWork()) {
            assertThat(work.agreements().size()).isEqualTo(2);
            assertThat(work.agreements().all()).hasSize(2);
        }
    }
}

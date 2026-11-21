package io.github.rkaushikdev.drawbarline.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.demurrage.AverageAgreement;
import io.github.rkaushikdev.drawbarline.demurrage.Step;
import io.github.rkaushikdev.drawbarline.demurrage.Tariff;
import io.github.rkaushikdev.drawbarline.demurrage.TariffBook;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.models.TariffRow;
import io.github.rkaushikdev.drawbarline.persistence.models.TariffStepRow;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepositoryTariffsTest {

    private static final Shipper ELEVATOR = new Shipper("PWRGRN", "Powers Grain", true);
    private static final LocalDate JULY_2024 = LocalDate.of(2024, 7, 1);
    private static final Tariff FOUR_TWO =
            new Tariff("4200", JULY_2024, 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));
    private static final Tariff FOUR_THREE =
            new Tariff("4300", LocalDate.of(2025, 7, 1), 1, 1, List.of(new Step(2, 12000), new Step(0, 24000)));

    private static Database engine() {
        return TestDatabase.seeded(work -> work.customers().addShipper(ELEVATOR));
    }

    private static Database published() {
        return TestDatabase.seeded(work -> {
            work.customers().addShipper(ELEVATOR);
            work.tariffs().publish(FOUR_TWO);
            work.tariffs().publish(FOUR_THREE);
        });
    }

    @Test
    void theTariffTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("tariff", "tariff_step", "average_agreement");
    }

    @Test
    void aTariffRowReprNamesItsNumberAndDate() {
        assertThat(new TariffRow("4200", JULY_2024, 2, 1)).hasToString("<TariffRow 4200 from 2024-07-01>");
    }

    @Test
    void aStepRowReprNamesTheDayItRunsThrough() {
        TariffRow tariff = new TariffRow("4200", JULY_2024, 2, 1);
        TariffStepRow row = tariff.addStep(new TariffStepRow(0, 2, 1));
        assertThat(row).hasToString("<TariffStepRow 4200 through 2>");
    }

    @Test
    void aTariffGoesInAndComesBackWhole() {
        try (UnitOfWork work = published().unitOfWork()) {
            assertThat(work.tariffs().tariff("4200")).isEqualTo(FOUR_TWO);
        }
    }

    @Test
    void theStepsKeepTheirOrder() {
        Tariff found;
        try (UnitOfWork work = published().unitOfWork()) {
            found = work.tariffs().tariff("4200");
        }
        assertThat(found.steps()).containsExactly(new Step(2, 10000), new Step(0, 20000));
    }

    @Test
    void aStoredTariffPricesTheSameWay() {
        try (UnitOfWork work = published().unitOfWork()) {
            assertThat(work.tariffs().tariff("4200").charge(3)).isEqualTo(new Money(40000));
        }
    }

    @Test
    void publishingTheSameNumberTwiceIsRefused() {
        try (UnitOfWork work = published().unitOfWork()) {
            assertThatThrownBy(() -> work.tariffs().publish(FOUR_TWO)).isInstanceOf(TariffError.class);
        }
    }

    @Test
    void twoTariffsFromTheSameDayAreRefused() {
        Tariff other = new Tariff("4400", JULY_2024, 2, 1, List.of(new Step(0, 1)));
        try (UnitOfWork work = published().unitOfWork()) {
            assertThatThrownBy(() -> work.tariffs().publish(other)).isInstanceOf(TariffError.class);
        }
    }

    @Test
    void anUnknownTariffIsRefused() {
        try (UnitOfWork work = published().unitOfWork()) {
            assertThatThrownBy(() -> work.tariffs().tariff("9999")).isInstanceOf(TariffError.class);
        }
    }

    @Test
    void theTariffInForceIsTheLatestOnePublished() {
        try (UnitOfWork work = published().unitOfWork()) {
            assertThat(work.tariffs().inEffectOn(LocalDate.of(2025, 1, 14)).code())
                    .isEqualTo("4200");
            assertThat(work.tariffs().inEffectOn(LocalDate.of(2025, 7, 1)).code())
                    .isEqualTo("4300");
        }
    }

    @Test
    void aDayBeforeAnythingWasPublishedIsRefused() {
        try (UnitOfWork work = published().unitOfWork()) {
            assertThatThrownBy(() -> work.tariffs().inEffectOn(LocalDate.of(2024, 1, 1)))
                    .isInstanceOf(TariffError.class);
        }
    }

    @Test
    void theWholeBookLoadsBack() {
        TariffBook book;
        try (UnitOfWork work = published().unitOfWork()) {
            book = work.tariffs().book();
        }
        assertThat(book.size()).isEqualTo(2);
        assertThat(book.inEffectOn(LocalDate.of(2025, 8, 1)).code()).isEqualTo("4300");
    }

    @Test
    void aNegativeStepIsRefusedByTheTable() {
        try (UnitOfWork work = engine().unitOfWork()) {
            assertThatThrownBy(() -> {
                        TariffRow row = new TariffRow("4900", JULY_2024, 0, 0);
                        row.addStep(new TariffStepRow(0, 0, -1));
                        work.session().persist(row);
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void negativeFreeTimeIsRefusedByTheTable() {
        try (UnitOfWork work = engine().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.session().persist(new TariffRow("4900", JULY_2024, -1, 0));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void anAgreementGoesInAndComesBack() {
        AverageAgreement agreement = new AverageAgreement("PWRGRN", 12000, JULY_2024);
        Database books = engine();
        try (UnitOfWork work = books.unitOfWork()) {
            work.tariffs().sign(agreement);
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.tariffs().agreementFor("PWRGRN", LocalDate.of(2025, 1, 14)))
                    .isEqualTo(agreement);
        }
    }

    @Test
    void signingTheSameAgreementTwiceIsRefused() {
        AverageAgreement agreement = new AverageAgreement("PWRGRN", 12000, JULY_2024);
        try (UnitOfWork work = engine().unitOfWork()) {
            work.tariffs().sign(agreement);
            assertThatThrownBy(() -> work.tariffs().sign(agreement)).isInstanceOf(TariffError.class);
        }
    }

    @Test
    void anExpiredAgreementIsNotInForce() {
        Database books = engine();
        try (UnitOfWork work = books.unitOfWork()) {
            work.tariffs().sign(new AverageAgreement("PWRGRN", 12000, JULY_2024, LocalDate.of(2024, 12, 31)));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThatThrownBy(() -> work.tariffs().agreementFor("PWRGRN", LocalDate.of(2025, 1, 14)))
                    .isInstanceOf(TariffError.class);
        }
    }

    @Test
    void aSecondAgreementTakesOverWhenTheFirstExpires() {
        Database books = engine();
        try (UnitOfWork work = books.unitOfWork()) {
            work.tariffs().sign(new AverageAgreement("PWRGRN", 12000, JULY_2024, LocalDate.of(2024, 12, 31)));
            work.tariffs().sign(new AverageAgreement("PWRGRN", 13500, LocalDate.of(2025, 1, 1)));
            work.commit();
        }
        AverageAgreement found;
        try (UnitOfWork work = books.unitOfWork()) {
            found = work.tariffs().agreementFor("PWRGRN", LocalDate.of(2025, 1, 14));
        }
        assertThat(found.dailyCents()).isEqualTo(13500);
    }

    @Test
    void aShipperWithNoAgreementIsRefused() {
        try (UnitOfWork work = engine().unitOfWork()) {
            assertThatThrownBy(() -> work.tariffs().agreementFor("ESCPAP", LocalDate.of(2025, 1, 14)))
                    .isInstanceOf(TariffError.class);
        }
    }

    @Test
    void theAgreementsComeBackInShipperAndDateOrder() {
        Database books = engine();
        try (UnitOfWork work = books.unitOfWork()) {
            work.tariffs().sign(new AverageAgreement("PWRGRN", 13500, LocalDate.of(2025, 1, 1)));
            work.tariffs().sign(new AverageAgreement("PWRGRN", 12000, JULY_2024, LocalDate.of(2024, 12, 31)));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.tariffs().agreements())
                    .extracting(AverageAgreement::effective)
                    .containsExactly(JULY_2024, LocalDate.of(2025, 1, 1));
        }
    }
}

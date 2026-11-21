package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.persistence.CustomerRepository.daysFromText;
import static io.github.rkaushikdev.drawbarline.persistence.CustomerRepository.daysToText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.customers.CustomerError;
import io.github.rkaushikdev.drawbarline.customers.CustomerRegister;
import io.github.rkaushikdev.drawbarline.customers.Industry;
import io.github.rkaushikdev.drawbarline.customers.Shipper;
import io.github.rkaushikdev.drawbarline.persistence.models.IndustryRow;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RepositoryCustomersTest {

    private static final Shipper MILL = new Shipper("ESCPAP", "Escanaba Paper");
    private static final Shipper ELEVATOR = new Shipper("PWRGRN", "Powers Grain", true);

    private static Industry aTrack() {
        return aTrack("ESCPAP1", MILL, "ESC", 11);
    }

    private static Industry aTrack(String code, Shipper shipper, String station, int spots) {
        return new Industry(code, "mill house track", station, shipper, spots);
    }

    private static Database empty() {
        return TestDatabase.fresh();
    }

    private static Database stocked() {
        return TestDatabase.seeded(work -> {
            work.customers().addShipper(MILL);
            work.customers().addShipper(ELEVATOR);
            work.customers().addIndustry(aTrack());
            work.customers().addIndustry(aTrack("ESCPAP2", MILL, "ESC", 6));
            work.customers().addIndustry(aTrack("PWRGRN1", ELEVATOR, "PWR", 20));
        });
    }

    @Test
    void theCustomerTablesAreOnTheMetadata() {
        assertThat(Database.modelTables()).contains("shipper", "industry");
    }

    @Test
    void aRowReprNamesTheTrackAndStation() {
        IndustryRow row = new IndustryRow("ESCPAP1", "x", "ESC", "ESCPAP", 1, "mon");
        assertThat(row).hasToString("<IndustryRow ESCPAP1 at ESC>");
    }

    @Test
    void theSwitchingDaysGoOutInWeekOrder() {
        assertThat(daysToText(Set.of("fri", "mon", "wed"))).isEqualTo("mon,wed,fri");
    }

    @Test
    void theSwitchingDaysComeBackAsASet() {
        assertThat(daysFromText("mon,wed,fri")).isEqualTo(Set.of("mon", "wed", "fri"));
    }

    @Test
    void aDayThatIsNotADayIsRefusedOnTheWayBack() {
        assertThatThrownBy(() -> daysFromText("mon,funday")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aShipperGoesInAndComesBack() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.customers().shipper("PWRGRN").averageAgreement()).isTrue();
        }
    }

    @Test
    void theSameShipperTwiceIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.customers().addShipper(MILL)).isInstanceOf(CustomerError.class);
        }
    }

    @Test
    void anUnknownShipperIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.customers().shipper("NOBODY")).isInstanceOf(CustomerError.class);
        }
    }

    @Test
    void anIndustryGoesInAndComesBackWhole() {
        Industry found;
        try (UnitOfWork work = stocked().unitOfWork()) {
            found = work.customers().industry("ESCPAP1");
        }
        assertThat(found).isEqualTo(aTrack());
    }

    @Test
    void anIndustryKeepsItsSwitchingDays() {
        Database books = stocked();
        try (UnitOfWork work = books.unitOfWork()) {
            work.customers()
                    .addIndustry(new Industry("ESCPAP3", "mill house track", "ESC", MILL, 11, Set.of("tue", "thu")));
            work.commit();
        }
        try (UnitOfWork work = books.unitOfWork()) {
            assertThat(work.customers().industry("ESCPAP3").switched()).isEqualTo(Set.of("tue", "thu"));
        }
    }

    @Test
    void anIndustryWhoseShipperIsNotFiledIsRefused() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> work.customers().addIndustry(aTrack())).isInstanceOf(CustomerError.class);
        }
    }

    @Test
    void theSameIndustryTwiceIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.customers().addIndustry(aTrack())).isInstanceOf(CustomerError.class);
        }
    }

    @Test
    void anUnknownIndustryIsRefused() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThatThrownBy(() -> work.customers().industry("NOWHERE")).isInstanceOf(CustomerError.class);
        }
    }

    @Test
    void aTrackWithNoSpotsIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.customers().addShipper(MILL);
                        work.session().persist(new IndustryRow("X1", "x", "ESC", "ESCPAP", 0, "mon"));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void aTrackNobodySwitchesIsRefusedByTheTable() {
        try (UnitOfWork work = empty().unitOfWork()) {
            assertThatThrownBy(() -> {
                        work.customers().addShipper(MILL);
                        work.session().persist(new IndustryRow("X1", "x", "ESC", "ESCPAP", 1, ""));
                        work.commit();
                    })
                    .isInstanceOf(PersistenceException.class);
        }
    }

    @Test
    void industriesComeBackByStation() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.customers().atStation("ESC"))
                    .extracting(Industry::code)
                    .containsExactly("ESCPAP1", "ESCPAP2");
        }
    }

    @Test
    void shippersComeBackInCodeOrder() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.customers().shippers()).extracting(Shipper::code).containsExactly("ESCPAP", "PWRGRN");
        }
    }

    @Test
    void theWholeRegisterLoadsBack() {
        CustomerRegister register;
        try (UnitOfWork work = stocked().unitOfWork()) {
            register = work.customers().register();
        }
        assertThat(register.size()).isEqualTo(3);
        assertThat(register.industry("PWRGRN1").shipper().code()).isEqualTo("PWRGRN");
        assertThat(register.onAverageAgreement()).extracting(Shipper::code).containsExactly("PWRGRN");
    }

    @Test
    void aLoadedRegisterStillKnowsTheSwitchingDays() {
        CustomerRegister register;
        try (UnitOfWork work = stocked().unitOfWork()) {
            register = work.customers().register();
        }
        assertThat(register.industry("ESCPAP1").switchedOn(LocalDate.of(2025, 1, 14)))
                .isTrue();
        assertThat(register.industry("ESCPAP1").switchedOn(LocalDate.of(2025, 1, 18)))
                .isFalse();
    }

    @Test
    void theRepositoryCountsItsIndustries() {
        try (UnitOfWork work = stocked().unitOfWork()) {
            assertThat(work.customers().size()).isEqualTo(3);
        }
    }
}

package io.github.rkaushikdev.drawbarline.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomersTest {

    static final Shipper MILL = new Shipper("ESCPAP", "Escanaba Paper");
    static final Shipper ELEVATOR = new Shipper("PWRGRN", "Powers Grain", true);

    static Industry anIndustry() {
        return new Industry("ESCPAP1", "mill house track", "ESC", MILL, 11);
    }

    static Industry anIndustry(int spots) {
        return new Industry("ESCPAP1", "mill house track", "ESC", MILL, spots);
    }

    static Industry anIndustry(Set<String> switched) {
        return new Industry("ESCPAP1", "mill house track", "ESC", MILL, 11, switched);
    }

    static CustomerRegister aRegister() {
        CustomerRegister register = new CustomerRegister();
        register.addShipper(MILL);
        register.addShipper(ELEVATOR);
        register.addIndustry(anIndustry());
        register.addIndustry(new Industry("ESCPAP2", "mill north", "ESC", MILL, 6));
        register.addIndustry(new Industry("PWRGRN1", "mill house track", "PWR", ELEVATOR, 20));
        return register;
    }

    @Test
    void aShipperPrintsItsCodeAndName() {
        assertThat(MILL).hasToString("ESCPAP Escanaba Paper");
    }

    @Test
    void aShipperCodeIsUpperCase() {
        assertThatThrownBy(() -> new Shipper("escpap", "Escanaba Paper")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aShipperNeedsAName() {
        assertThatThrownBy(() -> new Shipper("ESCPAP", " ")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void anIndustryPrintsItsNameAndStation() {
        assertThat(anIndustry()).hasToString("ESCPAP1 mill house track at ESC");
    }

    @Test
    void anIndustryHoldsAtLeastOneCar() {
        assertThatThrownBy(() -> anIndustry(0)).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void anIndustryCodeIsUpperCase() {
        assertThatThrownBy(() -> new Industry("escpap1", "mill house track", "ESC", MILL, 11))
                .isInstanceOf(ConfigurationError.class);
    }

    @Test
    void anIndustryIsSwitchedWeekdaysByDefault() {
        Industry track = anIndustry();
        assertThat(track.switchedOn(LocalDate.of(2025, 1, 14))).isTrue();
        assertThat(track.switchedOn(LocalDate.of(2025, 1, 18))).isFalse();
    }

    @Test
    void anIndustryCanBeSwitchedOnASaturday() {
        Industry track = anIndustry(Set.of("mon", "wed", "sat"));
        assertThat(track.switchedOn(LocalDate.of(2025, 1, 18))).isTrue();
        assertThat(track.switchedOn(LocalDate.of(2025, 1, 14))).isFalse();
    }

    @Test
    void aDayThatIsNotADayIsRefused() {
        assertThatThrownBy(() -> anIndustry(Set.of("mon", "funday")))
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("days=['funday']");
    }

    @Test
    void anIndustryNobodySwitchesIsRefused() {
        assertThatThrownBy(() -> anIndustry(Set.of())).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aTrackWithRoomIsNotFull() {
        assertThat(anIndustry(11).full(10)).isFalse();
    }

    @Test
    void aTrackAtItsSpotCapacityIsFull() {
        assertThat(anIndustry(11).full(11)).isTrue();
    }

    @Test
    void aTrackOverItsSpotCapacityIsFull() {
        assertThat(anIndustry(11).full(14)).isTrue();
    }

    @Test
    void aShipperGoesOnAndComesBack() {
        assertThat(aRegister().shipper("ESCPAP")).isEqualTo(MILL);
    }

    @Test
    void theSameShipperTwiceIsRefused() {
        CustomerRegister register = aRegister();
        assertThatThrownBy(() -> register.addShipper(MILL)).isInstanceOf(CustomerError.class);
    }

    @Test
    void anUnknownShipperIsRefused() {
        assertThatThrownBy(() -> aRegister().shipper("NOBODY")).isInstanceOf(CustomerError.class);
    }

    @Test
    void anIndustryGoesOnAndComesBack() {
        assertThat(aRegister().industry("PWRGRN1").station()).isEqualTo("PWR");
    }

    @Test
    void theSameIndustryTwiceIsRefused() {
        CustomerRegister register = aRegister();
        assertThatThrownBy(() -> register.addIndustry(anIndustry())).isInstanceOf(CustomerError.class);
    }

    @Test
    void anIndustryWhoseShipperIsNotFiledIsRefused() {
        CustomerRegister register = new CustomerRegister();
        assertThatThrownBy(() -> register.addIndustry(anIndustry())).isInstanceOf(CustomerError.class);
    }

    @Test
    void anUnknownIndustryIsRefused() {
        assertThatThrownBy(() -> aRegister().industry("NOWHERE")).isInstanceOf(CustomerError.class);
    }

    @Test
    void industriesComeBackByStation() {
        assertThat(aRegister().atStation("ESC")).extracting(Industry::code).containsExactly("ESCPAP1", "ESCPAP2");
    }

    @Test
    void aStationWeServeNobodyAtListsNothing() {
        assertThat(aRegister().atStation("WAL")).isEmpty();
    }

    @Test
    void industriesComeBackByShipper() {
        assertThat(aRegister().forShipper("PWRGRN")).extracting(Industry::code).containsExactly("PWRGRN1");
    }

    @Test
    void askingForAnUnknownShippersTrackIsRefused() {
        assertThatThrownBy(() -> aRegister().forShipper("NOBODY")).isInstanceOf(CustomerError.class);
    }

    @Test
    void shippersOnAnAverageAgreementComeBackTogether() {
        assertThat(aRegister().onAverageAgreement()).extracting(Shipper::code).containsExactly("PWRGRN");
    }

    @Test
    void theRegisterIteratesIndustriesInCodeOrder() {
        assertThat(aRegister()).extracting(Industry::code).containsExactly("ESCPAP1", "ESCPAP2", "PWRGRN1");
    }

    @Test
    void theRegisterCountsItsIndustries() {
        assertThat(aRegister().size()).isEqualTo(3);
    }
}

package io.github.rkaushikdev.drawbarline.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class OwnershipTest {

    static final ReportingMark DBL = new ReportingMark("DBL");
    static final ReportingMark CN = new ReportingMark("CN");
    static final ReportingMark ELS = new ReportingMark("ELS");

    static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Test
    void aLeasePrintsItsTerm() {
        Lease term = new Lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(term).hasToString("CN from 2025-01-01 to 2025-06-30");
    }

    @Test
    void anOpenLeasePrintsAsOpen() {
        assertThat(new Lease(CN, date(2025, 1, 1))).hasToString("CN from 2025-01-01 to open");
    }

    @Test
    void aLeaseThatEndsBeforeItBeginsIsRefused() {
        assertThatThrownBy(() -> new Lease(CN, date(2025, 6, 30), date(2025, 1, 1)))
                .isInstanceOf(OwnershipError.class);
    }

    @Test
    void aLeaseCoversBothOfItsEndDays() {
        Lease term = new Lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(term.covers(date(2025, 1, 1))).isTrue();
        assertThat(term.covers(date(2025, 6, 30))).isTrue();
    }

    @Test
    void aLeaseDoesNotCoverTheDayAfterItEnds() {
        Lease term = new Lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(term.covers(date(2025, 7, 1))).isFalse();
    }

    @Test
    void anOpenLeaseCoversEverythingAfterItBegins() {
        Lease term = new Lease(CN, date(2025, 1, 1));
        assertThat(term.covers(date(2099, 1, 1))).isTrue();
        assertThat(term.covers(date(2024, 12, 31))).isFalse();
    }

    @Test
    void anUnleasedCarPaysItsOwner() {
        assertThat(new Ownership(DBL).holderOn(date(2025, 3, 1))).isEqualTo(DBL);
    }

    @Test
    void aLeasedCarPaysTheLessee() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(owned.holderOn(date(2025, 3, 1))).isEqualTo(CN);
    }

    @Test
    void aCarPaysItsOwnerAgainOnceTheLeaseRunsOut() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(owned.holderOn(date(2025, 7, 1))).isEqualTo(DBL);
    }

    @Test
    void anOwnerDoesNotLeaseFromItself() {
        assertThatThrownBy(() -> new Ownership(DBL).lease(DBL, date(2025, 1, 1)))
                .isInstanceOf(OwnershipError.class);
    }

    @Test
    void twoTermsThatDoNotTouchBothStand() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        owned.lease(ELS, date(2025, 7, 1), date(2025, 12, 31));
        assertThat(owned.holderOn(date(2025, 8, 1))).isEqualTo(ELS);
        assertThat(owned.terms()).hasSize(2);
    }

    @Test
    void twoTermsThatOverlapAreRefused() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThatThrownBy(() -> owned.lease(ELS, date(2025, 6, 30), date(2025, 12, 31)))
                .isInstanceOf(OwnershipError.class);
    }

    @Test
    void aTermInsideAnOpenOneIsRefused() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1));
        assertThatThrownBy(() -> owned.lease(ELS, date(2026, 1, 1), date(2026, 6, 30)))
                .isInstanceOf(OwnershipError.class);
    }

    @Test
    void aTermBeforeAnOpenOneStillStands() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 7, 1));
        owned.lease(ELS, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(owned.holderOn(date(2025, 3, 1))).isEqualTo(ELS);
        assertThat(owned.holderOn(date(2026, 3, 1))).isEqualTo(CN);
    }

    @Test
    void termsComeBackInTheOrderTheyRun() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 7, 1), date(2025, 12, 31));
        owned.lease(ELS, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(owned.terms()).extracting(Lease::lessee).containsExactly(ELS, CN);
    }

    @Test
    void closingTheOpenTermEndsIt() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1));
        owned.endOpenTerm(date(2025, 3, 31));
        assertThat(owned.holderOn(date(2025, 4, 1))).isEqualTo(DBL);
    }

    @Test
    void closingWhenNothingIsOpenIsRefused() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThatThrownBy(() -> owned.endOpenTerm(date(2025, 3, 31))).isInstanceOf(OwnershipError.class);
    }

    @Test
    void aClosedTermLeavesRoomForTheNext() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1));
        owned.endOpenTerm(date(2025, 3, 31));
        owned.lease(ELS, date(2025, 4, 1));
        assertThat(owned.holderOn(date(2025, 5, 1))).isEqualTo(ELS);
    }

    @Test
    void aCarKnowsWhetherItIsOutOnLease() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(owned.leasedOn(date(2025, 3, 1))).isTrue();
        assertThat(owned.leasedOn(date(2025, 8, 1))).isFalse();
    }

    @Test
    void theTermOnADayComesBackWhole() {
        Ownership owned = new Ownership(DBL);
        owned.lease(CN, date(2025, 1, 1), date(2025, 6, 30));
        assertThat(owned.termOn(date(2025, 3, 1)).orElseThrow().lessee()).isEqualTo(CN);
        assertThat(owned.termOn(date(2025, 8, 1))).isEmpty();
    }
}

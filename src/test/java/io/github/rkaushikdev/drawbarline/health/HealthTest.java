package io.github.rkaushikdev.drawbarline.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.Numbering;
import io.github.rkaushikdev.drawbarline.support.Office;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HealthTest {

    private static final LocalDate DAY = LocalDate.of(2025, 1, 14);

    private static List<Finding> findingsOf(Database database) {
        return Health.examine(database, DAY);
    }

    private static Finding named(List<Finding> findings, String check) {
        return findings.stream()
                .filter(one -> one.check().equals(check))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void aLevelSaysWhetherItStopsAClose() {
        assertThat(Level.BAD.stopsAClose()).isTrue();
        assertThat(Level.WARN.stopsAClose()).isFalse();
        assertThat(Level.GOOD.stopsAClose()).isFalse();
    }

    @Test
    void aDatabaseWithNoSchemaIsNotReady() {
        List<Finding> found;
        try (Database bare = Database.inMemory()) {
            found = findingsOf(bare);
        }
        assertThat(named(found, "schema").level()).isEqualTo(Level.BAD);
        assertThat(Health.ready(found)).isFalse();
    }

    @Test
    void aFreshDatabaseHasItsSchemaAndCounters() {
        List<Finding> found = findingsOf(Office.empty());
        assertThat(named(found, "schema").level()).isEqualTo(Level.GOOD);
        assertThat(named(found, "numbering").level()).isEqualTo(Level.GOOD);
    }

    @Test
    void aFreshDatabaseHasNoRatesAndIsNotReady() {
        List<Finding> found = findingsOf(Office.empty());
        assertThat(named(found, "car hire rates").level()).isEqualTo(Level.BAD);
        assertThat(Health.ready(found)).isFalse();
    }

    @Test
    void aFreshDatabaseHasNoTariff() {
        assertThat(named(findingsOf(Office.empty()), "demurrage tariff").level())
                .isEqualTo(Level.BAD);
    }

    @Test
    void aLoadedRailroadIsReady() {
        List<Finding> found = findingsOf(Office.loaded());
        assertThat(named(found, "car hire rates").level()).isEqualTo(Level.GOOD);
        assertThat(named(found, "demurrage tariff").level()).isEqualTo(Level.GOOD);
        assertThat(Health.ready(found)).isTrue();
    }

    @Test
    void aLoadedRailroadHasTrackAndCars() {
        List<Finding> found = findingsOf(Office.loaded());
        assertThat(named(found, "industry track").level()).isEqualTo(Level.GOOD);
        assertThat(named(found, "fleet").level()).isEqualTo(Level.GOOD);
    }

    @Test
    void noAgreementsIsAWarningAndNotAStop() {
        List<Finding> found = findingsOf(Office.loaded());
        assertThat(named(found, "agreements").level()).isEqualTo(Level.WARN);
        assertThat(Health.ready(found)).isTrue();
    }

    @Test
    void everyCheckIsReportedOnce() {
        List<Finding> found = findingsOf(Office.loaded());
        assertThat(found.stream().map(Finding::check).collect(Collectors.toSet()))
                .hasSameSizeAs(found);
    }

    @Test
    void aFindingPrintsItsLevelAndCheck() {
        assertThat(named(findingsOf(Office.loaded()), "schema").toString()).startsWith("ok    schema");
    }

    @Test
    void aBadFindingCarriesARemedy() {
        Finding found = named(findingsOf(Office.empty()), "car hire rates");
        assertThat(found.remedy()).isNotEmpty();
        assertThat(found.toString()).contains(found.remedy());
    }

    @Test
    void aGoodFindingDoesNotShoutARemedy() {
        assertThat(named(findingsOf(Office.loaded()), "schema").toString()).doesNotContain("(");
    }

    @Test
    void everySeriesIsChecked() {
        Finding found = named(findingsOf(Office.empty()), "numbering");
        assertThat(found.detail()).isEqualTo(Numbering.SERIES.size() + " series open");
    }
}

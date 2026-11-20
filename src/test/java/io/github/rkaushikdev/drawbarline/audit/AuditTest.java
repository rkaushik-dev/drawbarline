package io.github.rkaushikdev.drawbarline.audit;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.clock.ClockError;
import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuditTest {

    static final Instant WHEN = utc(2025, 2, 10, 14, 30);

    static Entry anEntry() {
        return new Entry(WHEN, "rjs", Action.CLOSED_PERIOD, "2025-01");
    }

    static Entry anEntry(String actor, String subject, String detail) {
        return new Entry(WHEN, actor, Action.CLOSED_PERIOD, subject, detail);
    }

    @Test
    void anEntryPrintsWhoDidWhat() {
        assertThat(anEntry()).hasToString("2025-02-10 14:30Z rjs closed a period 2025-01");
    }

    @Test
    void anEntryPrintsItsDetailWhenThereIsOne() {
        Entry found = anEntry("rjs", "2025-01", "cn sent a corrected sheet");
        assertThat(found.toString()).endsWith("(cn sent a corrected sheet)");
    }

    @Test
    void everyActionHasReadableText() {
        assertThat(Action.values()).allSatisfy(one -> assertThat(one.text()).isNotEmpty());
    }

    @Test
    void anEntryWithNoActorIsRefused() {
        assertThatThrownBy(() -> anEntry("  ", "2025-01", "")).isInstanceOf(AuditError.class);
    }

    @Test
    void aSignatureRatherThanInitialsIsRefused() {
        assertThatThrownBy(() -> anEntry("x".repeat(33), "2025-01", "")).isInstanceOf(AuditError.class);
    }

    @Test
    void anEntryAboutNothingIsRefused() {
        assertThatThrownBy(() -> anEntry("rjs", "", "")).isInstanceOf(AuditError.class);
    }

    @Test
    void aDetailLongerThanTheFieldIsRefused() {
        assertThatThrownBy(() -> anEntry("rjs", "2025-01", "x".repeat(201))).isInstanceOf(AuditError.class);
    }

    @Test
    void aNaiveMomentIsRefused() {
        // An entry's moment is an instant, so a wall clock reading cannot stand in for it; one read
        // from outside without its zone is refused where it arrives.
        assertThat(Entry.class.getRecordComponents())
                .filteredOn(component -> component.getName().equals("at"))
                .extracting(RecordComponent::getType)
                .containsExactly(Instant.class);
        assertThatThrownBy(() -> Clock.requireAware(LocalDateTime.of(2025, 2, 10, 14, 30), "at"))
                .isInstanceOf(ClockError.class);
    }

    @Test
    void theActorIsTrimmed() {
        assertThat(anEntry("  rjs ", "2025-01", "").actor()).isEqualTo("rjs");
    }

    @Test
    void aTrailRecordsInOrder() {
        AuditTrail trail = new AuditTrail();
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", WHEN);
        trail.record("mkb", Action.RETIRED_CAR, "DBL 104207", WHEN.minus(Duration.ofDays(1)));
        assertThat(trail.entries()).extracting(Entry::actor).containsExactly("mkb", "rjs");
    }

    @Test
    void aTrailDefaultsToNow() {
        AuditTrail trail = new AuditTrail();
        Instant before = Clock.now();
        Entry found = trail.record("rjs", Action.CLOSED_PERIOD, "2025-01");
        assertThat(found.at()).isBetween(before, Instant.now());
    }

    @Test
    void entriesComeBackByActor() {
        AuditTrail trail = new AuditTrail();
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", WHEN);
        trail.record("mkb", Action.RETIRED_CAR, "DBL 104207", WHEN);
        assertThat(trail.byActor("rjs")).extracting(Entry::subject).containsExactly("2025-01");
    }

    @Test
    void entriesComeBackByAction() {
        AuditTrail trail = new AuditTrail();
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", WHEN);
        trail.record("rjs", Action.REOPENED_PERIOD, "2025-01", WHEN);
        assertThat(trail.ofAction(Action.REOPENED_PERIOD)).hasSize(1);
    }

    @Test
    void entriesComeBackAboutOneSubject() {
        AuditTrail trail = new AuditTrail();
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", WHEN);
        trail.record("rjs", Action.REOPENED_PERIOD, "2025-01", WHEN);
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-02", WHEN);
        assertThat(trail.about("2025-01")).hasSize(2);
    }

    @Test
    void entriesComeBackSinceAMoment() {
        AuditTrail trail = new AuditTrail();
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", WHEN.minus(Duration.ofDays(5)));
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-02", WHEN);
        assertThat(trail.since(WHEN.minus(Duration.ofDays(1)))).hasSize(1);
    }

    @Test
    void aNaiveSinceIsRefused() throws NoSuchMethodException {
        // The cut is an instant, so a wall clock reading cannot be passed as one; one read from
        // outside without its zone is refused where it arrives.
        assertThat(AuditTrail.class.getMethod("since", Instant.class).getParameterTypes())
                .containsExactly(Instant.class);
        assertThatThrownBy(() -> Clock.requireAware(LocalDateTime.of(2025, 2, 1, 0, 0), "since"))
                .isInstanceOf(ClockError.class)
                .hasMessageContaining("what='since'");
    }

    @Test
    void theActorsComeBackSorted() {
        AuditTrail trail = new AuditTrail();
        trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", WHEN);
        trail.record("mkb", Action.CLOSED_PERIOD, "2025-02", WHEN);
        assertThat(trail.actors()).containsExactly("mkb", "rjs");
    }

    @Test
    void anEmptyTrailHoldsNothing() {
        assertThat(new AuditTrail().size()).isZero();
        assertThat(new AuditTrail().actors()).isEmpty();
    }
}

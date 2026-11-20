package io.github.rkaushikdev.drawbarline.events;

import static io.github.rkaushikdev.drawbarline.events.EventKind.kindNamed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.EventError;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EventKindsTest {

    static Set<EventKind> kindsThat(Predicate<EventKind> test) {
        return Arrays.stream(EventKind.values())
                .filter(test)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EventKind.class)));
    }

    @Test
    void everyKindCarriesReadableText() {
        assertThat(EventKind.values())
                .allSatisfy(kind -> assertThat(kind.text()).isNotEmpty());
    }

    @Test
    void aKindPrintsAsItsLowerCaseName() {
        assertThat(EventKind.BAD_ORDERED).hasToString("bad_ordered");
    }

    @Test
    void kindTextsAreAllDifferent() {
        assertThat(Arrays.stream(EventKind.values()).map(EventKind::text).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void aKindReadsBackOffItsName() {
        assertThat(kindNamed("received")).isSameAs(EventKind.RECEIVED);
    }

    @Test
    void aKindReadsBackWithSurroundingSpace() {
        assertThat(kindNamed("  BAD_ORDERED ")).isSameAs(EventKind.BAD_ORDERED);
    }

    @Test
    void aNameNoKindCarriesIsRefused() {
        assertThatThrownBy(() -> kindNamed("shunted")).isInstanceOf(EventError.class);
    }

    @Test
    void onlyInterchangeNamesARoad() {
        assertThat(kindsThat(EventKind::needsRoad)).isEqualTo(EnumSet.of(EventKind.RECEIVED, EventKind.DELIVERED));
    }

    @Test
    void theIndustryEventsNameATrack() {
        assertThat(kindsThat(EventKind::needsIndustry))
                .isEqualTo(EnumSet.of(EventKind.PLACED, EventKind.CONSTRUCTIVE, EventKind.RELEASED, EventKind.PULLED));
    }

    @Test
    void onlyAMovementCarriesADistance() {
        assertThat(kindsThat(EventKind::needsDistance)).isEqualTo(EnumSet.of(EventKind.MOVED));
    }

    @Test
    void carHireStartsWhenWeTakeTheCar() {
        assertThat(EventKind.RECEIVED.startsCarHire()).isTrue();
        assertThat(EventKind.DELIVERED.startsCarHire()).isFalse();
    }

    @Test
    void carHireStopsWhenWeHandItOn() {
        assertThat(EventKind.DELIVERED.endsCarHire()).isTrue();
        assertThat(EventKind.RECEIVED.endsCarHire()).isFalse();
    }

    @Test
    void exactlyOneKindStartsCarHire() {
        assertThat(kindsThat(EventKind::startsCarHire)).hasSize(1);
    }

    @Test
    void exactlyOneKindEndsCarHire() {
        assertThat(kindsThat(EventKind::endsCarHire)).hasSize(1);
    }

    @Test
    void bothPlacementsStartDemurrage() {
        assertThat(EventKind.PLACED.startsDemurrage()).isTrue();
        assertThat(EventKind.CONSTRUCTIVE.startsDemurrage()).isTrue();
    }

    @Test
    void aPullDoesNotStartDemurrage() {
        assertThat(EventKind.PULLED.startsDemurrage()).isFalse();
    }

    @Test
    void onlyAReleaseEndsDemurrage() {
        assertThat(kindsThat(EventKind::endsDemurrage)).isEqualTo(EnumSet.of(EventKind.RELEASED));
    }

    @Test
    void thePlacementsAreTheTwoThatStartDemurrage() {
        assertThat(kindsThat(EventKind::isPlacement)).isEqualTo(kindsThat(EventKind::startsDemurrage));
    }

    @Test
    void interchangeEventsAreTheOnesThatNameARoad() {
        assertThat(kindsThat(EventKind::isInterchange)).isEqualTo(kindsThat(EventKind::needsRoad));
    }

    @Test
    void aHoldNeitherStartsNorStopsAnythingBillable() {
        EventKind held = EventKind.HELD;
        assertThat(held.startsCarHire()).isFalse();
        assertThat(held.endsCarHire()).isFalse();
        assertThat(held.startsDemurrage()).isFalse();
        assertThat(held.endsDemurrage()).isFalse();
    }

    @Test
    void theListOfKindsIsShortEnoughToRead() {
        assertThat(EventKind.values()).hasSize(13);
    }
}

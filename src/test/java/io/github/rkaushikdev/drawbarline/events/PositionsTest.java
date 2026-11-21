package io.github.rkaushikdev.drawbarline.events;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.events.Positions.movable;
import static io.github.rkaushikdev.drawbarline.events.Positions.onLine;
import static io.github.rkaushikdev.drawbarline.events.Positions.onTrack;
import static io.github.rkaushikdev.drawbarline.events.Positions.placed;
import static io.github.rkaushikdev.drawbarline.events.Positions.positions;
import static io.github.rkaushikdev.drawbarline.events.Positions.stateOf;
import static io.github.rkaushikdev.drawbarline.events.Positions.unplaced;
import static io.github.rkaushikdev.drawbarline.support.Moves.badOrdered;
import static io.github.rkaushikdev.drawbarline.support.Moves.delivered;
import static io.github.rkaushikdev.drawbarline.support.Moves.held;
import static io.github.rkaushikdev.drawbarline.support.Moves.pulled;
import static io.github.rkaushikdev.drawbarline.support.Moves.received;
import static io.github.rkaushikdev.drawbarline.support.Moves.released;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.reports.InterchangeReport;
import io.github.rkaushikdev.drawbarline.reports.OnHand;
import io.github.rkaushikdev.drawbarline.support.Moves;
import io.github.rkaushikdev.drawbarline.switching.Switching;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PositionsTest {

    static final Instant NOW = utc(2025, 1, 20, 12);

    static EventLog aLog() {
        EventLog log = new EventLog();
        received(log, "CN 400100", 6);
        received(log, "CN 400101", 6);
        received(log, "CN 400102", 6);
        Moves.placed(log, "CN 400100", 7);
        delivered(log, "CN 400102", 16);
        return log;
    }

    @Test
    void aWalkCoversEveryCarInTheLog() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(found).hasSize(3);
    }

    @Test
    void aCarStillHereIsOnLine() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(onLine(found)).extracting(CarMark::toString).containsExactly("CN 400100", "CN 400101");
    }

    @Test
    void aDeliveredCarIsNotOnLine() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(onLine(found)).doesNotContain(CarMark.parse("CN 400102"));
    }

    @Test
    void aPlacedCarShowsOnItsTrack() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(onTrack(found, "ESCPAP1")).extracting(CarMark::toString).containsExactly("CN 400100");
    }

    @Test
    void aTrackWithNothingOnItIsEmpty() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(onTrack(found, "PWRGRN1")).isEmpty();
    }

    @Test
    void thePlacedCarsComeBackTogether() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(placed(found)).extracting(CarMark::toString).containsExactly("CN 400100");
    }

    @Test
    void theUnplacedCarsAreTheRestOfWhatIsHere() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(unplaced(found)).extracting(CarMark::toString).containsExactly("CN 400101");
    }

    @Test
    void aBadOrderedCarIsNotMovable() {
        EventLog log = aLog();
        badOrdered(log, "CN 400101", 8);
        Map<CarMark, CarState> found = positions(log, NOW);
        assertThat(movable(found)).doesNotContain(CarMark.parse("CN 400101"));
    }

    @Test
    void aHeldCarIsNotMovable() {
        EventLog log = aLog();
        held(log, "CN 400101", 8);
        Map<CarMark, CarState> found = positions(log, NOW);
        assertThat(movable(found)).doesNotContain(CarMark.parse("CN 400101"));
    }

    @Test
    void oneCarStateReadsBack() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(stateOf(found, CarMark.parse("CN 400100")).industry()).contains("ESCPAP1");
    }

    @Test
    void aCarNobodyHasRecordedReadsAsOffLine() {
        Map<CarMark, CarState> found = positions(aLog(), NOW);
        assertThat(stateOf(found, CarMark.parse("WC 000001")).onLine()).isFalse();
    }

    @Test
    void aWalkToAnEarlierMomentSeesLess() {
        Map<CarMark, CarState> found = positions(aLog(), utc(2025, 1, 6, 18));
        assertThat(placed(found)).isEmpty();
    }

    @Test
    void aWalkBeforeAnythingHappenedSeesNothingOnLine() {
        assertThat(onLine(positions(aLog(), utc(2025, 1, 1)))).isEmpty();
    }

    @Test
    void anEmptyLogWalksToNothing() {
        assertThat(positions(new EventLog(), NOW)).isEmpty();
    }

    @Test
    void theWalkAgreesWithTheSwitchList() {
        EventLog log = aLog();
        assertThat(Switching.carsOnTrack(log, "ESCPAP1", NOW)).isEqualTo(onTrack(positions(log, NOW), "ESCPAP1"));
    }

    @Test
    void theWalkAgreesWithTheOnHandReport() {
        EventLog log = aLog();
        assertThat(InterchangeReport.onHand(log, NOW).stream().map(OnHand::car).toList())
                .isEqualTo(onLine(positions(log, NOW)));
    }

    @Test
    void aReleasedCarIsStillPlacedUntilItIsPulled() {
        EventLog log = aLog();
        released(log, "CN 400100", 10);
        assertThat(placed(positions(log, NOW))).containsExactly(CarMark.parse("CN 400100"));
    }

    @Test
    void aPulledCarLeavesTheTrack() {
        EventLog log = aLog();
        released(log, "CN 400100", 10);
        pulled(log, "CN 400100", 11);
        assertThat(placed(positions(log, NOW))).isEmpty();
    }
}

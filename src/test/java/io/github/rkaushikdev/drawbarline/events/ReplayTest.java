package io.github.rkaushikdev.drawbarline.events;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.events.Replay.replay;
import static io.github.rkaushikdev.drawbarline.events.Replay.stateOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ReplayTest {

    static final CarMark CAR = CarMark.parse("DBL 104207");
    static final ReportingMark CN = new ReportingMark("CN");

    static CarStream streamOf(Event... events) {
        CarStream stream = new CarStream(CAR);
        for (Event event : events) {
            stream.record(event);
        }
        return stream;
    }

    static Event received(int hour) {
        return received(hour, 41207);
    }

    static Event received(int hour, Integer waybill) {
        return Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, 14, hour), "ESC")
                .road(CN)
                .waybill(waybill)
                .build();
    }

    static Event delivered(int day) {
        return Event.builder(EventKind.DELIVERED, CAR, utc(2025, 1, day, 9), "MRN")
                .road(CN)
                .build();
    }

    static Event moved(int day, String station, int tenths) {
        return Event.builder(EventKind.MOVED, CAR, utc(2025, 1, day, 6), station)
                .tenths(tenths)
                .build();
    }

    static Event placed(int day) {
        return placed(day, EventKind.PLACED);
    }

    static Event placed(int day, EventKind kind) {
        return Event.builder(kind, CAR, utc(2025, 1, day, 8), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    static Event released(int day) {
        return Event.builder(EventKind.RELEASED, CAR, utc(2025, 1, day, 15), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    static Event pulled(int day) {
        return Event.builder(EventKind.PULLED, CAR, utc(2025, 1, day, 17), "ESC")
                .industry("ESCPAP1")
                .build();
    }

    static Event simple(int day, EventKind kind) {
        return Event.of(kind, CAR, utc(2025, 1, day, 10), "ESC");
    }

    @Test
    void aCarNothingHasHappenedToIsOffLine() {
        CarState state = replay(new CarStream(CAR));
        assertThat(state.onLine()).isFalse();
        assertThat(state.applied()).isZero();
        assertThat(state).hasToString("DBL 104207 off line");
    }

    @Test
    void aReceivedCarIsOnOurRail() {
        CarState state = replay(streamOf(received(14)));
        assertThat(state.onLine()).isTrue();
        assertThat(state.station()).contains("ESC");
        assertThat(state.receivedAt()).contains(utc(2025, 1, 14, 14));
        assertThat(state.waybill()).contains(41207);
    }

    @Test
    void aCarReceivedTwiceIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), received(16)))).isInstanceOf(EventError.class);
    }

    @Test
    void anEventBeforeTheCarArrivesIsRefused() {
        EventError caught =
                catchThrowableOfType(EventError.class, () -> replay(streamOf(simple(15, EventKind.NOTIFIED))));
        assertThat(caught.message()).contains("not on our rail");
    }

    @Test
    void aDeliveredCarGoesOffLine() {
        CarState state = replay(streamOf(received(14), delivered(16)));
        assertThat(state.onLine()).isFalse();
        assertThat(state.waybill()).isEmpty();
        assertThat(state.station()).contains("MRN");
    }

    @Test
    void aCarDeliveredTwiceIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), delivered(16), delivered(17))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aPlacedCarCarriesItsTrack() {
        CarState state = replay(streamOf(received(14), placed(15)));
        assertThat(state.industry()).contains("ESCPAP1");
        assertThat(state.placed()).isTrue();
        assertThat(state).hasToString("DBL 104207 at ESCPAP1");
    }

    @Test
    void aConstructivePlacementPlacesTheCarToo() {
        CarState state = replay(streamOf(received(14), placed(15, EventKind.CONSTRUCTIVE)));
        assertThat(state.placed()).isTrue();
        assertThat(state.placedAt()).contains(utc(2025, 1, 15, 8));
    }

    @Test
    void aCarPlacedTwiceIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), placed(15), placed(16))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aReleaseWithoutAPlacementIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), released(15)))).isInstanceOf(EventError.class);
    }

    @Test
    void aReleasedCarIsWaitingToBePulled() {
        CarState state = replay(streamOf(received(14), placed(15), released(16)));
        assertThat(state.awaitingPull()).isTrue();
        assertThat(state.releasedAt()).contains(utc(2025, 1, 16, 15));
    }

    @Test
    void aCarReleasedTwiceIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), placed(15), released(16), released(17))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aPullClearsTheTrack() {
        CarState state = replay(streamOf(received(14), placed(15), released(16), pulled(17)));
        assertThat(state.placed()).isFalse();
        assertThat(state.industry()).isEmpty();
        assertThat(state.awaitingPull()).isFalse();
    }

    @Test
    void aPullWithNothingOnTheTrackIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), pulled(15)))).isInstanceOf(EventError.class);
    }

    @Test
    void aCarStillOnATrackCannotBeDelivered() {
        EventError caught =
                catchThrowableOfType(EventError.class, () -> replay(streamOf(received(14), placed(15), delivered(16))));
        assertThat(caught.message()).contains("pulled before it goes across");
    }

    @Test
    void aCarStillOnATrackCannotBeMoved() {
        assertThatThrownBy(() -> replay(streamOf(received(14), placed(15), moved(16, "PWR", 336))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aMovementAddsUpTheMiles() {
        CarState state = replay(streamOf(received(14), moved(15, "PWR", 336), moved(16, "IMT", 286)));
        assertThat(state.tenthsRun()).isEqualTo(622);
        assertThat(state.station()).contains("IMT");
    }

    @Test
    void aBadOrderedCarDoesNotMove() {
        CarState state = replay(streamOf(received(14), simple(15, EventKind.BAD_ORDERED)));
        assertThat(state.badOrdered()).isTrue();
        assertThat(state.movable()).isFalse();
    }

    @Test
    void aBadOrderedCarCannotBeDelivered() {
        assertThatThrownBy(() -> replay(streamOf(received(14), simple(15, EventKind.BAD_ORDERED), delivered(16))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aCarBadOrderedTwiceIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(
                        received(14),
                        simple(15, EventKind.BAD_ORDERED),
                        Event.of(EventKind.BAD_ORDERED, CAR, utc(2025, 1, 16, 11), "ESC"))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aRepairPutsTheCarBackInService() {
        CarState state =
                replay(streamOf(received(14), simple(15, EventKind.BAD_ORDERED), simple(16, EventKind.REPAIRED)));
        assertThat(state.badOrdered()).isFalse();
        assertThat(state.movable()).isTrue();
    }

    @Test
    void aRepairOnACarThatWasNotBadOrderedIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), simple(15, EventKind.REPAIRED))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aHeldCarDoesNotMove() {
        CarState state = replay(streamOf(received(14), simple(15, EventKind.HELD)));
        assertThat(state.held()).isTrue();
        assertThat(state.movable()).isFalse();
    }

    @Test
    void freeingACarThatWasNotHeldIsRefused() {
        assertThatThrownBy(() -> replay(streamOf(received(14), simple(15, EventKind.FREED))))
                .isInstanceOf(EventError.class);
    }

    @Test
    void aFreedCarMovesAgain() {
        CarState state = replay(streamOf(received(14), simple(15, EventKind.HELD), simple(16, EventKind.FREED)));
        assertThat(state.held()).isFalse();
        assertThat(state.movable()).isTrue();
    }

    @Test
    void aReplayCanStopPartWay() {
        CarStream stream = streamOf(received(14), placed(15), released(16), pulled(17));
        CarState state = replay(stream, utc(2025, 1, 15, 12));
        assertThat(state.placed()).isTrue();
        assertThat(state.applied()).isEqualTo(2);
    }

    @Test
    void aSecondCycleStartsClean() {
        CarStream stream = streamOf(
                received(14),
                placed(15),
                released(16),
                pulled(17),
                delivered(18),
                Event.builder(EventKind.RECEIVED, CAR, utc(2025, 1, 20, 14), "ESC")
                        .road(CN)
                        .waybill(41300)
                        .build());
        CarState state = replay(stream);
        assertThat(state.onLine()).isTrue();
        assertThat(state.waybill()).contains(41300);
        assertThat(state.placed()).isFalse();
    }

    @Test
    void theStateCountsWhatItApplied() {
        assertThat(replay(streamOf(received(14), placed(15))).applied()).isEqualTo(2);
    }

    @Test
    void askingTheWrongStreamForACarIsRefused() {
        assertThatThrownBy(() -> stateOf(streamOf(received(14)), CarMark.parse("CN 400100")))
                .isInstanceOf(EventError.class);
    }

    @Test
    void askingTheRightStreamReplaysIt() {
        assertThat(stateOf(streamOf(received(14)), CAR).onLine()).isTrue();
    }

    @Test
    void aStateIsFrozen() {
        // A state is a record: every field is final and there is nothing that sets one, so a state
        // only moves on by a replay building the next one.
        assertThat(CarState.class.isRecord()).isTrue();
        assertThat(Arrays.stream(CarState.class.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers())))
                .allMatch(field -> Modifier.isFinal(field.getModifiers()));
        assertThat(Arrays.stream(CarState.class.getMethods()).map(Method::getName))
                .noneMatch(name -> name.startsWith("set"));
        Field onLine = Arrays.stream(CarState.class.getDeclaredFields())
                .filter(field -> field.getName().equals("onLine"))
                .findFirst()
                .orElseThrow();
        assertThat(Modifier.isFinal(onLine.getModifiers())).isTrue();
    }
}

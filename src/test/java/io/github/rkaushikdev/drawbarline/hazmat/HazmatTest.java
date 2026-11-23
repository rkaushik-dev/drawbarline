package io.github.rkaushikdev.drawbarline.hazmat;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.KRAFT_PAPER;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.SODIUM_HYDROXIDE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class HazmatTest {

    static final HazardClass CORROSIVE = HazardClass.CORROSIVE;

    static Standing aCar(int position) {
        return new Standing(position, false, Optional.empty(), false);
    }

    static Standing occupied(int position) {
        return new Standing(position, false, Optional.empty(), true);
    }

    static Standing placarded(int position) {
        return placarded(position, CORROSIVE);
    }

    static Standing placarded(int position, HazardClass hazard) {
        return new Standing(position, true, hazard);
    }

    @Test
    void everyClassCarriesItsNumberAndText() {
        assertThat(HazardClass.CORROSIVE.number()).isEqualTo(8);
        assertThat(HazardClass.FLAMMABLE_LIQUID.text()).isEqualTo("flammable liquid");
    }

    @Test
    void mostClassesNeedABuffer() {
        assertThat(HazardClass.EXPLOSIVE.needsBuffer()).isTrue();
        assertThat(HazardClass.MISCELLANEOUS.needsBuffer()).isFalse();
    }

    @Test
    void aPlacardPrintsItsNameNumberAndClass() {
        Placard found = new Placard(CORROSIVE, 1824, "sodium hydroxide solution");
        assertThat(found).hasToString("sodium hydroxide solution UN1824 class 8");
    }

    @Test
    void aUnNumberOutOfRangeIsRefused() {
        assertThatThrownBy(() -> new Placard(CORROSIVE, 0, "something")).isInstanceOf(HazmatError.class);
    }

    @Test
    void aPlacardWithNoNameIsRefused() {
        assertThatThrownBy(() -> new Placard(CORROSIVE, 1824, "  ")).isInstanceOf(HazmatError.class);
    }

    @Test
    void aHazardousCommodityTakesAPlacard() {
        Placard found = Hazmat.placardFor(SODIUM_HYDROXIDE, 1824, CORROSIVE);
        assertThat(found.name()).isEqualTo("sodium hydroxide solution");
    }

    @Test
    void anOrdinaryCommodityTakesNoPlacard() {
        assertThatThrownBy(() -> Hazmat.placardFor(KRAFT_PAPER, 1824, CORROSIVE))
                .isInstanceOf(HazmatError.class);
    }

    @Test
    void theHazardIsReadOffTheCommodityCode() {
        assertThat(Hazmat.isHazardous(SODIUM_HYDROXIDE)).isTrue();
        assertThat(Hazmat.isHazardous(KRAFT_PAPER)).isFalse();
    }

    @Test
    void aPlacardedCarNamesItsClass() {
        assertThatThrownBy(() -> new Standing(1, true)).isInstanceOf(HazmatError.class);
    }

    @Test
    void anUnplacardedCarCarriesNoClass() {
        assertThatThrownBy(() -> new Standing(1, false, CORROSIVE)).isInstanceOf(HazmatError.class);
    }

    @Test
    void positionsCountFromOne() {
        assertThatThrownBy(() -> new Standing(0, false)).isInstanceOf(HazmatError.class);
    }

    @Test
    void aTrainOfNothingHandles() {
        assertThat(Hazmat.handles(List.of())).isTrue();
    }

    @Test
    void aTrainWithNoPlacardsHandles() {
        assertThat(Hazmat.handles(List.of(aCar(1), aCar(2), aCar(3)))).isTrue();
    }

    @Test
    void aPlacardedCarBehindABufferHandles() {
        assertThat(Hazmat.handles(List.of(aCar(1), placarded(2), aCar(3)))).isTrue();
    }

    @Test
    void aPlacardedCarAgainstTheEngineDoesNotHandle() {
        List<String> found = Hazmat.checkStanding(List.of(placarded(1), aCar(2)));
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("against the engine");
    }

    @Test
    void theFindingNamesTheClass() {
        List<String> found = Hazmat.checkStanding(List.of(placarded(1), aCar(2)));
        assertThat(found.get(0)).contains("corrosive");
    }

    @Test
    void aMiscellaneousPlacardMaySitAgainstTheEngine() {
        assertThat(Hazmat.handles(List.of(placarded(1, HazardClass.MISCELLANEOUS), aCar(2))))
                .isTrue();
    }

    @Test
    void aPlacardedCarAgainstAnOccupiedCarDoesNotHandle() {
        List<String> found = Hazmat.checkStanding(List.of(aCar(1), placarded(2), occupied(3)));
        assertThat(found).hasSize(1);
        assertThat(found.get(0)).contains("occupied car");
    }

    @Test
    void aPlacardedCarABufferAwayFromAnOccupiedCarHandles() {
        assertThat(Hazmat.handles(List.of(aCar(1), placarded(2), aCar(3), occupied(4))))
                .isTrue();
    }

    @Test
    void bothFindingsComeBackWhenBothApply() {
        assertThat(Hazmat.checkStanding(List.of(placarded(1), occupied(2)))).hasSize(2);
    }

    @Test
    void twoPlacardedCarsNextToEachOtherAreFine() {
        assertThat(Hazmat.handles(List.of(aCar(1), placarded(2), placarded(3), aCar(4))))
                .isTrue();
    }

    @Test
    void thePositionsHaveToRunWithoutAGap() {
        assertThatThrownBy(() -> Hazmat.checkStanding(List.of(aCar(1), aCar(3))))
                .isInstanceOf(HazmatError.class);
    }

    @Test
    void theCarsMayBeGivenInAnyOrder() {
        assertThat(Hazmat.handles(List.of(aCar(3), placarded(2), aCar(1)))).isTrue();
    }

    @Test
    void anOccupiedCarAtTheHeadEndIsCheckedToo() {
        List<String> found = Hazmat.checkStanding(List.of(occupied(1), placarded(2), aCar(3)));
        assertThat(found).anySatisfy(one -> assertThat(one).contains("occupied car"));
    }

    /** A train of so many cars with one placarded car at a position, the rest plain. */
    static List<Standing> aTrain(int cars, int placardedAt) {
        List<Standing> found = new ArrayList<>();
        for (int at = 1; at <= cars; at++) {
            found.add(at == placardedAt ? placarded(at) : aCar(at));
        }
        return found;
    }

    @Test
    void aLongTrainStandsAPlacardedCarSixthFromTheEngine() {
        assertThat(Hazmat.handles(aTrain(8, 6))).isTrue();
        assertThat(Hazmat.handles(aTrain(8, 7))).isTrue();
        List<String> found = Hazmat.checkStanding(aTrain(8, 5));
        assertThat(found)
                .containsExactly(
                        "position 5 is placarded corrosive and the train has the cars to stand it sixth from the engine");
    }

    @Test
    void aTrainWithoutFiveSpareCarsOnlyNeedsTheOneBuffer() {
        assertThat(Hazmat.engineBuffer(aTrain(5, 2))).isEqualTo(Hazmat.BUFFER_CARS);
        assertThat(Hazmat.handles(aTrain(5, 2))).isTrue();
        assertThat(Hazmat.checkStanding(aTrain(5, 1)))
                .containsExactly("position 1 is placarded corrosive and sits against the engine");
    }

    @Test
    void fiveSpareCarsAreWhatMakesATrainLongEnough() {
        assertThat(Hazmat.engineBuffer(aTrain(6, 2))).isEqualTo(Hazmat.SIXTH_FROM_THE_ENGINE - 1);
        assertThat(Hazmat.handles(aTrain(6, 2))).isFalse();
        assertThat(Hazmat.handles(aTrain(6, 6))).isTrue();
    }

    @Test
    void aMiscellaneousPlacardNeedsNoPlaceAndCountsAsASpareCar() {
        List<Standing> cars = List.of(
                placarded(1, HazardClass.MISCELLANEOUS), aCar(2), aCar(3), aCar(4), aCar(5), placarded(6), aCar(7));
        assertThat(Hazmat.engineBuffer(cars)).isEqualTo(5);
        assertThat(Hazmat.handles(cars)).isTrue();
    }

    @Test
    void everyPlacardedCarTooNearTheEngineIsNamed() {
        List<Standing> cars = List.of(aCar(1), placarded(2), aCar(3), placarded(4), aCar(5), aCar(6), aCar(7), aCar(8));
        assertThat(Hazmat.checkStanding(cars)).hasSize(2).allSatisfy(one -> assertThat(one)
                .contains("sixth from the engine"));
    }
}

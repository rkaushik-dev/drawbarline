package io.github.rkaushikdev.drawbarline.train;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.SODIUM_HYDROXIDE;
import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static io.github.rkaushikdev.drawbarline.support.Cars.aCar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.api.Errors;
import io.github.rkaushikdev.drawbarline.fleet.Car;
import io.github.rkaushikdev.drawbarline.hazmat.HazardClass;
import io.github.rkaushikdev.drawbarline.hazmat.Placard;
import io.github.rkaushikdev.drawbarline.hazmat.Standing;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.mileage.Mileage;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrainTest {

    static final Lading GRAIN = new Lading(WHEAT, new Weight(198000));
    static final LocalDate DAY = LocalDate.of(2025, 1, 14);

    static Consist aConsist() {
        return aConsist(3, true);
    }

    static Consist aConsist(int count) {
        return aConsist(count, true);
    }

    static Consist aConsist(int count, boolean loaded) {
        List<Position> positions = new ArrayList<>();
        for (int at = 0; at < count; at++) {
            positions.add(new Position(aCar("CN " + (400100 + at)), loaded ? Optional.of(GRAIN) : Optional.empty()));
        }
        return new Consist("L51", DAY, positions);
    }

    @Test
    void aTrainPrintsItsSymbolCarsAndTons() {
        assertThat(aConsist()).hasToString("L51 2025-01-14 3 cars 392 tons");
    }

    @Test
    void aTrainWithNoSymbolIsRefused() {
        assertThatThrownBy(() -> new Consist("  ", DAY, List.of())).isInstanceOf(ConsistError.class);
    }

    @Test
    void theSameCarTwiceInOneTrainIsRefused() {
        Car car = aCar("CN 400100");
        assertThatThrownBy(() -> new Consist("L51", DAY, List.of(new Position(car), new Position(car))))
                .isInstanceOf(ConsistError.class);
    }

    @Test
    void aLoadedCarWeighsItsLightWeightAndItsLading() {
        assertThat(new Position(aCar("CN 400100"), GRAIN).weight()).isEqualTo(new Weight(63500 + 198000));
    }

    @Test
    void anEmptyCarWeighsOnlyItself() {
        assertThat(new Position(aCar("CN 400100")).weight()).isEqualTo(new Weight(63500));
    }

    @Test
    void aPositionPrintsWhatItCarries() {
        assertThat(new Position(aCar("CN 400100"), GRAIN)).hasToString("CN 400100 wheat");
        assertThat(new Position(aCar("CN 400100"))).hasToString("CN 400100 empty");
    }

    @Test
    void theTrainCountsLoadsAndEmpties() {
        List<Position> positions = List.of(
                new Position(aCar("CN 400100"), GRAIN),
                new Position(aCar("CN 400101")),
                new Position(aCar("CN 400102"), GRAIN));
        Consist found = new Consist("L51", DAY, positions);
        assertThat(found.loads()).isEqualTo(2);
        assertThat(found.empties()).isEqualTo(1);
    }

    @Test
    void theTonnageIsTheWholeTrain() {
        assertThat(aConsist(2).weight()).isEqualTo(new Weight(2 * (63500 + 198000)));
    }

    @Test
    void theTonnageRoundsDownTheWayARatingIsRead() {
        assertThat(aConsist(1).tons()).isEqualTo(130);
    }

    @Test
    void theLengthCountsTheSlackAtEveryCoupling() {
        assertThat(aConsist(3).length()).isEqualTo(new Length(3 * 714 + 2 * 6));
    }

    @Test
    void aTrainOfOneCarHasNoSlack() {
        assertThat(aConsist(1).length()).isEqualTo(new Length(714));
    }

    @Test
    void aTrainOfNoCarsIsNoLength() {
        Consist empty = new Consist("L51", DAY, List.of());
        assertThat(empty.length()).isEqualTo(new Length(0));
        assertThat(empty.tons()).isZero();
    }

    @Test
    void aTrainKnowsWhichCarsItCarries() {
        Consist found = aConsist();
        assertThat(found.carries(CarMark.parse("CN 400100"))).isTrue();
        assertThat(found.carries(CarMark.parse("WC 000001"))).isFalse();
    }

    @Test
    void theMainAndTheBranchHaveTheirOwnRatings() {
        assertThat(Train.district(Mileage.MAIN).maxTons()).isEqualTo(6800);
        assertThat(Train.district(Mileage.BRANCH).maxTons()).isEqualTo(3400);
    }

    @Test
    void aLineThisRoadDoesNotHaveIsRefused() {
        assertThatThrownBy(() -> Train.district("C")).isInstanceOf(ConsistError.class);
    }

    @Test
    void aDistrictOfNoTonnageIsRefused() {
        assertThatThrownBy(() -> new District(Mileage.MAIN, "nowhere", 0, Length.of(100)))
                .isInstanceOf(ConsistError.class);
    }

    @Test
    void aDistrictOnALineWeDoNotHaveIsRefused() {
        assertThatThrownBy(() -> new District("C", "nowhere", 100, Length.of(100)))
                .isInstanceOf(ConsistError.class);
    }

    @Test
    void aTrainInsideTheRatingClears() {
        assertThat(Train.clears(aConsist(10), Train.district(Mileage.MAIN))).isTrue();
    }

    @Test
    void aTrainOverTheTonnageDoesNotClear() {
        Consist found = aConsist(60);
        assertThat(Train.clears(found, Train.district(Mileage.MAIN))).isFalse();
        assertThat(Train.check(found, Train.district(Mileage.MAIN)))
                .anySatisfy(one -> assertThat(one).contains("tons is over"));
    }

    @Test
    void aTrainOverTheLengthDoesNotClear() {
        Consist found = aConsist(80, false);
        assertThat(Train.check(found, Train.district(Mileage.MAIN)))
                .anySatisfy(one -> assertThat(one).contains("longer than"));
    }

    @Test
    void theBranchStopsATrainTheMainWouldTake() {
        Consist found = aConsist(30);
        assertThat(Train.clears(found, Train.district(Mileage.MAIN))).isTrue();
        assertThat(Train.clears(found, Train.district(Mileage.BRANCH))).isFalse();
    }

    @Test
    void aTrainThatClearsDrawsNoFindings() {
        assertThat(Train.check(aConsist(3), Train.district(Mileage.MAIN))).isEmpty();
    }

    @Test
    void theWheelReportListsTheCarsInOrder() {
        List<String> rows = Train.wheelReport(aConsist()).stream()
                .filter(line -> line.contains("CN 4001"))
                .toList();
        assertThat(rows.stream().map(line -> line.strip().split("\\s+")[0]).toList())
                .containsExactly("1", "2", "3");
    }

    @Test
    void theWheelReportNamesTheTrain() {
        assertThat(Train.wheelReport(aConsist()).get(0)).isEqualTo("WHEEL REPORT L51");
    }

    @Test
    void theWheelReportTotalsTheTrain() {
        String total = Train.wheelReport(aConsist()).stream()
                .filter(line -> line.strip().startsWith("3 cars"))
                .findFirst()
                .orElseThrow();
        assertThat(total).contains("3 loads 0 empties");
        assertThat(total).contains("392");
    }

    @Test
    void theWheelReportShowsAnEmptyAsEmpty() {
        assertThat(Train.wheelReport(aConsist(1, false)))
                .anySatisfy(line -> assertThat(line).contains("empty"));
    }

    @Test
    void theConsistErrorHasADecidedStatus() {
        assertThat(Errors.STATUS_FOR).containsKey(ConsistError.class);
    }

    /** A car of the usual size loaded to a gross weight on the rail. */
    static Position grossing(String mark, long pounds) {
        return new Position(aCar(mark), new Lading(WHEAT, new Weight(pounds - 63500)));
    }

    @Test
    void theBranchBridgesTakeAHundredTonCarAndTheMainAOneTen() {
        assertThat(Train.district(Mileage.BRANCH).maxCarWeight()).isEqualTo(new Weight(263000));
        assertThat(Train.district(Mileage.MAIN).maxCarWeight()).isEqualTo(new Weight(286000));
        assertThat(new District(Mileage.MAIN, "anywhere", 100, Length.of(100)).maxCarWeight())
                .isEqualTo(new Weight(286000));
    }

    @Test
    void aOneTenCarLoadedFullRunsTheMainAndNotTheBranch() {
        Consist found = new Consist("L51", DAY, List.of(grossing("CN 400100", 286000), grossing("CN 400101", 263000)));
        assertThat(Train.clears(found, Train.district(Mileage.MAIN))).isTrue();
        assertThat(Train.check(found, Train.district(Mileage.BRANCH)))
                .containsExactly(
                        "CN 400100 weighs 286,000 lb on the rail and the powers to niagara takes 263,000 lb a car");
        assertThat(Train.setOuts(found, Train.district(Mileage.BRANCH)))
                .extracting(one -> one.car().mark().toString())
                .containsExactly("CN 400100");
    }

    @Test
    void aCarExactlyAtTheRatingRuns() {
        District branch = Train.district(Mileage.BRANCH);
        assertThat(branch.takesCar(grossing("CN 400100", 263000))).isTrue();
        assertThat(branch.takesCar(grossing("CN 400100", 263001))).isFalse();
        assertThat(branch.takesCar(new Position(aCar("CN 400100")))).isTrue();
    }

    @Test
    void theTrainFindingsComeBeforeTheCarFindings() {
        List<Position> positions = new ArrayList<>();
        positions.add(grossing("CN 400100", 280000));
        for (int at = 1; at < 30; at++) {
            positions.add(new Position(aCar("CN " + (400100 + at)), GRAIN));
        }
        List<String> found = Train.check(new Consist("L51", DAY, positions), Train.district(Mileage.BRANCH));
        assertThat(found).hasSize(2);
        assertThat(found.get(0)).contains("tons is over");
        assertThat(found.get(1)).startsWith("CN 400100 weighs 280,000 lb");
    }

    @Test
    void aDistrictTakesACarThatWeighsSomething() {
        assertThatThrownBy(() -> new District(Mileage.MAIN, "nowhere", 100, Length.of(100), Weight.NOTHING))
                .isInstanceOf(ConsistError.class);
    }

    @Test
    void aPositionReadsItsTonsDown() {
        assertThat(grossing("CN 400100", 263999).tons()).isEqualTo(131);
        assertThat(new Position(aCar("CN 400100")).tons()).isEqualTo(31);
    }

    static final Placard CAUSTIC = new Placard(HazardClass.CORROSIVE, 1824, "sodium hydroxide solution");
    static final Lading LYE = new Lading(SODIUM_HYDROXIDE, new Weight(180000));

    /** A train of so many plain hoppers with a placarded tank at one place. */
    static Consist withTankAt(int cars, int at) {
        List<Position> positions = new ArrayList<>();
        for (int place = 1; place <= cars; place++) {
            Car car = aCar("CN " + (400100 + place));
            positions.add(place == at ? new Position(car, LYE, CAUSTIC) : new Position(car, GRAIN));
        }
        return new Consist("L51", DAY, positions);
    }

    @Test
    void aHazardousLoadTravelsUnderAPlacard() {
        assertThatThrownBy(() -> new Position(aCar("CN 400100"), LYE))
                .isInstanceOf(ConsistError.class)
                .hasMessageContaining("under a placard");
        assertThatThrownBy(() -> new Position(aCar("CN 400100"), GRAIN, CAUSTIC))
                .isInstanceOf(ConsistError.class)
                .hasMessageContaining("hazardous load or its residue");
    }

    @Test
    void aResidueEmptyKeepsItsPlacard() {
        Position found = Position.residue(aCar("CN 400100"), CAUSTIC);
        assertThat(found.loaded()).isFalse();
        assertThat(found.standing(3)).isEqualTo(new Standing(3, true, HazardClass.CORROSIVE));
    }

    @Test
    void theTrainCheckFindsAPlacardTooNearTheEngine() {
        assertThat(Train.check(withTankAt(8, 3), Train.district(Mileage.MAIN)))
                .containsExactly(
                        "position 3 is placarded corrosive and the train has the cars to stand it sixth from the engine");
        assertThat(Train.check(withTankAt(8, 6), Train.district(Mileage.MAIN))).isEmpty();
    }

    @Test
    void theStandingsCountFromTheHeadEnd() {
        assertThat(withTankAt(3, 2).standings())
                .extracting(one -> one.position() + " " + one.placarded())
                .containsExactly("1 false", "2 true", "3 false");
    }

    @Test
    void theWheelReportShowsThePlacardsAndCountsThem() {
        List<String> lines = Train.wheelReport(withTankAt(3, 2));
        assertThat(lines)
                .anySatisfy(line -> assertThat(line).contains("CN 400102").contains("UN1824 class 8"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("1 placarded"));
        List<String> residue =
                Train.wheelReport(new Consist("L51", DAY, List.of(Position.residue(aCar("CN 400100"), CAUSTIC))));
        assertThat(residue).anySatisfy(line -> assertThat(line).contains("UN1824 class 8 residue"));
    }
}

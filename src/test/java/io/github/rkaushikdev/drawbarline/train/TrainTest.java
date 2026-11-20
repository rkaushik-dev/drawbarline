package io.github.rkaushikdev.drawbarline.train;

import static io.github.rkaushikdev.drawbarline.lading.Commodity.WHEAT;
import static io.github.rkaushikdev.drawbarline.support.Cars.aCar;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.fleet.Car;
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
}

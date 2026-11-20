package io.github.rkaushikdev.drawbarline.blocking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.mileage.MileageError;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockingTest {

    static final Block GREEN_BAY = new Block("GRB", "green bay and beyond", 1, Set.of("GRB", "OCO"));
    static final Block MARINETTE = new Block("MRN", "marinette", 2, Set.of("MRN", "PSH"));
    static final Block LOCAL = new Block("LCL", "the local", 3, Set.of("PWR", "SPL", "HRM"));

    static Plan aPlan() {
        return new Plan("ESC", List.of(GREEN_BAY, MARINETTE, LOCAL));
    }

    static CarMark aCar(int number) {
        return CarMark.parse("CN " + number);
    }

    static Bound bound(int number, String destination) {
        return new Bound(aCar(number), destination);
    }

    static List<String> shown(List<CarMark> cars) {
        return cars.stream().map(CarMark::toString).toList();
    }

    @Test
    void aBlockPrintsItsNameAndPosition() {
        assertThat(GREEN_BAY).hasToString("GRB green bay and beyond at 1");
    }

    @Test
    void aBlockCodeIsUpperCase() {
        assertThatThrownBy(() -> new Block("grb", "x", 1, Set.of("GRB"))).isInstanceOf(BlockingError.class);
    }

    @Test
    void aBlockStandsFromPositionOne() {
        assertThatThrownBy(() -> new Block("GRB", "x", 0, Set.of("GRB"))).isInstanceOf(BlockingError.class);
    }

    @Test
    void aBlockCoveringNothingIsRefused() {
        assertThatThrownBy(() -> new Block("GRB", "x", 1, Set.of())).isInstanceOf(BlockingError.class);
    }

    @Test
    void aBlockCoveringAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> new Block("CHI", "x", 1, Set.of("CHI"))).isInstanceOf(MileageError.class);
    }

    @Test
    void aBlockTakesThePlacesItCovers() {
        assertThat(GREEN_BAY.takes("OCO")).isTrue();
        assertThat(GREEN_BAY.takes("MRN")).isFalse();
    }

    @Test
    void aPlanStandsItsBlocksHeadEndFirst() {
        assertThat(aPlan().standing()).extracting(Block::code).containsExactly("GRB", "MRN", "LCL");
    }

    @Test
    void aPlanWithNoBlocksIsRefused() {
        assertThatThrownBy(() -> new Plan("ESC", List.of())).isInstanceOf(BlockingError.class);
    }

    @Test
    void aPlanAtAPlaceWeDoNotServeIsRefused() {
        assertThatThrownBy(() -> new Plan("CHI", List.of(GREEN_BAY))).isInstanceOf(MileageError.class);
    }

    @Test
    void twoBlocksWithOneCodeAreRefused() {
        Block other = new Block("GRB", "again", 4, Set.of("WAL"));
        assertThatThrownBy(() -> new Plan("ESC", List.of(GREEN_BAY, other))).isInstanceOf(BlockingError.class);
    }

    @Test
    void twoBlocksInOnePositionAreRefused() {
        Block other = new Block("WAL", "wallace", 1, Set.of("WAL"));
        assertThatThrownBy(() -> new Plan("ESC", List.of(GREEN_BAY, other))).isInstanceOf(BlockingError.class);
    }

    @Test
    void twoBlocksCoveringOnePlaceAreRefused() {
        Block other = new Block("OCO", "oconto", 4, Set.of("OCO"));
        BlockingError caught =
                catchThrowableOfType(BlockingError.class, () -> new Plan("ESC", List.of(GREEN_BAY, other)));
        assertThat(caught.context().get("places")).isEqualTo(List.of("OCO"));
    }

    @Test
    void aDestinationFindsItsBlock() {
        assertThat(aPlan().blockFor("PSH").orElseThrow().code()).isEqualTo("MRN");
    }

    @Test
    void aDestinationNobodyPlannedForHasNoBlock() {
        assertThat(aPlan().blockFor("NIA")).isEmpty();
    }

    @Test
    void aPlanListsEverythingItCovers() {
        assertThat(aPlan().covers()).contains("SPL");
        assertThat(aPlan().covers()).doesNotContain("NIA");
    }

    @Test
    void everyCarGoesInItsBlock() {
        Classification found = Blocking.classify(List.of(bound(1, "GRB"), bound(2, "MRN"), bound(3, "PWR")), aPlan());
        assertThat(shown(found.carsIn("GRB"))).containsExactly("CN 000001");
        assertThat(shown(found.carsIn("LCL"))).containsExactly("CN 000003");
    }

    @Test
    void twoCarsForOneBlockStandTogether() {
        Classification found = Blocking.classify(List.of(bound(1, "GRB"), bound(2, "OCO")), aPlan());
        assertThat(found.carsIn("GRB")).hasSize(2);
    }

    @Test
    void carsInsideABlockComeBackInCarOrder() {
        Classification found = Blocking.classify(List.of(bound(9, "GRB"), bound(2, "OCO")), aPlan());
        assertThat(shown(found.carsIn("GRB"))).containsExactly("CN 000002", "CN 000009");
    }

    @Test
    void aCarNobodyPlannedForIsNotQuietlyAdded() {
        Classification found = Blocking.classify(List.of(bound(1, "NIA")), aPlan());
        assertThat(found.unblocked()).containsExactly(aCar(1));
        assertThat(found.blocked()).isZero();
    }

    @Test
    void aBlockWithNoCarsIsStillOnThePlan() {
        Classification found = Blocking.classify(List.of(bound(1, "GRB")), aPlan());
        assertThat(found.carsIn("MRN")).isEmpty();
    }

    @Test
    void theClassificationPrintsItsCounts() {
        Classification found = Blocking.classify(List.of(bound(1, "GRB"), bound(2, "MRN")), aPlan());
        assertThat(found).hasToString("2 cars in 3 blocks");
    }

    @Test
    void unblockedCarsShowUpWhenItPrints() {
        Classification found = Blocking.classify(List.of(bound(1, "GRB"), bound(2, "NIA")), aPlan());
        assertThat(found.toString()).contains("1 unblocked");
    }

    @Test
    void theTrainStandsInBlockOrder() {
        List<Bound> cars = List.of(bound(3, "PWR"), bound(1, "GRB"), bound(2, "MRN"));
        Classification found = Blocking.classify(cars, aPlan());
        assertThat(shown(Blocking.orderOf(found, aPlan()))).containsExactly("CN 000001", "CN 000002", "CN 000003");
    }

    @Test
    void aTrainOfNothingOrdersToNothing() {
        assertThat(Blocking.orderOf(Blocking.classify(List.of(), aPlan()), aPlan()))
                .isEmpty();
    }

    @Test
    void unblockedCarsAreLeftOutOfTheTrain() {
        Classification found = Blocking.classify(List.of(bound(1, "GRB"), bound(2, "NIA")), aPlan());
        assertThat(Blocking.orderOf(found, aPlan())).hasSize(1);
    }
}

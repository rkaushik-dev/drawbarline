package io.github.rkaushikdev.drawbarline.demurrage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.TariffError;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BunchingTest {

    static final Tariff TARIFF =
            new Tariff("4200", LocalDate.of(2024, 7, 1), 2, 1, List.of(new Step(2, 10000), new Step(0, 20000)));
    static final LocalDate PLACED = LocalDate.of(2025, 1, 14);

    static Detention at(String industry, int number) {
        return new Detention(
                CarMark.parse("CN " + number),
                industry,
                PLACED,
                LocalDate.of(2025, 1, 15),
                Optional.of(LocalDate.of(2025, 1, 16)),
                LocalDate.of(2025, 1, 22),
                4,
                true,
                false);
    }

    /** Six cars set out on the mill track and six on the woodyard, the same morning. */
    static List<Detention> aBunch() {
        List<Detention> found = new ArrayList<>();
        for (int number = 1; number <= 6; number++) {
            found.add(at("ESCPAP1", number));
            found.add(at("ESCPAP2", 100 + number));
        }
        return found;
    }

    @Test
    void eachTrackIsReckonedAtItsOwnRate() {
        Bunching rates = Bunching.byTrack(4, Map.of("ESCPAP2", 2));
        List<Relief> found = rates.reliefs(aBunch());
        assertThat(found)
                .extracting(one -> one.car() + " " + one.days())
                .containsExactly(
                        "CN 000005 1", "CN 000006 1", "CN 000103 1", "CN 000104 1", "CN 000105 2", "CN 000106 2");
    }

    @Test
    void aTrackWithNoRateOfItsOwnTakesTheOrdinaryOne() {
        Bunching rates = Bunching.byTrack(4, Map.of("ESCPAP2", 2));
        assertThat(rates.perDay("ESCPAP1")).isEqualTo(4);
        assertThat(rates.perDay("ESCPAP2")).isEqualTo(2);
        assertThat(Bunching.flat(3).perDay("ESCPAP2")).isEqualTo(3);
    }

    @Test
    void aFlatRateRelievesBothTracksAlike() {
        assertThat(Bunching.flat(4).reliefs(aBunch())).extracting(Relief::days).containsExactly(1, 1, 1, 1);
        assertThat(Bunching.flat(4).reliefs(aBunch())).isEqualTo(Relief.bunching(aBunch(), 4));
    }

    @Test
    void relievingRecountsOnlyTheCarsPushedBack() {
        List<Detention> relieved = Bunching.byTrack(4, Map.of("ESCPAP2", 2)).relieve(aBunch(), TARIFF);
        assertThat(relieved).hasSize(12);
        assertThat(relieved.get(0)).isEqualTo(at("ESCPAP1", 1));
        Detention woodyardLast = relieved.get(11);
        assertThat(woodyardLast.car()).isEqualTo(CarMark.parse("CN 106"));
        assertThat(woodyardLast.clockStarts()).isEqualTo(LocalDate.of(2025, 1, 17));
        assertThat(woodyardLast.chargeable()).isLessThan(4);
    }

    @Test
    void everyTrackIsWorkedAtLeastACarADay() {
        assertThatThrownBy(() -> Bunching.flat(0)).isInstanceOf(TariffError.class);
        assertThatThrownBy(() -> Bunching.byTrack(4, Map.of("ESCPAP2", 0)))
                .isInstanceOf(TariffError.class)
                .hasMessageContaining("at least one car a day");
    }
}

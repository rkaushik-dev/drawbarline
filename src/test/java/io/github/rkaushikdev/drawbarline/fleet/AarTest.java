package io.github.rkaushikdev.drawbarline.fleet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AarTest {

    @Test
    void everyKindHasASingleLetter() {
        assertThat(CarKind.values())
                .allSatisfy(kind -> assertThat(kind.letter()).hasSize(1));
    }

    @Test
    void kindLettersAreUnique() {
        assertThat(Arrays.stream(CarKind.values()).map(CarKind::letter).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void everyKindDescribesItself() {
        assertThat(CarKind.values())
                .allSatisfy(kind -> assertThat(kind.description()).isNotEmpty());
    }

    @Test
    void aKindReadsBackOffItsLetter() {
        assertThat(CarKind.fromLetter("T")).isSameAs(CarKind.TANK);
    }

    @Test
    void aLetterNoKindCarriesIsRefused() {
        assertThatThrownBy(() -> CarKind.fromLetter("Z")).isInstanceOf(EquipmentError.class);
    }

    @Test
    void maintenanceEquipmentCarriesNoLading() {
        assertThat(CarKind.MAINTENANCE.carriesLading()).isFalse();
        assertThat(CarKind.BOX.carriesLading()).isTrue();
    }

    @Test
    void aPlainBoxCarDesignation() {
        MechanicalDesignation designation = new MechanicalDesignation("XM");
        assertThat(designation.kind()).isSameAs(CarKind.BOX);
        assertThat(designation.qualifiers()).isEqualTo("M");
        assertThat(designation).hasToString("XM");
    }

    @Test
    void aOneLetterDesignationHasNoQualifiers() {
        assertThat(new MechanicalDesignation("F").qualifiers()).isEmpty();
    }

    @Test
    void aCoveredHopperDesignation() {
        assertThat(new MechanicalDesignation("LO").kind()).isSameAs(CarKind.COVERED_HOPPER);
    }

    @Test
    void parseTakesLowerCaseAndSpace() {
        assertThat(MechanicalDesignation.parse(" rbl ")).isEqualTo(new MechanicalDesignation("RBL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "XMLOR", "X1", "xm", "Z"})
    void aDesignationThatCannotBeOneIsRefused(String bad) {
        assertThatThrownBy(() -> new MechanicalDesignation(bad)).isInstanceOf(EquipmentError.class);
    }

    @Test
    void aDesignationThatIsNotTextIsRefused() {
        assertThatThrownBy(() -> new MechanicalDesignation(null)).isInstanceOf(EquipmentError.class);
    }

    @Test
    void aTypeCodeCarriesAKindAndADetail() {
        CarTypeCode code = new CarTypeCode("T104");
        assertThat(code.kind()).isSameAs(CarKind.TANK);
        assertThat(code.detail()).isEqualTo(104);
        assertThat(code).hasToString("T104");
    }

    @Test
    void aDetailWithLeadingZerosReadsAsANumber() {
        assertThat(new CarTypeCode("X002").detail()).isEqualTo(2);
    }

    @Test
    void parseTakesLowerCase() {
        assertThat(CarTypeCode.parse("l351")).isEqualTo(new CarTypeCode("L351"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "T10", "T1045", "TT04", "Z104", "1104"})
    void aTypeCodeThatCannotBeOneIsRefused(String bad) {
        assertThatThrownBy(() -> new CarTypeCode(bad)).isInstanceOf(EquipmentError.class);
    }

    @Test
    void aTypeCodeThatIsNotTextIsRefused() {
        assertThatThrownBy(() -> new CarTypeCode(null))
                .isInstanceOf(EquipmentError.class)
                .hasMessageContaining("a type code is text");
    }

    @Test
    void theTwoIdentifiersAgreeWhenTheyNameTheSameKind() {
        assertThat(Aar.agrees(new MechanicalDesignation("LO"), new CarTypeCode("L351")))
                .isTrue();
    }

    @Test
    void theTwoIdentifiersDisagreeWhenTheyDoNot() {
        assertThat(Aar.agrees(new MechanicalDesignation("XM"), new CarTypeCode("T104")))
                .isFalse();
    }

    @Test
    void identifiersCompareByValue() {
        assertThat(new CarTypeCode("T104")).isEqualTo(new CarTypeCode("T104"));
        assertThat(new MechanicalDesignation("XM")).isEqualTo(new MechanicalDesignation("XM"));
    }

    @Test
    void identifiersAreHashable() {
        assertThat(Set.copyOf(List.of(new CarTypeCode("T104"), new CarTypeCode("T104"), new CarTypeCode("L351"))))
                .hasSize(2);
    }
}

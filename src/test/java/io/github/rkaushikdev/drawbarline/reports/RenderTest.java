package io.github.rkaushikdev.drawbarline.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.money.Money;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderTest {

    static Table aTable() {
        Table table = new Table(List.of(new Column("road"), new Column("amount", Align.RIGHT)));
        table.add("CN", "$1,200.00");
        table.add("ELS", "$8.00");
        return table;
    }

    @Test
    void aColumnTakesTheWidthOfItsWidestValue() {
        assertThat(new Column("road").sized(List.of("escanaba"))).isEqualTo(8);
    }

    @Test
    void aColumnIsNeverNarrowerThanItsHeading() {
        assertThat(new Column("amount").sized(List.of("1"))).isEqualTo(6);
    }

    @Test
    void aColumnCanBeGivenAFloorWidth() {
        assertThat(new Column("road", 12).sized(List.of("CN"))).isEqualTo(12);
    }

    @Test
    void textLaysOutLeft() {
        assertThat(new Column("road").lay("CN", 6)).isEqualTo("CN    ");
    }

    @Test
    void numbersLayOutRight() {
        assertThat(new Column("amount", Align.RIGHT).lay("12", 6)).isEqualTo("    12");
    }

    @Test
    void aTablePrintsAHeadingAndARule() {
        List<String> lines = aTable().lines();
        assertThat(lines.get(0)).isEqualTo("road     amount");
        assertThat(lines.get(1)).isEqualTo("----  ---------");
    }

    @Test
    void aNumericHeadingSitsOverItsNumbers() {
        List<String> lines = aTable().lines();
        assertThat(lines.get(0)).endsWith("amount");
        assertThat(lines.get(2)).endsWith("$1,200.00");
    }

    @Test
    void theRowsLineUpUnderTheHeadings() {
        List<String> lines = aTable().lines();
        assertThat(lines.get(2)).isEqualTo("CN    $1,200.00");
        assertThat(lines.get(3)).isEqualTo("ELS       $8.00");
    }

    @Test
    void aTableWithNoTotalsStopsAfterTheRows() {
        assertThat(aTable().lines()).hasSize(4);
    }

    @Test
    void aTotalGetsItsOwnRule() {
        Table table = aTable();
        table.total("", "$1,208.00");
        List<String> lines = table.lines();
        assertThat(lines.get(4)).isEqualTo("----  ---------");
        assertThat(lines.get(5)).isEqualTo("      $1,208.00");
    }

    @Test
    void aRowThatDoesNotFitTheColumnsIsRefused() {
        assertThatThrownBy(() -> aTable().add("CN")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aTotalThatDoesNotFitTheColumnsIsRefused() {
        assertThatThrownBy(() -> aTable().total("CN")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void aWideTotalWidensTheColumn() {
        Table table = aTable();
        table.total("", "$1,200,000.00");
        assertThat(table.widths().get(1)).isEqualTo("$1,200,000.00".length());
    }

    @Test
    void aTablePrintsAsItsLines() {
        assertThat(aTable()).hasToString(String.join("\n", aTable().lines()));
    }

    @Test
    void anEmptyTableStillPrintsItsHeadings() {
        Table table = new Table(List.of(new Column("road")));
        assertThat(table.lines()).containsExactly("road", "----");
    }

    @Test
    void aHeadingCarriesATitleAndABlankLine() {
        assertThat(Render.heading("car hire liability")).containsExactly("CAR HIRE LIABILITY", "");
    }

    @Test
    void aHeadingCanCarryASubtitle() {
        assertThat(Render.heading("car hire liability", "2025-01"))
                .containsExactly("CAR HIRE LIABILITY", "2025-01", "");
    }

    @Test
    void moneyPrintsTheWayTheDomainPrintsIt() {
        assertThat(Render.money(new Money(-123456))).isEqualTo("-$1,234.56");
    }

    @Test
    void hoursPrintToTwoPlaces() {
        assertThat(Render.hours(9600)).isEqualTo("96.00");
        assertThat(Render.hours(17)).isEqualTo("0.17");
    }

    @Test
    void milesPrintToOnePlace() {
        assertThat(Render.miles(336)).isEqualTo("33.6");
        assertThat(Render.miles(1089)).isEqualTo("108.9");
    }
}

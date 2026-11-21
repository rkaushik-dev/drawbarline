package io.github.rkaushikdev.drawbarline.limits;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.EventError;
import io.github.rkaushikdev.drawbarline.audit.Action;
import io.github.rkaushikdev.drawbarline.audit.Entry;
import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.models.AuditRow;
import io.github.rkaushikdev.drawbarline.persistence.models.EventRow;
import io.github.rkaushikdev.drawbarline.persistence.models.IndustryRow;
import io.github.rkaushikdev.drawbarline.persistence.models.ShipperRow;
import io.github.rkaushikdev.drawbarline.settlement.Adjustment;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * The domain and the columns have to agree about how long a field is.
 *
 * <p>Both sides use the same numbers, so this checks that the numbers are used rather than that
 * they match: a column that went back to a literal would slip past a test that only compared two
 * constants. The widths are read off the mapping itself.
 */
class LimitsTest {

    private static final CarMark CAR = CarMark.parse("CN 400100");

    /** The declared width of a text column, read from the field mapped to it. */
    private static int widthOf(Class<?> row, String column) {
        Field found = Arrays.stream(row.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Column.class))
                .filter(field -> field.getAnnotation(Column.class).name().equals(column))
                .findFirst()
                .orElseThrow(() -> new AssertionError(row.getSimpleName() + " maps no column " + column));
        assertThat(found.getType())
                .as("%s.%s is a text column", row.getSimpleName(), column)
                .isEqualTo(String.class);
        return found.getAnnotation(Column.class).length();
    }

    private static Event weighed(String note) {
        return Event.builder(EventKind.WEIGHED, CAR, utc(2025, 1, 6), "ESC")
                .note(note)
                .build();
    }

    @Test
    void everyLimitIsAPositiveWholeNumber() throws IllegalAccessException {
        List<Field> limits = Arrays.stream(Limits.class.getDeclaredFields())
                .filter(field -> Modifier.isStatic(field.getModifiers()))
                .filter(field -> field.getName().equals(field.getName().toUpperCase(Locale.ROOT)))
                .toList();
        assertThat(limits).isNotEmpty();
        for (Field limit : limits) {
            assertThat(limit.getType()).as(limit.getName()).isEqualTo(int.class);
            assertThat(limit.getInt(null)).as(limit.getName()).isPositive();
        }
    }

    @Test
    void aNoteAtTheLimitIsAccepted() {
        assertThat(weighed("x".repeat(Limits.NOTE)).note()).hasSize(Limits.NOTE);
    }

    @Test
    void aNoteOneOverIsRefused() {
        assertThatThrownBy(() -> weighed("x".repeat(Limits.NOTE + 1))).isInstanceOf(EventError.class);
    }

    @Test
    void theNoteColumnHoldsWhatTheDomainAccepts() {
        assertThat(widthOf(EventRow.class, "note")).isEqualTo(Limits.NOTE);
    }

    @Test
    void anActorAtTheLimitIsAccepted() {
        Entry found = new Entry(utc(2025, 1, 6), "x".repeat(Limits.ACTOR), Action.CLOSED_PERIOD, "2025-01");
        assertThat(found.actor()).hasSize(Limits.ACTOR);
    }

    @Test
    void theActorColumnHoldsWhatTheDomainAccepts() {
        assertThat(widthOf(AuditRow.class, "actor")).isEqualTo(Limits.ACTOR);
    }

    @Test
    void theDetailColumnHoldsWhatTheDomainAccepts() {
        assertThat(widthOf(AuditRow.class, "detail")).isEqualTo(Limits.DETAIL);
    }

    @Test
    void theSubjectColumnMatchesTheLimit() {
        assertThat(widthOf(AuditRow.class, "subject")).isEqualTo(Limits.SUBJECT);
    }

    @Test
    void theShipperColumnsMatchTheLimits() {
        assertThat(widthOf(ShipperRow.class, "code")).isEqualTo(Limits.SHIPPER_CODE);
        assertThat(widthOf(ShipperRow.class, "name")).isEqualTo(Limits.PARTY_NAME);
    }

    @Test
    void theIndustryColumnsMatchTheLimits() {
        assertThat(widthOf(IndustryRow.class, "code")).isEqualTo(Limits.INDUSTRY_CODE);
        assertThat(widthOf(IndustryRow.class, "station")).isEqualTo(Limits.STATION_CODE);
    }

    @Test
    void aReferenceIsCutToTheFieldRatherThanRefused() {
        Adjustment found = new Adjustment(
                new ReportingMark("CN"),
                new Period(2025, 1),
                new Period(2025, 3),
                new Money(-100),
                LocalDate.of(2025, 3, 12),
                "x".repeat(500));
        assertThat(found.reference()).hasSize(Limits.REFERENCE);
    }

    @Test
    void aCarMarkNeverExceedsItsColumn() {
        assertThat(CarMark.parse("PLMX 999999").toString().length()).isLessThanOrEqualTo(Limits.CAR_MARK);
    }

    @Test
    void aReportingMarkNeverExceedsItsColumn() {
        assertThat(new ReportingMark("PLMX").toString().length()).isLessThanOrEqualTo(Limits.REPORTING_MARK);
    }

    @Test
    void theHelperPassesWhatFits() {
        assertThat(Limits.fits("short", Limits.NOTE, "a note")).isEqualTo("short");
    }

    @Test
    void theHelperNamesTheFieldThatDoesNotFit() {
        assertThatThrownBy(() -> Limits.fits("x".repeat(200), Limits.NOTE, "a note"))
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("a note")
                .hasMessageContaining(String.valueOf(Limits.NOTE));
    }

    @Test
    void thePredicateAgreesWithTheHelper() {
        assertThat(Limits.within("short", Limits.NOTE)).isTrue();
        assertThat(Limits.within("x".repeat(200), Limits.NOTE)).isFalse();
    }
}

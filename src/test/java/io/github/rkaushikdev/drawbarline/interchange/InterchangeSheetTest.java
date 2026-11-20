package io.github.rkaushikdev.drawbarline.interchange;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.CODE_FOR;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.KIND_CODES;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.LINE_WIDTH;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.parse;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.parseRecord;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.render;
import static io.github.rkaushikdev.drawbarline.interchange.Sheet.renderRecord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.drawbarline.events.Event;
import io.github.rkaushikdev.drawbarline.events.EventKind;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class InterchangeSheetTest {

    static final ReportingMark CN = new ReportingMark("CN");
    static final Header HEADER = new Header(CN, "ESC", LocalDate.of(2025, 1, 6), 41);

    static String aRecord() {
        return aRecord("RC", "CN", "400100", "20250106", "0700");
    }

    static String aRecordOfKind(String kind) {
        return aRecord(kind, "CN", "400100", "20250106", "0700");
    }

    static String aRecordFor(String number) {
        return aRecord("RC", "CN", number, "20250106", "0700");
    }

    static String aRecord(String kind, String initial, String number, String stamp, String clock) {
        return Sheet.padded(String.format(
                Locale.ROOT, "IC%s%-4s%s%s%s041207%05d%-12s", kind, initial, number, stamp, clock, 0, ""));
    }

    static String aFile(String... records) {
        List<String> lines = new ArrayList<>();
        lines.add(HEADER.render());
        lines.addAll(List.of(records));
        lines.add(Sheet.padded(String.format(Locale.ROOT, "TR%06d", records.length)));
        return String.join("\n", lines) + "\n";
    }

    static Event received() {
        return received(400100, 12);
    }

    static Event received(int number, int hour) {
        return Event.builder(EventKind.RECEIVED, CarMark.parse("CN " + number), utc(2025, 1, 6, hour), "ESC")
                .road(CN)
                .build();
    }

    @Test
    void everyKindHasACodeAndEveryCodeAKind() {
        assertThat(new HashSet<>(KIND_CODES.values())).isEqualTo(EnumSet.allOf(EventKind.class));
        assertThat(CODE_FOR).hasSameSizeAs(KIND_CODES);
    }

    @Test
    void aHeaderRendersToTheLineWidth() {
        assertThat(HEADER.render()).hasSize(LINE_WIDTH);
        assertThat(HEADER.render()).startsWith("HDCN  ESC 2025010600");
    }

    @Test
    void aRecordRendersToTheLineWidth() {
        assertThat(renderRecord(received())).hasSize(LINE_WIDTH);
    }

    @Test
    void aRecordRoundTrips() {
        Event event = Event.builder(EventKind.RECEIVED, CarMark.parse("CN 400100"), utc(2025, 1, 6, 12), "ESC")
                .road(CN)
                .waybill(41207)
                .build();
        assertThat(parseRecord(renderRecord(event), "ESC", CN)).isEqualTo(event);
    }

    @Test
    void aMovementRoundTripsWithItsDistance() {
        Event event = Event.builder(EventKind.MOVED, CarMark.parse("CN 400100"), utc(2025, 1, 6, 18), "PWR")
                .tenths(336)
                .build();
        assertThat(parseRecord(renderRecord(event), "PWR", CN).tenths()).contains(336);
    }

    @Test
    void aPlacementRoundTripsWithItsTrack() {
        Event event = Event.builder(EventKind.PLACED, CarMark.parse("CN 400100"), utc(2025, 1, 7, 14), "ESC")
                .industry("ESCPAP1")
                .build();
        assertThat(parseRecord(renderRecord(event), "ESC", CN).industry()).contains("ESCPAP1");
    }

    @Test
    void aRecordReadsTheTimeLocalAtTheJunction() {
        Event found = parseRecord(aRecord(), "ESC", CN);
        assertThat(found.when()).isEqualTo(utc(2025, 1, 6, 12));
    }

    @Test
    void theSameRecordAtMarinetteIsAnHourLater() {
        Event found = parseRecord(aRecord(), "MRN", CN);
        assertThat(found.when()).isEqualTo(utc(2025, 1, 6, 13));
    }

    @Test
    void aKindCodeNobodyPublishesIsRefused() {
        assertThatThrownBy(() -> parseRecord(aRecordOfKind("ZZ"), "ESC", CN)).isInstanceOf(SheetError.class);
    }

    @Test
    void aDateThatIsNotADateIsRefused() {
        assertThatThrownBy(() -> parseRecord(aRecord("RC", "CN", "400100", "20259901", "0700"), "ESC", CN))
                .isInstanceOf(SheetError.class);
    }

    @Test
    void aCleanFileReadsBack() {
        Sheet sheet = parse(aFile(aRecord()));
        assertThat(sheet.isClean()).isTrue();
        assertThat(sheet.events()).hasSize(1);
        assertThat(sheet.header().orElseThrow().road()).isEqualTo(CN);
    }

    @Test
    void theHeaderCarriesTheJunctionAndSequence() {
        Sheet sheet = parse(aFile(aRecord()));
        assertThat(sheet.header().orElseThrow().junction()).isEqualTo("ESC");
        assertThat(sheet.header().orElseThrow().sequence()).isEqualTo(41);
    }

    @Test
    void aFileWithABadLineKeepsTheGoodOnes() {
        Sheet sheet = parse(aFile(aRecord(), aRecordOfKind("ZZ"), aRecordFor("400101")));
        assertThat(sheet.events()).hasSize(2);
        assertThat(sheet.problems()).hasSize(1);
    }

    @Test
    void aProblemNamesTheLineItWasOn() {
        Sheet sheet = parse(aFile(aRecord(), aRecordOfKind("ZZ")));
        assertThat(sheet.problems().get(0).line()).isEqualTo(3);
        assertThat(sheet.problems().get(0).toString()).contains("line 3");
    }

    @Test
    void aFileWithAProblemIsNotClean() {
        Sheet sheet = parse(aFile(aRecord(), aRecordOfKind("ZZ")));
        assertThat(sheet.isClean()).isFalse();
    }

    @Test
    void aTrailerThatDisagreesIsCaught() {
        String text = aFile(aRecord()).replace("TR000001", "TR000004");
        Sheet sheet = parse(text);
        assertThat(sheet.isCounted()).isFalse();
        assertThat(sheet.isClean()).isFalse();
    }

    @Test
    void aTruncatedFileIsCaughtByTheCount() {
        String text = aFile(aRecord(), aRecordFor("400101"));
        String truncated = String.join("\n", Sheet.lines(text).subList(0, 2)) + "\n";
        assertThat(parse(truncated).isCounted()).isFalse();
    }

    @Test
    void aRecordBeforeTheHeaderIsAProblem() {
        Sheet sheet = parse(aRecord() + "\n");
        assertThat(sheet.problems()).hasSize(1);
        assertThat(sheet.events()).isEmpty();
    }

    @Test
    void aRecordTypeNobodyPublishesIsAProblem() {
        Sheet sheet = parse(aFile(aRecord()) + "XX\n");
        assertThat(sheet.problems()).hasSize(1);
    }

    @Test
    void aJunctionWeDoNotServeIsAProblem() {
        String bad = HEADER.render().replace("ESC ", "CHI ");
        Sheet sheet = parse(bad + "\n");
        assertThat(sheet.problems()).hasSize(1);
        assertThat(sheet.header()).isEmpty();
    }

    @Test
    void blankLinesAreIgnored() {
        String text = aFile(aRecord()).replace("\n", "\n\n");
        assertThat(parse(text).isClean()).isTrue();
    }

    @Test
    void aWholeFileRoundTrips() {
        List<Event> events = List.of(received(), received(400101, 13));
        Sheet sheet = parse(render(HEADER, events));
        assertThat(sheet.events()).isEqualTo(events);
        assertThat(sheet.isClean()).isTrue();
    }

    @Test
    void anEmptyFileRendersATrailerOfNothing() {
        String text = render(HEADER, List.of());
        Sheet sheet = parse(text);
        assertThat(sheet.events()).isEmpty();
        assertThat(sheet.declared()).contains(0);
        assertThat(sheet.isClean()).isTrue();
    }

    @Test
    void everyRenderedLineIsTheSameWidth() {
        for (String line : Sheet.lines(render(HEADER, List.of(received())))) {
            assertThat(line).hasSize(LINE_WIDTH);
        }
    }

    @Test
    void aReadingInTheHourTheClocksRepeatIsTheFirstPass() {
        Event found = parseRecord(aRecord("RC", "CN", "400100", "20251102", "0130"), "MRN", CN);
        assertThat(found.when()).isEqualTo(utc(2025, 11, 2, 6, 30));
    }

    @Test
    void aReadingInTheHourTheClocksSkipIsAProblemWithItsLine() {
        Sheet sheet = parse(aFile(aRecord("RC", "CN", "400100", "20250309", "0230")));
        assertThat(sheet.events()).isEmpty();
        assertThat(sheet.problems()).hasSize(1);
        assertThat(sheet.problems().get(0).why()).contains("clocks went forward");
    }
}

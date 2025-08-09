from datetime import datetime

import pytest

from drawbarline.clock import utc
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.interchange.sheet import (
    CODE_FOR,
    KIND_CODES,
    LINE_WIDTH,
    Header,
    SheetError,
    parse,
    parse_record,
    render,
    render_record,
)
from drawbarline.marks import CarMark, ReportingMark

CN = ReportingMark("CN")
HEADER = Header(CN, "ESC", datetime(2025, 1, 6), 41)


def a_record(kind="RC", initial="CN", number="400100", stamp="20250106", clock="0700"):
    return f"IC{kind}{initial:<4}{number}{stamp}{clock}041207{0:05d}{'':<12}".ljust(80)


def a_file(*records):
    lines = [HEADER.render(), *records, f"TR{len(records):06d}".ljust(80)]
    return "\n".join(lines) + "\n"


def test_every_kind_has_a_code_and_every_code_a_kind():
    assert set(KIND_CODES.values()) == set(EventKind)
    assert len(CODE_FOR) == len(KIND_CODES)


def test_a_header_renders_to_the_line_width():
    assert len(HEADER.render()) == LINE_WIDTH
    assert HEADER.render().startswith("HDCN  ESC 2025010600")


def received(number=400100, hour=12, station="ESC"):
    return Event(
        EventKind.RECEIVED,
        CarMark.parse(f"CN {number}"),
        utc(2025, 1, 6, hour),
        station,
        road=CN,
    )


def test_a_record_renders_to_the_line_width():
    assert len(render_record(received())) == LINE_WIDTH


def test_a_record_round_trips():
    event = Event(
        EventKind.RECEIVED,
        CarMark.parse("CN 400100"),
        utc(2025, 1, 6, 12),
        "ESC",
        road=CN,
        waybill=41207,
    )
    assert parse_record(render_record(event), "ESC", CN) == event


def test_a_movement_round_trips_with_its_distance():
    event = Event(
        EventKind.MOVED,
        CarMark.parse("CN 400100"),
        utc(2025, 1, 6, 18),
        "PWR",
        tenths=336,
    )
    assert parse_record(render_record(event), "PWR", CN).tenths == 336


def test_a_placement_round_trips_with_its_track():
    event = Event(
        EventKind.PLACED,
        CarMark.parse("CN 400100"),
        utc(2025, 1, 7, 14),
        "ESC",
        industry="ESCPAP1",
    )
    assert parse_record(render_record(event), "ESC", CN).industry == "ESCPAP1"


def test_a_record_reads_the_time_local_at_the_junction():
    found = parse_record(a_record(), "ESC", CN)
    assert found.when == utc(2025, 1, 6, 12)


def test_the_same_record_at_marinette_is_an_hour_later():
    found = parse_record(a_record(), "MRN", CN)
    assert found.when == utc(2025, 1, 6, 13)


def test_a_kind_code_nobody_publishes_is_refused():
    with pytest.raises(SheetError):
        parse_record(a_record(kind="ZZ"), "ESC", CN)


def test_a_date_that_is_not_a_date_is_refused():
    with pytest.raises(SheetError):
        parse_record(a_record(stamp="20259901"), "ESC", CN)


def test_a_clean_file_reads_back():
    sheet = parse(a_file(a_record()))
    assert sheet.clean is True
    assert len(sheet.events) == 1
    assert sheet.header.road == CN


def test_the_header_carries_the_junction_and_sequence():
    sheet = parse(a_file(a_record()))
    assert sheet.header.junction == "ESC"
    assert sheet.header.sequence == 41


def test_a_file_with_a_bad_line_keeps_the_good_ones():
    sheet = parse(a_file(a_record(), a_record(kind="ZZ"), a_record(number="400101")))
    assert len(sheet.events) == 2
    assert len(sheet.problems) == 1


def test_a_problem_names_the_line_it_was_on():
    sheet = parse(a_file(a_record(), a_record(kind="ZZ")))
    assert sheet.problems[0].line == 3
    assert "line 3" in str(sheet.problems[0])


def test_a_file_with_a_problem_is_not_clean():
    sheet = parse(a_file(a_record(), a_record(kind="ZZ")))
    assert sheet.clean is False


def test_a_trailer_that_disagrees_is_caught():
    text = a_file(a_record()).replace("TR000001", "TR000004")
    sheet = parse(text)
    assert sheet.counted is False
    assert sheet.clean is False


def test_a_truncated_file_is_caught_by_the_count():
    text = a_file(a_record(), a_record(number="400101"))
    truncated = "\n".join(text.splitlines()[:2]) + "\n"
    assert parse(truncated).counted is False


def test_a_record_before_the_header_is_a_problem():
    sheet = parse(a_record() + "\n")
    assert len(sheet.problems) == 1
    assert sheet.events == []


def test_a_record_type_nobody_publishes_is_a_problem():
    sheet = parse(a_file(a_record()) + "XX\n")
    assert len(sheet.problems) == 1


def test_a_junction_we_do_not_serve_is_a_problem():
    bad = HEADER.render().replace("ESC ", "CHI ")
    sheet = parse(bad + "\n")
    assert len(sheet.problems) == 1
    assert sheet.header is None


def test_blank_lines_are_ignored():
    text = a_file(a_record()).replace("\n", "\n\n")
    assert parse(text).clean is True


def test_a_whole_file_round_trips():
    events = [received(), received(400101, hour=13)]
    sheet = parse(render(HEADER, events))
    assert sheet.events == events
    assert sheet.clean is True


def test_an_empty_file_renders_a_trailer_of_nothing():
    text = render(HEADER, [])
    sheet = parse(text)
    assert sheet.events == []
    assert sheet.declared == 0
    assert sheet.clean is True


def test_every_rendered_line_is_the_same_width():
    for line in render(HEADER, [received()]).splitlines():
        assert len(line) == LINE_WIDTH

import pytest
from click.testing import CliRunner

from drawbarline.cli import cli
from drawbarline.clock import utc
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.interchange.reconcile import compare
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.reports.reconcile import reconciliation_report

CN = ReportingMark("CN")

CAR = [
    "fleet",
    "add",
    "CN 400100",
    "--designation",
    "LO",
    "--type-code",
    "L351",
    "--built",
    "2020-06-01",
    "--light-weight",
    "63500",
    "--gross-rail-load",
    "286000",
    "--length",
    "59-6",
]


def received(number=400100, hour=12):
    return Event(
        EventKind.RECEIVED,
        CarMark.parse(f"CN {number}"),
        utc(2025, 1, 6, hour),
        "ESC",
        road=CN,
    )


def a_record(number="400100", clock="0700"):
    return f"IC{'RC'}{'CN':<4}{number}20250106{clock}041207{0:05d}{'':<12}".ljust(80)


def a_file(*records):
    header = f"HD{'CN':<4}{'ESC':<4}20250106{41:04d}".ljust(80)
    return "\n".join([header, *records, f"TR{len(records):06d}".ljust(80)]) + "\n"


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


def test_a_clean_comparison_reports_agreement():
    lines = reconciliation_report(compare([received()], [received()]), CN, "in.txt")
    assert lines[0] == "INTERCHANGE RECONCILIATION CN"
    assert "agreed: 1" in lines
    assert "on their record and not ours: none" in lines


def test_their_records_come_before_ours():
    found = compare([received(400101)], [received(400102)])
    lines = reconciliation_report(found, CN, "in.txt")
    theirs = lines.index("on their record and not ours: 1")
    ours = lines.index("on our record and not theirs: 1")
    assert theirs < ours


def test_a_record_only_they_have_is_listed():
    found = compare([], [received(400102)])
    lines = reconciliation_report(found, CN, "in.txt")
    assert any("CN 400102" in line for line in lines)


def test_the_timings_come_worst_first():
    found = compare(
        [received(400100, hour=12), received(400101, hour=12)],
        [received(400100, hour=14), received(400101, hour=20)],
    )
    lines = reconciliation_report(found, CN, "in.txt")
    rows = [line for line in lines if line.startswith("CN 4001")]
    assert rows[0].startswith("CN 400101")


def test_nothing_out_of_time_says_none():
    lines = reconciliation_report(compare([received()], [received()]), CN, "in.txt")
    assert "recorded at different times: none" in lines


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    run(database, *CAR)
    return database


def write(tmp_path, name, text):
    path = tmp_path / name
    path.write_text(text, encoding="utf-8")
    return str(path)


def test_a_matching_sheet_reconciles_clean(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    run(books, "interchange", "receive", path)
    found = run(books, "interchange", "reconcile", path)
    assert found.exit_code == 0
    assert "agreed: 1" in found.output


def test_a_sheet_we_never_took_shows_as_only_theirs(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    found = run(books, "interchange", "reconcile", path)
    assert found.exit_code != 0
    assert "on their record and not ours: 1" in found.output


def test_a_clock_difference_inside_the_tolerance_agrees(books, tmp_path):
    taken = write(tmp_path, "ours.txt", a_file(a_record(clock="0700")))
    run(books, "interchange", "receive", taken)
    theirs = write(tmp_path, "theirs.txt", a_file(a_record(clock="0720")))
    found = run(books, "interchange", "reconcile", theirs)
    assert found.exit_code == 0


def test_a_clock_difference_past_the_tolerance_is_reported(books, tmp_path):
    taken = write(tmp_path, "ours.txt", a_file(a_record(clock="0700")))
    run(books, "interchange", "receive", taken)
    theirs = write(tmp_path, "theirs.txt", a_file(a_record(clock="0900")))
    found = run(books, "interchange", "reconcile", theirs)
    assert found.exit_code != 0
    assert "recorded at different times: 1" in found.output


def test_the_tolerance_can_be_tightened_on_the_command_line(books, tmp_path):
    taken = write(tmp_path, "ours.txt", a_file(a_record(clock="0700")))
    run(books, "interchange", "receive", taken)
    theirs = write(tmp_path, "theirs.txt", a_file(a_record(clock="0720")))
    found = run(books, "interchange", "reconcile", theirs, "--tolerance", "5")
    assert found.exit_code != 0


def test_a_sheet_with_no_records_is_refused(books, tmp_path):
    path = write(tmp_path, "empty.txt", a_file())
    found = run(books, "interchange", "reconcile", path)
    assert found.exit_code != 0
    assert "no records to compare" in found.output


def test_a_sheet_with_no_header_is_refused(books, tmp_path):
    path = write(tmp_path, "bad.txt", a_record() + "\n")
    assert run(books, "interchange", "reconcile", path).exit_code != 0


def test_reconciling_changes_nothing(books, tmp_path):
    path = write(tmp_path, "in.txt", a_file(a_record()))
    run(books, "interchange", "receive", path)
    before = run(books, "status").output
    run(books, "interchange", "reconcile", path)
    assert run(books, "status").output == before

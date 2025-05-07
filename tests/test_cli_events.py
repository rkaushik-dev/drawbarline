import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

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

SHEET = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,moved,2025-01-06 13:00,PWR,,,,336
CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
"""


REPEATED = "CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,\n"


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    run(database, *CAR)
    return database


def test_an_event_is_recorded(books):
    found = run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    )
    assert found.exit_code == 0
    assert "recorded" in found.output


def test_a_recorded_event_shows_up_on_the_stream(books):
    run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    )
    found = run(books, "events", "show", "CN 400100")
    assert "received at ESC CN" in found.output
    assert "-> CN 400100 at ESC" in found.output


def test_the_local_reading_is_read_at_the_station(books):
    run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    )
    assert "2025-01-06 12:00Z" in run(books, "events", "show", "CN 400100").output


def test_an_interchange_without_a_road_is_refused(books):
    found = run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
    )
    assert found.exit_code != 0


def test_a_reading_that_never_happened_is_refused(books):
    found = run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "PWR",
        "--when",
        "2025-03-09 02:30",
        "--road",
        "CN",
    )
    assert found.exit_code != 0


def test_the_ambiguous_hour_needs_the_flag(books):
    first = run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "PWR",
        "--when",
        "2025-11-02 01:30",
        "--road",
        "CN",
    )
    assert first.exit_code == 0
    second = run(
        books,
        "events",
        "record",
        "CN 400100",
        "held",
        "--at",
        "PWR",
        "--when",
        "2025-11-02 01:30",
        "--second-pass",
    )
    assert second.exit_code == 0


def test_a_kind_nobody_publishes_is_refused(books):
    found = run(
        books,
        "events",
        "record",
        "CN 400100",
        "shunted",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
    )
    assert found.exit_code != 0


def test_the_same_event_twice_is_refused(books):
    args = [
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    ]
    run(books, *args)
    assert run(books, *args).exit_code != 0


def test_a_sheet_loads_in_one_go(books, tmp_path):
    sheet = tmp_path / "interchange.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    found = run(books, "events", "load", str(sheet))
    assert found.exit_code == 0
    assert "took 3 events" in found.output


def test_a_loaded_sheet_replays(books, tmp_path):
    sheet = tmp_path / "interchange.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(books, "events", "load", str(sheet))
    found = run(books, "events", "show", "CN 400100")
    assert "-> CN 400100 at PWRGRN1" in found.output


def test_a_sheet_that_repeats_a_line_names_the_line(books, tmp_path):
    sheet = tmp_path / "bad.csv"
    sheet.write_text(SHEET + REPEATED, encoding="utf-8")
    found = run(books, "events", "load", str(sheet))
    assert found.exit_code != 0
    assert "line 5" in found.output


def test_a_sheet_that_fails_leaves_nothing_behind(books, tmp_path):
    sheet = tmp_path / "bad.csv"
    sheet.write_text(SHEET + REPEATED, encoding="utf-8")
    run(books, "events", "load", str(sheet))
    assert "cars      1" in run(books, "status").output
    assert "events    0" in run(books, "status").output


def test_a_sheet_missing_a_column_is_refused(books, tmp_path):
    sheet = tmp_path / "short.csv"
    sheet.write_text("car,kind\nCN 400100,received\n", encoding="utf-8")
    found = run(books, "events", "load", str(sheet))
    assert found.exit_code != 0
    assert "missing" in found.output


def test_a_sheet_that_does_not_exist_is_refused(books):
    assert run(books, "events", "load", "nowhere.csv").exit_code != 0


def test_a_stream_for_a_car_with_nothing_on_it_reads_off_line(books):
    found = run(books, "events", "show", "CN 400100")
    assert "off line" in found.output


def test_the_status_counts_the_events(books, tmp_path):
    sheet = tmp_path / "interchange.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(books, "events", "load", str(sheet))
    assert "events    3" in run(books, "status").output

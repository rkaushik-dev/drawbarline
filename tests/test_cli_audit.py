import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

RATES = """type_code,from_age,to_age,effective,hourly,mileage
L351,0,9,2024-07-01,4630,1210
"""

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
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    (tmp_path / "rates.csv").write_text(RATES, encoding="utf-8")
    (tmp_path / "events.csv").write_text(SHEET, encoding="utf-8")
    run(database, "reference", "rates", str(tmp_path / "rates.csv"))
    run(
        database,
        "reference",
        "tariff",
        "4200",
        "--effective",
        "2024-07-01",
        "--free-loaded",
        "2",
        "--free-empty",
        "1",
        "--step",
        "0:20000",
    )
    run(database, *CAR)
    run(database, "events", "load", str(tmp_path / "events.csv"))
    return database


def test_the_trail_starts_empty(books):
    assert "nothing on the trail yet" in run(books, "audit").output


def test_a_close_writes_to_the_trail(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    found = run(books, "audit")
    assert "rjs closed a period 2025-01" in found.output


def test_an_unsigned_close_says_so(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10")
    assert "unsigned closed a period" in run(books, "audit").output


def test_the_trail_carries_what_the_close_did(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    found = run(books, "audit")
    assert "postings" in found.output
    assert "car hire" in found.output


def test_a_dry_run_writes_nothing_to_the_trail(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs", "--dry-run")
    assert "nothing on the trail yet" in run(books, "audit").output


def test_a_reopening_writes_its_reason_to_the_trail(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        books,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet from cn",
        "--by",
        "mkb",
    )
    found = run(books, "audit")
    assert "mkb reopened a period 2025-01" in found.output
    assert "corrected sheet from cn" in found.output


def test_the_newest_entry_comes_first(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        books,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet",
        "--by",
        "mkb",
    )
    lines = [line for line in run(books, "audit").output.splitlines() if line.strip()]
    assert "reopened" in lines[0]


def test_the_trail_can_be_read_for_one_person(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        books,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet",
        "--by",
        "mkb",
    )
    found = run(books, "audit", "--by", "rjs")
    assert "rjs" in found.output
    assert "mkb" not in found.output


def test_a_person_who_did_nothing_has_no_trail(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    assert "nothing on the trail yet" in run(books, "audit", "--by", "abc").output


def test_the_limit_holds(books):
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        books,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet",
        "--by",
        "rjs",
    )
    lines = [
        line for line in run(books, "audit", "--limit", "1").output.splitlines() if line.strip()
    ]
    assert len(lines) == 1


def test_a_close_that_fails_leaves_no_trail(books):
    run(books, "close", "2025-01", "--issued", "2025-01-20", "--by", "rjs")
    assert "nothing on the trail yet" in run(books, "audit").output


def test_a_reopening_of_an_open_month_leaves_no_trail(books):
    run(books, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "x", "--by", "rjs")
    assert "nothing on the trail yet" in run(books, "audit").output

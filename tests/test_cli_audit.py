import pytest

from conftest import run

SHEET = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


@pytest.fixture
def worked(books, tmp_path):
    """The loaded railroad with one car worked through january."""
    sheet = tmp_path / "events.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(worked, "events", "load", str(sheet))
    return books


def test_the_trail_starts_empty(worked):
    assert "nothing on the trail yet" in run(worked, "audit").output


def test_a_close_writes_to_the_trail(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    found = run(worked, "audit")
    assert "rjs closed a period 2025-01" in found.output


def test_an_unsigned_close_says_so(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10")
    assert "unsigned closed a period" in run(worked, "audit").output


def test_the_trail_carries_what_the_close_did(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    found = run(worked, "audit")
    assert "postings" in found.output
    assert "car hire" in found.output


def test_a_dry_run_writes_nothing_to_the_trail(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs", "--dry-run")
    assert "nothing on the trail yet" in run(worked, "audit").output


def test_a_reopening_writes_its_reason_to_the_trail(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        worked,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet from cn",
        "--by",
        "mkb",
    )
    found = run(worked, "audit")
    assert "mkb reopened a period 2025-01" in found.output
    assert "corrected sheet from cn" in found.output


def test_the_newest_entry_comes_first(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        worked,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet",
        "--by",
        "mkb",
    )
    lines = [line for line in run(worked, "audit").output.splitlines() if line.strip()]
    assert "reopened" in lines[0]


def test_the_trail_can_be_read_for_one_person(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        worked,
        "reopen",
        "2025-01",
        "--on",
        "2025-03-01",
        "--reason",
        "corrected sheet",
        "--by",
        "mkb",
    )
    found = run(worked, "audit", "--by", "rjs")
    assert "rjs" in found.output
    assert "mkb" not in found.output


def test_a_person_who_did_nothing_has_no_trail(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    assert "nothing on the trail yet" in run(worked, "audit", "--by", "abc").output


def test_the_limit_holds(worked):
    run(worked, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    run(
        worked,
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
        line for line in run(worked, "audit", "--limit", "1").output.splitlines() if line.strip()
    ]
    assert len(lines) == 1


def test_a_close_that_fails_leaves_no_trail(worked):
    run(worked, "close", "2025-01", "--issued", "2025-01-20", "--by", "rjs")
    assert "nothing on the trail yet" in run(worked, "audit").output


def test_a_reopening_of_an_open_month_leaves_no_trail(worked):
    run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "x", "--by", "rjs")
    assert "nothing on the trail yet" in run(worked, "audit").output

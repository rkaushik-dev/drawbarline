import json

import pytest

from conftest import run
from drawbarline.export import SHAPE, VERSION, counts, month
from drawbarline.persistence.base import make_engine
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.settlement.period import Period

JANUARY = Period(2025, 1)

SHEET = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


@pytest.fixture
def closed(books, tmp_path):
    sheet = tmp_path / "events.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(books, "events", "load", str(sheet))
    run(books, "close", "2025-01", "--issued", "2025-02-10", "--by", "rjs")
    return books


def bundle_of(database):
    with UnitOfWork(make_engine(f"sqlite+pysqlite:///{database}")) as work:
        return month(work, JANUARY)


def test_a_bundle_says_what_it_is(closed):
    found = bundle_of(closed)
    assert found["shape"] == SHAPE
    assert found["version"] == VERSION


def test_a_bundle_names_its_month(closed):
    assert bundle_of(closed)["period"] == "2025-01"


def test_a_closed_month_says_so(closed):
    assert bundle_of(closed)["closed"] is True


def test_an_open_month_says_so(books):
    assert bundle_of(books)["closed"] is False


def test_a_bundle_carries_the_postings(closed):
    found = bundle_of(closed)["postings"]
    assert [one["road"] for one in found] == ["CN"]
    assert found[0]["kind"] == "car_hire"
    assert found[0]["cents"] > 0


def test_a_bundle_carries_the_statements(closed):
    found = bundle_of(closed)["statements"]
    assert found[0]["road"] == "CN"
    assert found[0]["closing_cents"] > 0
    assert found[0]["direction"] == "we pay CN"


def test_a_statement_carries_its_lines(closed):
    found = bundle_of(closed)["statements"][0]
    assert [one["kind"] for one in found["lines"]] == ["car_hire"]


def test_a_bundle_carries_the_events(closed):
    found = bundle_of(closed)["events"]
    assert len(found) == 2
    assert {one["kind"] for one in found} == {"received", "delivered"}


def test_an_event_carries_its_moment_in_utc(closed):
    found = bundle_of(closed)["events"][0]
    assert found["moment"].endswith("+00:00")


def test_a_bundle_carries_the_audit_trail(closed):
    found = bundle_of(closed)["audit"]
    assert found[0]["actor"] == "rjs"
    assert found[0]["action"] == "closed_period"


def test_a_bundle_says_whether_the_month_balances(closed):
    assert bundle_of(closed)["balances"] is True


def test_a_bundle_is_json(closed):
    text = json.dumps(bundle_of(closed))
    assert json.loads(text)["period"] == "2025-01"


def test_a_month_with_nothing_in_it_still_exports(books):
    found = bundle_of(books)
    assert found["postings"] == []
    assert found["events"] == []


def test_the_counts_say_how_much_is_in_it(closed):
    found = counts(bundle_of(closed))
    assert found["events"] == 2
    assert found["postings"] == 1


def test_the_export_command_writes_to_standard_output(closed):
    found = run(closed, "export", "2025-01")
    assert found.exit_code == 0
    assert json.loads(found.output)["period"] == "2025-01"


def test_the_export_command_writes_a_file(closed, tmp_path):
    out = tmp_path / "january.json"
    found = run(closed, "export", "2025-01", "--out", str(out))
    assert found.exit_code == 0
    assert "2 events" in found.output
    assert json.loads(out.read_text(encoding="utf-8"))["period"] == "2025-01"


def test_a_month_that_cannot_be_read_is_refused(closed):
    assert run(closed, "export", "january").exit_code != 0


def test_exporting_changes_nothing(closed):
    before = run(closed, "status").output
    run(closed, "export", "2025-01")
    assert run(closed, "status").output == before

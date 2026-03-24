"""One month, worked all the way through, from an empty database.

Every other module in this suite tests one thing. This works a month the way
the office works one, through the command line, and then reads the same month
back four different ways and checks the four agree. It is slow by the
standards of the rest of the suite and it is the test that would have caught
every integration fault this application has actually had.

The numbers are not asserted against constants. They are asserted against each
other, because the property that matters is not that car hire came to a
particular figure, it is that the statement, the trial balance, the export and
the report all say the same figure.
"""

from __future__ import annotations

import json

import pytest

from conftest import car_args, run

INTERCHANGE = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,moved,2025-01-06 13:00,PWR,,,,336
CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
CN 400101,received,2025-01-08 07:00,ESC,CN,,41208,
CN 400101,bad_ordered,2025-01-09 08:00,ESC,,,,
CN 400101,repaired,2025-01-12 08:00,ESC,,,,
CN 400101,delivered,2025-01-20 09:00,MRN,CN,,,
"""

CARD = [
    "repairs",
    "card",
    "88104",
    "CN 400101",
    "--by",
    "CN",
    "--on",
    "2025-01-10",
    "--at",
    "ESC",
    "--defect",
    "4312:01:1:5",
]

CLOSE = ("close", "2025-01", "--issued", "2025-02-10")


@pytest.fixture
def worked(books, tmp_path):
    """A month of traffic on the loaded railroad, not yet closed."""
    sheet = tmp_path / "interchange.csv"
    sheet.write_text(INTERCHANGE, encoding="utf-8")
    assert run(books, "events", "load", str(sheet)).exit_code == 0
    assert run(books, *CARD).exit_code == 0
    return books


def money_in(text, after):
    line = [one for one in text.splitlines() if one.startswith(after)][0]
    return [word for word in line.split() if word.startswith(("$", "-$"))][-1]


def closing_of(database):
    statement = run(database, "statement", "CN", "2025-01").output
    return [one for one in statement.splitlines() if one.startswith("closing")][0]


def test_the_books_are_ready_before_the_month_is_worked(books):
    assert run(books, "doctor", "--on", "2025-01-14").exit_code == 0


def test_the_month_loads(worked):
    found = run(worked, "status")
    assert "events    10" in found.output
    assert "postings  0" in found.output


def test_a_dry_run_leaves_the_month_open(worked):
    run(worked, *CLOSE, "--dry-run")
    assert "nothing closed yet" in run(worked, "periods").output


def test_the_month_closes(worked):
    found = run(worked, *CLOSE, "--by", "rjs")
    assert found.exit_code == 0
    assert "closed 2025-01" in found.output


def test_the_close_is_signed(worked):
    run(worked, *CLOSE, "--by", "rjs")
    assert "rjs closed a period 2025-01" in run(worked, "audit").output


def test_the_month_cannot_be_closed_twice(worked):
    run(worked, *CLOSE)
    assert run(worked, *CLOSE).exit_code != 0


def test_the_trial_balance_balances(worked):
    run(worked, *CLOSE)
    found = run(worked, "report", "balance", "2025-01")
    assert found.output.rstrip().endswith("balances")


def test_the_statement_and_the_trial_balance_agree(worked):
    run(worked, *CLOSE)
    balance = run(worked, "report", "balance", "2025-01").output
    assert closing_of(worked).split()[-1] in balance


def test_the_export_carries_the_same_closing_balance(worked):
    run(worked, *CLOSE)
    bundle = json.loads(run(worked, "export", "2025-01").output)
    cents = bundle["statements"][0]["closing_cents"]
    assert f"${cents / 100:,.2f}" == closing_of(worked).split()[-1]


def test_the_export_says_the_month_is_closed(worked):
    run(worked, *CLOSE)
    bundle = json.loads(run(worked, "export", "2025-01").output)
    assert bundle["closed"] is True
    assert bundle["balances"] is True


def test_the_export_carries_every_event(worked):
    run(worked, *CLOSE)
    bundle = json.loads(run(worked, "export", "2025-01").output)
    assert len(bundle["events"]) == 10


def test_the_car_hire_report_names_the_only_connection(worked):
    found = run(worked, "report", "carhire", "2025-01")
    assert "CN" in found.output
    assert "all roads" in found.output


def test_the_reclaim_shows_up_against_the_car_hire(worked):
    found = run(worked, *CLOSE)
    assert money_in(found.output, "reclaimed") != "$0.00"


def test_the_bad_ordered_car_is_on_the_shop_report(worked):
    found = run(worked, "report", "shop", "--at", "2025-01-11")
    assert "CN 400101" in found.output
    assert "1 down" in found.output


def test_the_repair_card_reaches_the_billing_report(worked):
    found = run(worked, "report", "repairs", "2025-01")
    assert "CN" in found.output
    assert "1 roads" in found.output


def test_the_demurrage_invoice_is_cut(worked):
    found = run(worked, *CLOSE)
    assert "invoice    demurrage invoice" in found.output
    assert "PWRGRN" in found.output


def test_the_close_package_carries_every_section(worked):
    run(worked, *CLOSE)
    found = run(worked, "report", "package", "2025-01")
    for heading in (
        "TRIAL BALANCE",
        "CAR HIRE LIABILITY",
        "DEMURRAGE BY SHIPPER",
        "REPAIR BILLING",
        "INTERCHANGE BALANCE",
        "FLEET UTILISATION",
    ):
        assert heading in found.output


def test_the_package_opens_with_the_trial_balance(worked):
    run(worked, *CLOSE)
    found = run(worked, "report", "package", "2025-01")
    assert found.output.splitlines()[0] == "TRIAL BALANCE"


def test_a_reopened_month_can_be_worked_again(worked):
    run(worked, *CLOSE, "--by", "rjs")
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
    assert run(worked, "close", "2025-01", "--issued", "2025-03-05").exit_code == 0
    trail = run(worked, "audit").output
    assert "mkb reopened a period" in trail
    assert trail.count("closed a period 2025-01") == 2


def test_the_month_still_balances_after_a_reopening(worked):
    run(worked, *CLOSE)
    run(worked, "reopen", "2025-01", "--on", "2025-03-01", "--reason", "corrected")
    run(worked, "close", "2025-01", "--issued", "2025-03-05")
    found = run(worked, "report", "balance", "2025-01")
    assert found.output.rstrip().endswith("balances")


def test_a_car_filed_after_the_close_does_not_move_it(worked):
    run(worked, *CLOSE)
    before = run(worked, "report", "balance", "2025-01").output
    run(worked, *car_args("DBL 104209"))
    assert run(worked, "report", "balance", "2025-01").output == before

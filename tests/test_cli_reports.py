import pytest

from conftest import run

SHEET = """car,kind,when,station,road,industry,waybill,tenths
CN 400100,received,2025-01-06 07:00,ESC,CN,,41207,
CN 400100,moved,2025-01-06 13:00,PWR,,,,336
CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


@pytest.fixture
def worked(books, tmp_path):
    """The loaded railroad with one car worked through january."""
    sheet = tmp_path / "events.csv"
    sheet.write_text(SHEET, encoding="utf-8")
    run(books, "events", "load", str(sheet))
    return books


def test_the_car_hire_report_prints(worked):
    found = run(worked, "report", "carhire", "2025-01")
    assert found.exit_code == 0
    assert "CAR HIRE LIABILITY" in found.output
    assert "CN" in found.output


def test_the_car_hire_detail_prints_for_one_road(worked):
    found = run(worked, "report", "carhire", "2025-01", "--road", "CN")
    assert "CAR HIRE DETAIL CN" in found.output
    assert "CN 400100" in found.output


def test_a_month_that_cannot_be_read_is_refused(worked):
    assert run(worked, "report", "carhire", "january").exit_code != 0


def test_the_utilisation_report_prints(worked):
    found = run(worked, "report", "utilisation", "2025-01")
    assert "FLEET UTILISATION" in found.output
    assert "CN 400100" in found.output


def test_the_demurrage_report_prints_car_by_car(worked):
    found = run(worked, "report", "demurrage", "2025-01")
    assert "DEMURRAGE DETENTION" in found.output
    assert "PWRGRN1" in found.output


def test_the_demurrage_report_summarises_by_shipper(worked):
    found = run(worked, "report", "demurrage", "2025-01", "--by-shipper")
    assert "DEMURRAGE BY SHIPPER" in found.output
    assert "Powers Grain" in found.output


def test_the_interchange_report_prints(worked):
    found = run(worked, "report", "interchange", "2025-01")
    assert "INTERCHANGE BALANCE" in found.output
    assert "ESC" in found.output
    assert "MRN" in found.output


def test_the_on_hand_report_prints(worked):
    found = run(worked, "report", "onhand", "--at", "2025-01-10")
    assert "CARS ON HAND" in found.output
    assert "CN 400100" in found.output


def test_a_car_already_delivered_is_not_on_hand(worked):
    found = run(worked, "report", "onhand", "--at", "2025-01-20")
    assert "0 cars" in found.output


def test_the_on_hand_report_defaults_to_now(worked):
    found = run(worked, "report", "onhand")
    assert found.exit_code == 0
    assert "CARS ON HAND" in found.output


def test_the_age_report_prints(worked):
    found = run(worked, "report", "age", "--on", "2025-07-01")
    assert "FLEET AGE PROFILE" in found.output
    assert "all cars" in found.output


def test_the_age_report_defaults_to_today(worked):
    assert run(worked, "report", "age").exit_code == 0


def test_a_date_that_cannot_be_read_is_refused(worked):
    assert run(worked, "report", "age", "--on", "yesterday").exit_code != 0


def test_a_report_changes_nothing(worked):
    before = run(worked, "status").output
    run(worked, "report", "carhire", "2025-01")
    assert run(worked, "status").output == before


def test_every_report_runs_on_an_empty_set_of_books(empty):
    run(
        empty,
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
    for args in (
        ("report", "carhire", "2025-01"),
        ("report", "utilisation", "2025-01"),
        ("report", "demurrage", "2025-01"),
        ("report", "interchange", "2025-01"),
        ("report", "onhand"),
        ("report", "age"),
    ):
        assert run(empty, *args).exit_code == 0

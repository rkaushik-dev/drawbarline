import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

RATES = """type_code,from_age,to_age,effective,hourly,mileage
L351,0,9,2024-07-01,4630,1210
L351,10,99,2024-07-01,3180,1210
"""

SHIPPERS = """code,name,average_agreement
PWRGRN,Powers Grain,yes
"""

INDUSTRIES = """code,name,station,shipper,spots,switched
PWRGRN1,elevator,PWR,PWRGRN,20,
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
CN 400100,moved,2025-01-06 13:00,PWR,,,,336
CN 400100,placed,2025-01-07 08:00,PWR,,PWRGRN1,41207,
CN 400100,released,2025-01-14 15:00,PWR,,PWRGRN1,,
CN 400100,pulled,2025-01-15 17:00,PWR,,PWRGRN1,,
CN 400100,delivered,2025-01-16 09:00,MRN,CN,,,
"""


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    for name, text in (
        ("rates.csv", RATES),
        ("shippers.csv", SHIPPERS),
        ("industries.csv", INDUSTRIES),
        ("events.csv", SHEET),
    ):
        (tmp_path / name).write_text(text, encoding="utf-8")
    run(database, "reference", "rates", str(tmp_path / "rates.csv"))
    run(database, "reference", "shippers", str(tmp_path / "shippers.csv"))
    run(database, "reference", "industries", str(tmp_path / "industries.csv"))
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
        "2:10000",
        "--step",
        "0:20000",
    )
    run(database, *CAR)
    run(database, "events", "load", str(tmp_path / "events.csv"))
    return database


def test_the_car_hire_report_prints(books):
    found = run(books, "report", "carhire", "2025-01")
    assert found.exit_code == 0
    assert "CAR HIRE LIABILITY" in found.output
    assert "CN" in found.output


def test_the_car_hire_detail_prints_for_one_road(books):
    found = run(books, "report", "carhire", "2025-01", "--road", "CN")
    assert "CAR HIRE DETAIL CN" in found.output
    assert "CN 400100" in found.output


def test_a_month_that_cannot_be_read_is_refused(books):
    assert run(books, "report", "carhire", "january").exit_code != 0


def test_the_utilisation_report_prints(books):
    found = run(books, "report", "utilisation", "2025-01")
    assert "FLEET UTILISATION" in found.output
    assert "CN 400100" in found.output


def test_the_demurrage_report_prints_car_by_car(books):
    found = run(books, "report", "demurrage", "2025-01")
    assert "DEMURRAGE DETENTION" in found.output
    assert "PWRGRN1" in found.output


def test_the_demurrage_report_summarises_by_shipper(books):
    found = run(books, "report", "demurrage", "2025-01", "--by-shipper")
    assert "DEMURRAGE BY SHIPPER" in found.output
    assert "Powers Grain" in found.output


def test_the_interchange_report_prints(books):
    found = run(books, "report", "interchange", "2025-01")
    assert "INTERCHANGE BALANCE" in found.output
    assert "ESC" in found.output
    assert "MRN" in found.output


def test_the_on_hand_report_prints(books):
    found = run(books, "report", "onhand", "--at", "2025-01-10")
    assert "CARS ON HAND" in found.output
    assert "CN 400100" in found.output


def test_a_car_already_delivered_is_not_on_hand(books):
    found = run(books, "report", "onhand", "--at", "2025-01-20")
    assert "0 cars" in found.output


def test_the_on_hand_report_defaults_to_now(books):
    found = run(books, "report", "onhand")
    assert found.exit_code == 0
    assert "CARS ON HAND" in found.output


def test_the_age_report_prints(books):
    found = run(books, "report", "age", "--on", "2025-07-01")
    assert "FLEET AGE PROFILE" in found.output
    assert "all cars" in found.output


def test_the_age_report_defaults_to_today(books):
    assert run(books, "report", "age").exit_code == 0


def test_a_date_that_cannot_be_read_is_refused(books):
    assert run(books, "report", "age", "--on", "yesterday").exit_code != 0


def test_a_report_changes_nothing(books):
    before = run(books, "status").output
    run(books, "report", "carhire", "2025-01")
    assert run(books, "status").output == before


def test_every_report_runs_on_an_empty_set_of_books(tmp_path):
    database = str(tmp_path / "empty.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
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
    for args in (
        ("report", "carhire", "2025-01"),
        ("report", "utilisation", "2025-01"),
        ("report", "demurrage", "2025-01"),
        ("report", "interchange", "2025-01"),
        ("report", "onhand"),
        ("report", "age"),
    ):
        assert run(database, *args).exit_code == 0

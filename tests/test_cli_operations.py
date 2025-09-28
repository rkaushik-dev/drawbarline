import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

SHIPPERS = """code,name,average_agreement
ESCPAP,Escanaba Paper,no
"""

INDUSTRIES = """code,name,station,shipper,spots,switched
ESCPAP1,mill house track,ESC,ESCPAP,2,
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

WAYBILL = [
    "waybill",
    "issue",
    "41207",
    "CN 400100",
    "--dated",
    "2025-01-14",
    "--shipper",
    "PWRGRN",
    "--consignee",
    "ESCPAP",
    "--route",
    "PWR,HRM,BKR,ESC",
    "--commodity",
    "0113710",
    "--commodity-name",
    "wheat",
    "--pounds",
    "198000",
]


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    CliRunner().invoke(cli, ["--database", database, "init"])
    (tmp_path / "shippers.csv").write_text(SHIPPERS, encoding="utf-8")
    (tmp_path / "industries.csv").write_text(INDUSTRIES, encoding="utf-8")
    run(database, "reference", "shippers", str(tmp_path / "shippers.csv"))
    run(database, "reference", "industries", str(tmp_path / "industries.csv"))
    for number in ("CN 400100", "CN 400101", "CN 400102"):
        args = list(CAR)
        args[2] = number
        run(database, *args)
    return database


def test_a_waybill_is_issued(books):
    found = run(books, *WAYBILL)
    assert found.exit_code == 0
    assert "issued wb 41207" in found.output


def test_a_waybill_shows_its_route_and_mileage(books):
    run(books, *WAYBILL)
    found = run(books, "waybill", "show", "41207")
    assert "route      PWR HRM BKR ESC" in found.output
    assert "miles      33.6" in found.output


def test_a_loaded_waybill_shows_its_lading(books):
    run(books, *WAYBILL)
    assert "lading     wheat" in run(books, "waybill", "show", "41207").output


def test_an_empty_waybill_says_empty(books):
    run(books, *WAYBILL[:12])
    assert "lading     empty" in run(books, "waybill", "show", "41207").output


def test_a_lading_with_no_weight_is_refused(books):
    args = [*WAYBILL[:12], "--commodity", "0113710"]
    assert run(books, *args).exit_code != 0


def test_a_hazardous_waybill_says_so(books):
    args = list(WAYBILL)
    args[args.index("0113710")] = "4930040"
    run(books, *args)
    assert "hazardous" in run(books, "waybill", "show", "41207").output


def test_the_same_number_twice_is_refused(books):
    run(books, *WAYBILL)
    assert run(books, *WAYBILL).exit_code != 0


def test_a_waybill_nobody_issued_cannot_be_shown(books):
    assert run(books, "waybill", "show", "99999").exit_code != 0


def test_an_embargo_is_issued(books):
    found = run(
        books,
        "embargo",
        "issue",
        "101",
        "--effective",
        "2025-01-06",
        "--reason",
        "bridge out",
        "--station",
        "HRM",
    )
    assert found.exit_code == 0
    assert "issued embargo 101 at HRM" in found.output


def test_an_embargo_that_restricts_nothing_is_refused(books):
    found = run(
        books,
        "embargo",
        "issue",
        "101",
        "--effective",
        "2025-01-06",
        "--reason",
        "everything",
    )
    assert found.exit_code != 0


def test_the_embargo_list_prints(books):
    run(
        books,
        "embargo",
        "issue",
        "101",
        "--effective",
        "2025-01-06",
        "--reason",
        "bridge out",
        "--station",
        "HRM",
    )
    found = run(books, "embargo", "list")
    assert "EMBARGOES" in found.output
    assert "1 embargoes" in found.output
    assert "open" in found.output


def test_the_list_can_be_asked_for_one_day(books):
    run(
        books,
        "embargo",
        "issue",
        "101",
        "--effective",
        "2025-01-06",
        "--reason",
        "bridge out",
        "--station",
        "HRM",
    )
    assert "0 embargoes" in run(books, "embargo", "list", "--on", "2025-01-05").output


def test_an_embargo_is_lifted(books):
    run(
        books,
        "embargo",
        "issue",
        "101",
        "--effective",
        "2025-01-06",
        "--reason",
        "bridge out",
        "--station",
        "HRM",
    )
    found = run(books, "embargo", "lift", "101", "--on", "2025-01-20")
    assert "lifted embargo 101" in found.output
    assert "0 embargoes" in run(books, "embargo", "list", "--on", "2025-01-21").output


def test_lifting_something_never_issued_is_refused(books):
    assert run(books, "embargo", "lift", "101", "--on", "2025-01-20").exit_code != 0


def test_an_empty_switch_list_says_nothing_to_do(books):
    found = run(books, "switchlist", "ESC", "--day", "2025-01-14", "--at", "2025-01-14")
    assert "nothing to do" in found.output
    assert "worked 0 of 0" in found.output


def test_an_order_places_a_car(books):
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
    found = run(
        books,
        "switchlist",
        "ESC",
        "--day",
        "2025-01-14",
        "--at",
        "2025-01-14",
        "--order",
        "CN 400100=ESCPAP1",
    )
    assert "place CN 400100 ESCPAP1" in found.output
    assert "worked 1 of 1" in found.output


def test_a_full_track_takes_a_constructive_placement(books):
    for number in ("CN 400100", "CN 400101", "CN 400102"):
        run(
            books,
            "events",
            "record",
            number,
            "received",
            "--at",
            "ESC",
            "--when",
            "2025-01-06 07:00",
            "--road",
            "CN",
        )
    for number in ("CN 400100", "CN 400101"):
        run(
            books,
            "events",
            "record",
            number,
            "placed",
            "--at",
            "ESC",
            "--when",
            "2025-01-07 08:00",
            "--industry",
            "ESCPAP1",
        )
    found = run(
        books,
        "switchlist",
        "ESC",
        "--day",
        "2025-01-14",
        "--at",
        "2025-01-14",
        "--order",
        "CN 400102=ESCPAP1",
    )
    assert "constructive CN 400102 ESCPAP1" in found.output
    assert "2 on hand" in found.output


def test_an_order_that_cannot_be_read_is_refused(books):
    found = run(books, "switchlist", "ESC", "--day", "2025-01-14", "--order", "nonsense")
    assert found.exit_code != 0


def test_an_order_for_a_car_not_on_our_rail_is_refused(books):
    found = run(
        books,
        "switchlist",
        "ESC",
        "--day",
        "2025-01-14",
        "--at",
        "2025-01-14",
        "--order",
        "CN 400100=ESCPAP1",
    )
    assert found.exit_code != 0

import pytest

from conftest import run

ORDER = [
    "order",
    "place",
    "1",
    "ESCPAP",
    "--track",
    "ESCPAP1",
    "--kind",
    "L",
    "--cars",
    "2",
    "--wanted",
    "2025-01-14",
    "--on",
    "2025-01-10",
]


@pytest.fixture
def stocked(books):
    """The loaded railroad with three empties standing at Powers."""
    for mark in ("CN 400100", "CN 400101", "CN 400102"):
        run(
            books,
            "events",
            "record",
            mark,
            "received",
            "--at",
            "ESC",
            "--when",
            "2025-01-06 07:00",
            "--road",
            "CN",
        )
        run(
            books,
            "events",
            "record",
            mark,
            "moved",
            "--at",
            "PWR",
            "--when",
            "2025-01-06 13:00",
            "--tenths",
            "336",
        )
    return books


def test_an_order_is_taken(books):
    found = run(books, *ORDER)
    assert found.exit_code == 0
    assert "took order 1 ESCPAP 2 covered hopper" in found.output


def test_an_order_for_a_shipper_nobody_filed_is_refused(books):
    args = list(ORDER)
    args[3] = "NOBODY"
    assert run(books, *args).exit_code != 0


def test_an_order_placed_after_the_day_it_wants_is_refused(books):
    args = list(ORDER)
    args[args.index("2025-01-10")] = "2025-01-20"
    assert run(books, *args).exit_code != 0


def test_a_kind_nobody_publishes_is_refused(books):
    args = list(ORDER)
    args[args.index("L")] = "Z"
    assert run(books, *args).exit_code != 0


def test_an_order_is_cancelled(books):
    run(books, *ORDER)
    found = run(books, "order", "cancel", "1", "--on", "2025-01-12")
    assert found.exit_code == 0
    assert "cancelled order 1" in found.output


def test_cancelling_twice_is_refused(books):
    run(books, *ORDER)
    run(books, "order", "cancel", "1", "--on", "2025-01-12")
    assert run(books, "order", "cancel", "1", "--on", "2025-01-13").exit_code != 0


def test_the_empties_report_prints(stocked):
    found = run(stocked, "empties", "--at", "2025-01-10")
    assert "EMPTIES ON HAND" in found.output
    assert "3 empties" in found.output
    assert "PWR" in found.output


def test_a_loaded_car_is_not_an_empty(books):
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
        "--waybill",
        "41207",
    )
    assert "0 empties" in run(books, "empties", "--at", "2025-01-10").output


def test_the_distribution_fills_an_order(stocked):
    run(stocked, *ORDER)
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    assert "CAR DISTRIBUTION" in found.output
    assert "2 cars" in found.output
    assert "every order filled" in found.output


def test_the_distribution_names_the_shipper(stocked):
    run(stocked, *ORDER)
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    assert "ESCPAP" in found.output


def test_the_distribution_reports_the_empty_mileage(stocked):
    run(stocked, *ORDER)
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    assert "67.2" in found.output


def test_an_order_bigger_than_the_fleet_goes_short(stocked):
    args = list(ORDER)
    args[args.index("2")] = "5"
    run(stocked, *args)
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    assert "orders not filled" in found.output
    assert "1 orders" in found.output


def test_a_cancelled_order_is_not_filled(stocked):
    run(stocked, *ORDER)
    run(stocked, "order", "cancel", "1", "--on", "2025-01-11")
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-12")
    assert "0 cars" in found.output


def test_a_day_with_no_orders_distributes_nothing(stocked):
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    assert "0 cars" in found.output
    assert "every order filled" in found.output


def test_a_date_that_cannot_be_read_is_refused(books):
    assert run(books, "distribute", "--on", "tuesday").exit_code != 0


def test_the_distribution_changes_nothing(stocked):
    run(stocked, *ORDER)
    before = run(stocked, "status").output
    run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    assert run(stocked, "status").output == before


def test_every_line_fits_the_paper(stocked):
    run(stocked, *ORDER)
    found = run(stocked, "distribute", "--on", "2025-01-14", "--at", "2025-01-10")
    for line in found.output.splitlines():
        assert len(line) <= 80

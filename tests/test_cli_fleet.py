import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

CAR = [
    "fleet",
    "add",
    "DBL 104207",
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


@pytest.fixture
def books(tmp_path):
    database = str(tmp_path / "books.db")
    runner = CliRunner()
    assert runner.invoke(cli, ["--database", database, "init"]).exit_code == 0
    return database


def run(database, *args):
    return CliRunner().invoke(cli, ["--database", database, *args])


def test_init_builds_the_database(tmp_path):
    database = str(tmp_path / "books.db")
    found = CliRunner().invoke(cli, ["--database", database, "init"])
    assert found.exit_code == 0
    assert "built" in found.output


def test_init_twice_is_refused(books):
    found = run(books, "init")
    assert found.exit_code != 0


def test_status_reads_an_empty_set_of_books(books):
    found = run(books, "status")
    assert "cars      0" in found.output
    assert "closed    0 months" in found.output


def test_a_car_is_filed_and_shows_up(books):
    assert run(books, *CAR).exit_code == 0
    found = run(books, "fleet", "list")
    assert "DBL 104207" in found.output
    assert "1 cars" in found.output


def test_the_same_car_twice_is_refused(books):
    run(books, *CAR)
    found = run(books, *CAR)
    assert found.exit_code != 0


def test_a_car_that_cannot_be_read_is_refused(books):
    found = run(books, *[*CAR[:2], "not a car", *CAR[3:]])
    assert found.exit_code != 0


def test_a_car_whose_codes_disagree_is_refused(books):
    bad = list(CAR)
    bad[bad.index("L351")] = "X202"
    assert run(books, *bad).exit_code != 0


def test_a_car_heavier_than_its_gross_rail_load_is_refused(books):
    bad = list(CAR)
    bad[bad.index("63500")] = "300000"
    assert run(books, *bad).exit_code != 0


def test_show_prints_the_stencil_and_the_dates(books):
    run(books, *CAR)
    found = run(books, "fleet", "show", "DBL 104207")
    assert "DBL 104207-6" in found.output
    assert "2070-06-01" in found.output
    assert "covered hopper" in found.output


def test_show_prints_the_load_limit(books):
    run(books, *CAR)
    assert "222,500 lb" in run(books, "fleet", "show", "DBL 104207").output


def test_show_on_a_car_nobody_filed_is_refused(books):
    assert run(books, "fleet", "show", "WC 1").exit_code != 0


def test_a_rebuilt_car_shows_its_rebuild(books):
    run(books, *CAR, "--rebuilt", "2022-09-01")
    assert "rebuilt      2022-09-01" in run(books, "fleet", "show", "DBL 104207").output


def test_the_list_filters_by_owner(books):
    run(books, *CAR)
    other = list(CAR)
    other[2] = "CN 400100"
    run(books, *other)
    assert "1 cars" in run(books, "fleet", "list", "--owner", "cn").output
    assert "2 cars" in run(books, "fleet", "list").output


def test_a_car_is_retired(books):
    run(books, *CAR)
    assert run(books, "fleet", "retire", "DBL 104207", "--on", "2025-03-31").exit_code == 0
    assert "retired      2025-03-31" in run(books, "fleet", "show", "DBL 104207").output


def test_retiring_twice_is_refused(books):
    run(books, *CAR)
    run(books, "fleet", "retire", "DBL 104207", "--on", "2025-03-31")
    found = run(books, "fleet", "retire", "DBL 104207", "--on", "2025-06-30")
    assert found.exit_code != 0


def test_a_filing_that_fails_leaves_nothing_behind(books):
    bad = list(CAR)
    bad[bad.index("63500")] = "300000"
    run(books, *bad)
    assert "0 cars" in run(books, "fleet", "list").output


def test_the_status_counts_the_cars(books):
    run(books, *CAR)
    assert "cars      1" in run(books, "status").output


def test_the_version_is_available(books):
    found = run(books, "--version")
    assert found.exit_code == 0

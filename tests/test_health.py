from datetime import date

from conftest import run
from drawbarline.health import Level, examine, ready
from drawbarline.persistence.base import make_engine
from drawbarline.persistence.numbering import SERIES
from drawbarline.persistence.uow import UnitOfWork

DAY = date(2025, 1, 14)


def findings_of(database):
    with UnitOfWork(make_engine(f"sqlite+pysqlite:///{database}")) as work:
        return examine(work, DAY)


def named(findings, check):
    return [one for one in findings if one.check == check][0]


def test_a_level_says_whether_it_stops_a_close():
    assert Level.BAD.stops_a_close is True
    assert Level.WARN.stops_a_close is False
    assert Level.GOOD.stops_a_close is False


def test_a_database_with_no_schema_is_not_ready():
    with UnitOfWork(make_engine()) as work:
        found = examine(work, DAY)
    assert named(found, "schema").level is Level.BAD
    assert ready(found) is False


def test_a_fresh_database_has_its_schema_and_counters(empty):
    found = findings_of(empty)
    assert named(found, "schema").level is Level.GOOD
    assert named(found, "numbering").level is Level.GOOD


def test_a_fresh_database_has_no_rates_and_is_not_ready(empty):
    found = findings_of(empty)
    assert named(found, "car hire rates").level is Level.BAD
    assert ready(found) is False


def test_a_fresh_database_has_no_tariff(empty):
    assert named(findings_of(empty), "demurrage tariff").level is Level.BAD


def test_a_loaded_railroad_is_ready(books):
    found = findings_of(books)
    assert named(found, "car hire rates").level is Level.GOOD
    assert named(found, "demurrage tariff").level is Level.GOOD
    assert ready(found) is True


def test_a_loaded_railroad_has_track_and_cars(books):
    found = findings_of(books)
    assert named(found, "industry track").level is Level.GOOD
    assert named(found, "fleet").level is Level.GOOD


def test_no_agreements_is_a_warning_and_not_a_stop(books):
    found = findings_of(books)
    assert named(found, "agreements").level is Level.WARN
    assert ready(found) is True


def test_every_check_is_reported_once(books):
    found = findings_of(books)
    assert len({one.check for one in found}) == len(found)


def test_a_finding_prints_its_level_and_check(books):
    assert str(named(findings_of(books), "schema")).startswith("ok    schema")


def test_a_bad_finding_carries_a_remedy(empty):
    found = named(findings_of(empty), "car hire rates")
    assert found.remedy
    assert found.remedy in str(found)


def test_a_good_finding_does_not_shout_a_remedy(books):
    assert "(" not in str(named(findings_of(books), "schema"))


def test_every_series_is_checked(empty):
    found = named(findings_of(empty), "numbering")
    assert found.detail == f"{len(SERIES)} series open"


def test_the_doctor_command_says_it_is_ready(books):
    found = run(books, "doctor", "--on", "2025-01-14")
    assert found.exit_code == 0
    assert "ready to work" in found.output


def test_the_doctor_command_stops_on_a_fresh_database(empty):
    found = run(empty, "doctor", "--on", "2025-01-14")
    assert found.exit_code != 0
    assert "not ready" in found.output


def test_the_doctor_command_lists_every_check(books):
    found = run(books, "doctor", "--on", "2025-01-14")
    for check in ("schema", "numbering", "car hire rates", "fleet"):
        assert check in found.output


def test_the_doctor_command_defaults_to_today(books):
    assert run(books, "doctor").exit_code == 0


def test_a_date_that_cannot_be_read_is_refused(books):
    assert run(books, "doctor", "--on", "tuesday").exit_code != 0

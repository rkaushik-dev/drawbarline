from datetime import UTC, datetime, timedelta

import pytest

from drawbarline.audit import Action, AuditError, AuditTrail, Entry
from drawbarline.clock import ClockError, utc
from drawbarline.persistence.base import create_all, make_engine, table_names
from drawbarline.persistence.uow import UnitOfWork

WHEN = utc(2025, 2, 10, 14, 30)


def an_entry(**changed):
    fields = {
        "at": WHEN,
        "actor": "rjs",
        "action": Action.CLOSED_PERIOD,
        "subject": "2025-01",
    }
    fields.update(changed)
    return Entry(**fields)


def test_an_entry_prints_who_did_what():
    assert str(an_entry()) == "2025-02-10 14:30Z rjs closed a period 2025-01"


def test_an_entry_prints_its_detail_when_there_is_one():
    found = an_entry(detail="cn sent a corrected sheet")
    assert str(found).endswith("(cn sent a corrected sheet)")


def test_every_action_has_readable_text():
    assert all(one.text for one in Action)


def test_an_entry_with_no_actor_is_refused():
    with pytest.raises(AuditError):
        an_entry(actor="  ")


def test_a_signature_rather_than_initials_is_refused():
    with pytest.raises(AuditError):
        an_entry(actor="x" * 33)


def test_an_entry_about_nothing_is_refused():
    with pytest.raises(AuditError):
        an_entry(subject="")


def test_a_detail_longer_than_the_field_is_refused():
    with pytest.raises(AuditError):
        an_entry(detail="x" * 201)


def test_a_naive_moment_is_refused():
    with pytest.raises(ClockError):
        an_entry(at=datetime(2025, 2, 10, 14, 30))


def test_the_actor_is_trimmed():
    assert an_entry(actor="  rjs ").actor == "rjs"


def test_a_trail_records_in_order():
    trail = AuditTrail()
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
    trail.record("mkb", Action.RETIRED_CAR, "DBL 104207", at=WHEN - timedelta(days=1))
    assert [one.actor for one in trail.entries] == ["mkb", "rjs"]


def test_a_trail_defaults_to_now():
    trail = AuditTrail()
    before = datetime.now(UTC)
    found = trail.record("rjs", Action.CLOSED_PERIOD, "2025-01")
    assert before <= found.at <= datetime.now(UTC)


def test_entries_come_back_by_actor():
    trail = AuditTrail()
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
    trail.record("mkb", Action.RETIRED_CAR, "DBL 104207", at=WHEN)
    assert [one.subject for one in trail.by_actor("rjs")] == ["2025-01"]


def test_entries_come_back_by_action():
    trail = AuditTrail()
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
    trail.record("rjs", Action.REOPENED_PERIOD, "2025-01", at=WHEN)
    assert len(trail.of_action(Action.REOPENED_PERIOD)) == 1


def test_entries_come_back_about_one_subject():
    trail = AuditTrail()
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
    trail.record("rjs", Action.REOPENED_PERIOD, "2025-01", at=WHEN)
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-02", at=WHEN)
    assert len(trail.about("2025-01")) == 2


def test_entries_come_back_since_a_moment():
    trail = AuditTrail()
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN - timedelta(days=5))
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-02", at=WHEN)
    assert len(trail.since(WHEN - timedelta(days=1))) == 1


def test_a_naive_since_is_refused():
    with pytest.raises(ClockError):
        AuditTrail().since(datetime(2025, 2, 1))


def test_the_actors_come_back_sorted():
    trail = AuditTrail()
    trail.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
    trail.record("mkb", Action.CLOSED_PERIOD, "2025-02", at=WHEN)
    assert trail.actors() == ["mkb", "rjs"]


def test_an_empty_trail_holds_nothing():
    assert len(AuditTrail()) == 0
    assert AuditTrail().actors() == []


@pytest.fixture
def engine():
    engine = make_engine()
    create_all(engine)
    return engine


def test_the_audit_table_is_on_the_metadata():
    assert "audit" in table_names()


def test_an_entry_survives_a_trip_through_the_database(engine):
    with UnitOfWork(engine) as work:
        work.audit.record("rjs", Action.CLOSED_PERIOD, "2025-01", "monthly close", WHEN)
        work.commit()
    with UnitOfWork(engine) as work:
        found = work.audit.entries()
    assert found[0].actor == "rjs"
    assert found[0].action is Action.CLOSED_PERIOD
    assert found[0].at == WHEN


def test_the_newest_entries_come_back_first(engine):
    with UnitOfWork(engine) as work:
        work.audit.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
        work.audit.record("mkb", Action.CLOSED_PERIOD, "2025-02", at=WHEN + timedelta(days=30))
        work.commit()
    with UnitOfWork(engine) as work:
        assert [one.subject for one in work.audit.entries()] == ["2025-02", "2025-01"]


def test_a_limit_takes_only_the_newest(engine):
    with UnitOfWork(engine) as work:
        for at in range(3):
            work.audit.record("rjs", Action.CLOSED_PERIOD, f"2025-0{at + 1}", at=WHEN)
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.audit.entries(limit=2)) == 2


def test_stored_entries_come_back_by_actor_and_action(engine):
    with UnitOfWork(engine) as work:
        work.audit.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
        work.audit.record("mkb", Action.RETIRED_CAR, "DBL 104207", at=WHEN)
        work.commit()
    with UnitOfWork(engine) as work:
        assert len(work.audit.by_actor("rjs")) == 1
        assert len(work.audit.of_action(Action.RETIRED_CAR)) == 1
        assert len(work.audit.about("DBL 104207")) == 1


def test_the_whole_trail_loads_back(engine):
    with UnitOfWork(engine) as work:
        work.audit.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
        work.commit()
    with UnitOfWork(engine) as work:
        trail = work.audit.trail()
    assert len(trail) == 1
    assert trail.actors() == ["rjs"]


def test_an_entry_that_is_not_committed_is_not_there(engine):
    with UnitOfWork(engine) as work:
        work.audit.record("rjs", Action.CLOSED_PERIOD, "2025-01", at=WHEN)
    with UnitOfWork(engine) as work:
        assert len(work.audit) == 0

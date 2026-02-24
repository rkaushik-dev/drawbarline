"""The domain and the columns have to agree about how long a field is.

Both sides import the same numbers now, so this checks the numbers are used
rather than that they match. A column that went back to a literal would slip
past a test that only compared two constants.
"""

from __future__ import annotations

from datetime import date

import pytest
from sqlalchemy import String

from drawbarline import limits
from drawbarline.audit import Action, Entry
from drawbarline.clock import utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.models.audit import AuditRow
from drawbarline.persistence.models.customers import IndustryRow, ShipperRow
from drawbarline.persistence.models.events import EventRow
from drawbarline.settlement.adjustment import Adjustment
from drawbarline.settlement.period import Period

CAR = CarMark.parse("CN 400100")


def width_of(row, column):
    kind = row.__table__.columns[column].type
    assert isinstance(kind, String)
    return kind.length


def test_every_limit_is_a_positive_whole_number():
    for name in dir(limits):
        if name.isupper():
            value = getattr(limits, name)
            assert isinstance(value, int)
            assert value > 0


def test_a_note_at_the_limit_is_accepted():
    found = Event(EventKind.WEIGHED, CAR, utc(2025, 1, 6), "ESC", note="x" * limits.NOTE)
    assert len(found.note) == limits.NOTE


def test_a_note_one_over_is_refused():
    with pytest.raises(EventError):
        Event(EventKind.WEIGHED, CAR, utc(2025, 1, 6), "ESC", note="x" * (limits.NOTE + 1))


def test_the_note_column_holds_what_the_domain_accepts():
    assert width_of(EventRow, "note") == limits.NOTE


def test_an_actor_at_the_limit_is_accepted():
    found = Entry(utc(2025, 1, 6), "x" * limits.ACTOR, Action.CLOSED_PERIOD, "2025-01")
    assert len(found.actor) == limits.ACTOR


def test_the_actor_column_holds_what_the_domain_accepts():
    assert width_of(AuditRow, "actor") == limits.ACTOR


def test_the_detail_column_holds_what_the_domain_accepts():
    assert width_of(AuditRow, "detail") == limits.DETAIL


def test_the_subject_column_matches_the_limit():
    assert width_of(AuditRow, "subject") == limits.SUBJECT


def test_the_shipper_columns_match_the_limits():
    assert width_of(ShipperRow, "code") == limits.SHIPPER_CODE
    assert width_of(ShipperRow, "name") == limits.PARTY_NAME


def test_the_industry_columns_match_the_limits():
    assert width_of(IndustryRow, "code") == limits.INDUSTRY_CODE
    assert width_of(IndustryRow, "station") == limits.STATION_CODE


def test_a_reference_is_cut_to_the_field_rather_than_refused():
    found = Adjustment(
        road=ReportingMark("CN"),
        corrects=Period(2025, 1),
        posted_in=Period(2025, 3),
        amount=Money(-100),
        on=date(2025, 3, 12),
        reason="x" * 500,
    )
    assert len(found.reference) == limits.REFERENCE


def test_a_car_mark_never_exceeds_its_column():
    assert len(str(CarMark.parse("PLMX 999999"))) <= limits.CAR_MARK


def test_a_reporting_mark_never_exceeds_its_column():
    assert len(str(ReportingMark("PLMX"))) <= limits.REPORTING_MARK


def test_the_helper_passes_what_fits():
    assert limits.fits("short", limits.NOTE, "a note") == "short"


def test_the_helper_names_the_field_that_does_not_fit():
    from drawbarline.errors import ConfigurationError

    with pytest.raises(ConfigurationError) as caught:
        limits.fits("x" * 200, limits.NOTE, "a note")
    assert "a note" in str(caught.value)
    assert str(limits.NOTE) in str(caught.value)


def test_the_predicate_agrees_with_the_helper():
    assert limits.within("short", limits.NOTE) is True
    assert limits.within("x" * 200, limits.NOTE) is False

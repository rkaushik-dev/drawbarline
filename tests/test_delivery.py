from datetime import date, datetime, time

from drawbarline.agreements import InterchangeAgreement
from drawbarline.clock import utc
from drawbarline.embargo import Embargo, EmbargoRegister
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import EventLog
from drawbarline.fleet.aar import CarKind
from drawbarline.interchange.delivery import can_deliver, delivered_today
from drawbarline.lading import SODIUM_HYDROXIDE, WHEAT
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.reference import drawbar_line
from support import a_fleet, received

CN = ReportingMark("CN")
CAR = CarMark.parse("CN 400100")
ESC = drawbar_line().station("ESC")
MORNING = datetime(2025, 1, 14, 9, 0)


def an_agreement(**changed):
    fields = {
        "road": CN,
        "junction": "ESC",
        "effective": date(2024, 7, 1),
        "kinds": frozenset({CarKind.COVERED_HOPPER}),
        "daily_cap": 2,
        "opens": time(7, 0),
        "closes": time(19, 0),
    }
    fields.update(changed)
    return InterchangeAgreement(**fields)


def a_log(delivered=0):
    log = EventLog()
    received(log, "CN 400100", 6)
    for at in range(delivered):
        car = CarMark.parse(f"CN {400200 + at}")
        log.record(Event(EventKind.DELIVERED, car, utc(2025, 1, 14, 14), "ESC", road=CN))
    return log


def a_verdict(**changed):
    fields = {
        "fleet": a_fleet("CN 400100"),
        "log": a_log(),
        "embargoes": EmbargoRegister(),
        "agreement": an_agreement(),
        "station": ESC,
        "local": MORNING,
    }
    fields.update(changed)
    return can_deliver(CAR, **fields)


def test_nothing_delivered_yet_counts_none():
    assert delivered_today(a_log(), "ESC", CN, date(2025, 1, 14), ESC) == 0


def test_a_delivery_today_is_counted():
    assert delivered_today(a_log(delivered=2), "ESC", CN, date(2025, 1, 14), ESC) == 2


def test_a_delivery_to_another_road_is_not_counted():
    log = a_log()
    log.record(
        Event(
            EventKind.DELIVERED,
            CarMark.parse("CN 400300"),
            utc(2025, 1, 14, 14),
            "ESC",
            road=ReportingMark("ELS"),
        )
    )
    assert delivered_today(log, "ESC", CN, date(2025, 1, 14), ESC) == 0


def test_a_delivery_on_another_day_is_not_counted():
    assert delivered_today(a_log(delivered=2), "ESC", CN, date(2025, 1, 15), ESC) == 0


def test_a_sound_car_goes_across():
    found = a_verdict()
    assert found.goes is True
    assert str(found) == "CN 400100 goes to CN at ESC"


def test_a_car_nobody_filed_is_held():
    found = can_deliver(
        CAR,
        fleet=a_fleet(),
        log=a_log(),
        embargoes=EmbargoRegister(),
        agreement=an_agreement(),
        station=ESC,
        local=MORNING,
    )
    assert found.goes is False
    assert "no equipment record" in found.findings[0]


def test_a_retired_car_is_held():
    fleet = a_fleet("CN 400100")
    fleet.retire(CAR, date(2024, 12, 31))
    found = a_verdict(fleet=fleet)
    assert any("retired" in one for one in found.findings)


def test_an_embargo_at_the_junction_holds_the_car():
    embargoes = EmbargoRegister()
    embargoes.issue(Embargo(101, date(2025, 1, 6), "bridge out", station="ESC"))
    found = a_verdict(embargoes=embargoes)
    assert any("embargo 101" in one for one in found.findings)


def test_an_embargo_somewhere_else_does_not():
    embargoes = EmbargoRegister()
    embargoes.issue(Embargo(101, date(2025, 1, 6), "bridge out", station="HRM"))
    assert a_verdict(embargoes=embargoes).goes is True


def test_a_commodity_embargo_holds_a_car_carrying_it():
    embargoes = EmbargoRegister()
    embargoes.issue(Embargo(101, date(2025, 1, 6), "elevator full", commodity_family="01"))
    found = a_verdict(embargoes=embargoes, commodity=WHEAT)
    assert found.goes is False


def test_a_kind_the_road_does_not_take_is_held():
    found = a_verdict(agreement=an_agreement(kinds=frozenset({CarKind.TANK})))
    assert any("does not take" in one for one in found.findings)


def test_hazardous_material_is_held_unless_the_agreement_allows_it():
    found = a_verdict(commodity=SODIUM_HYDROXIDE)
    assert any("hazardous" in one for one in found.findings)


def test_an_agreement_that_allows_hazardous_lets_it_go():
    found = a_verdict(agreement=an_agreement(hazardous=True), commodity=SODIUM_HYDROXIDE)
    assert found.goes is True


def test_a_car_offered_when_the_junction_is_shut_is_held():
    found = a_verdict(local=datetime(2025, 1, 14, 22, 0))
    assert any("closed at 22:00" in one for one in found.findings)


def test_a_car_past_the_daily_cap_is_held():
    found = a_verdict(log=a_log(delivered=2))
    assert any("taken its 2 cars" in one for one in found.findings)


def test_the_car_that_makes_the_cap_still_goes():
    assert a_verdict(log=a_log(delivered=1)).goes is True


def test_every_reason_comes_back_together():
    embargoes = EmbargoRegister()
    embargoes.issue(Embargo(101, date(2025, 1, 6), "bridge out", station="ESC"))
    found = a_verdict(
        embargoes=embargoes,
        agreement=an_agreement(kinds=frozenset({CarKind.TANK})),
        local=datetime(2025, 1, 14, 22, 0),
    )
    assert len(found.findings) >= 3


def test_a_held_car_prints_the_first_reason():
    found = a_verdict(agreement=an_agreement(kinds=frozenset({CarKind.TANK})))
    assert str(found).startswith("CN 400100 held at ESC:")

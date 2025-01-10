from datetime import date

import pytest

from drawbarline.carhire.liability import Charge, charges_for, liability, tenths_in
from drawbarline.carhire.rates import Band, Rate, RateTable
from drawbarline.clock import Interval, utc
from drawbarline.errors import UnknownCar
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import EventLog
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.money import Money
from drawbarline.weight import Weight

HOPPER = CarTypeCode("L351")
HOME = ReportingMark("DBL")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
JANUARY = Interval(utc(2025, 1, 1, 6), utc(2025, 2, 1, 6))


def a_car(mark):
    return Car(
        mark=CarMark.parse(mark),
        designation=MechanicalDesignation("LO"),
        type_code=HOPPER,
        built=date(2020, 6, 1),
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_table():
    table = RateTable()
    table.add(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
    return table


def visit(log, mark, received_day, delivered_day=None, tenths=None):
    car = CarMark.parse(mark)
    log.record(Event(EventKind.RECEIVED, car, utc(2025, 1, received_day, 12), "ESC", road=CN))
    if tenths:
        log.record(
            Event(EventKind.MOVED, car, utc(2025, 1, received_day, 18), "PWR", tenths=tenths)
        )
    if delivered_day:
        log.record(Event(EventKind.DELIVERED, car, utc(2025, 1, delivered_day, 12), "MRN", road=CN))


def a_world():
    register = FleetRegister()
    for mark in ("CN 400100", "DBL 104207", "PLMX 9001"):
        register.add(a_car(mark))
    return register, EventLog(), a_table()


def test_a_foreign_car_here_two_days_earns_two_days_of_hours():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12)
    found = charges_for(register, log, table, CarMark.parse("CN 400100"), JANUARY, HOME)
    assert found[0].hours == Money(463 * 48)
    assert found[0].owed_to == CN


def test_our_own_car_on_our_own_rail_earns_nothing():
    register, log, table = a_world()
    visit(log, "DBL 104207", 10, 12)
    assert charges_for(register, log, table, CarMark.parse("DBL 104207"), JANUARY, HOME) == []


def test_our_own_car_out_on_lease_earns_for_the_lessee():
    register, log, table = a_world()
    register.ownership(CarMark.parse("DBL 104207")).lease(ELS, date(2025, 1, 1))
    visit(log, "DBL 104207", 10, 12)
    found = charges_for(register, log, table, CarMark.parse("DBL 104207"), JANUARY, HOME)
    assert found[0].owed_to == ELS


def test_a_private_car_earns_mileage_and_no_hours():
    register, log, table = a_world()
    visit(log, "PLMX 9001", 10, 12, tenths=336)
    found = charges_for(register, log, table, CarMark.parse("PLMX 9001"), JANUARY, HOME)
    assert found[0].hours == Money(0)
    assert found[0].miles == Money(4066)


def test_a_railroad_car_earns_both_halves():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12, tenths=336)
    found = charges_for(register, log, table, CarMark.parse("CN 400100"), JANUARY, HOME)
    assert found[0].hours.cents > 0
    assert found[0].miles.cents > 0
    assert found[0].amount == found[0].hours + found[0].miles


def test_a_car_still_here_is_charged_to_the_window_end():
    register, log, table = a_world()
    visit(log, "CN 400100", 31)
    found = charges_for(register, log, table, CarMark.parse("CN 400100"), JANUARY, HOME)
    assert found[0].window.end == JANUARY.end


def test_a_car_that_came_after_the_window_raises_nothing():
    register, log, table = a_world()
    log.record(
        Event(EventKind.RECEIVED, CarMark.parse("CN 400100"), utc(2025, 3, 1, 12), "ESC", road=CN)
    )
    assert charges_for(register, log, table, CarMark.parse("CN 400100"), JANUARY, HOME) == []


def test_a_car_nobody_filed_is_refused():
    register, log, table = a_world()
    with pytest.raises(UnknownCar):
        charges_for(register, log, table, CarMark.parse("WC 1"), JANUARY, HOME)


def test_the_miles_inside_a_window_are_the_ones_counted():
    register, log, table = a_world()
    car = CarMark.parse("CN 400100")
    log.record(Event(EventKind.RECEIVED, car, utc(2025, 1, 10, 12), "ESC", road=CN))
    log.record(Event(EventKind.MOVED, car, utc(2025, 1, 11, 12), "PWR", tenths=336))
    log.record(Event(EventKind.MOVED, car, utc(2025, 2, 11, 12), "IMT", tenths=286))
    assert tenths_in(log.stream(car), JANUARY) == 336


def test_a_charge_prints_the_car_the_road_and_the_money():
    charge = Charge(CarMark.parse("CN 400100"), CN, JANUARY, Money(1000), Money(200))
    assert str(charge) == "CN 400100 to CN $12.00"


def test_a_charge_of_nothing_says_so():
    charge = Charge(CarMark.parse("CN 400100"), CN, JANUARY, Money(0), Money(0))
    assert charge.free is True


def test_the_run_gathers_every_car_in_the_log():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12)
    visit(log, "PLMX 9001", 10, 12, tenths=100)
    found = liability(register, log, table, JANUARY, HOME)
    assert len(found) == 2
    assert found.cars() == [CarMark.parse("CN 400100"), CarMark.parse("PLMX 9001")]


def test_the_run_skips_a_car_with_no_equipment_record():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12)
    log.record(
        Event(EventKind.RECEIVED, CarMark.parse("WC 1"), utc(2025, 1, 10, 12), "ESC", road=CN)
    )
    assert len(liability(register, log, table, JANUARY, HOME)) == 1


def test_the_run_totals_by_road():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12)
    register.ownership(CarMark.parse("DBL 104207")).lease(ELS, date(2025, 1, 1))
    visit(log, "DBL 104207", 10, 12)
    by_road = liability(register, log, table, JANUARY, HOME).by_road()
    assert set(by_road) == {CN, ELS}


def test_the_totals_split_into_hours_and_miles():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12, tenths=336)
    found = liability(register, log, table, JANUARY, HOME)
    assert found.total() == found.hours_total() + found.miles_total()


def test_charges_for_one_road_come_back_on_their_own():
    register, log, table = a_world()
    visit(log, "CN 400100", 10, 12)
    found = liability(register, log, table, JANUARY, HOME)
    assert len(found.for_road(CN)) == 1
    assert found.for_road(ELS) == []


def test_charges_come_back_sorted_by_road_then_car():
    register, log, table = a_world()
    visit(log, "PLMX 9001", 10, 12, tenths=100)
    visit(log, "CN 400100", 10, 12)
    marks = [str(charge.car) for charge in liability(register, log, table, JANUARY, HOME).charges]
    assert marks == ["CN 400100", "PLMX 009001"]


def test_an_empty_log_owes_nothing():
    register, log, table = a_world()
    assert liability(register, log, table, JANUARY, HOME).total() == Money(0)

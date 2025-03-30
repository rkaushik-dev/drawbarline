"""A small working railroad, for the tests that need more than one register."""

from __future__ import annotations

from datetime import date

from drawbarline.carhire.rates import Band, Rate, RateTable
from drawbarline.clock import utc
from drawbarline.customers import CustomerRegister, Industry, Shipper
from drawbarline.demurrage.tariff import Step, Tariff, TariffBook
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.events.stream import EventLog
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.measure import Length
from drawbarline.network import Network
from drawbarline.reference import drawbar_line
from drawbarline.settlement.close import Books
from drawbarline.settlement.ledger import Ledger
from drawbarline.settlement.period import PeriodRegister
from drawbarline.weight import Weight

HOME = ReportingMark("DBL")
CN = ReportingMark("CN")
ELS = ReportingMark("ELS")
HOPPER = CarTypeCode("L351")
MILL = Shipper("ESCPAP", "Escanaba Paper")
ELEVATOR = Shipper("PWRGRN", "Powers Grain", average_agreement=True)


def a_car(mark: str, built: date = date(2020, 6, 1)) -> Car:
    return Car(
        mark=CarMark.parse(mark),
        designation=MechanicalDesignation("LO"),
        type_code=HOPPER,
        built=built,
        light_weight=Weight(63500),
        gross_rail_load=Weight(286000),
        outside_length=Length.of(59, 6),
    )


def a_fleet(*marks: str) -> FleetRegister:
    register = FleetRegister()
    for mark in marks:
        register.add(a_car(mark))
    return register


def a_rate_table() -> RateTable:
    table = RateTable()
    table.add(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
    table.add(Band(HOPPER, 10, 99, date(2024, 7, 1), Rate(3180, 1210)))
    return table


def a_tariff_book() -> TariffBook:
    book = TariffBook()
    book.publish(Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000))))
    return book


def a_customer_register() -> CustomerRegister:
    register = CustomerRegister()
    register.add_shipper(MILL)
    register.add_shipper(ELEVATOR)
    register.add_industry(Industry("ESCPAP1", "mill house track", "ESC", MILL, 11))
    register.add_industry(Industry("PWRGRN1", "elevator", "PWR", ELEVATOR, 20))
    return register


def a_network() -> Network:
    return drawbar_line()


def books(*marks: str) -> Books:
    return Books(
        network=a_network(),
        fleet=a_fleet(*marks),
        log=EventLog(),
        rates=a_rate_table(),
        tariffs=a_tariff_book(),
        customers=a_customer_register(),
        ledger=Ledger(),
        periods=PeriodRegister(),
        home=HOME,
    )


def received(log: EventLog, mark: str, day: int, hour: int = 12, waybill: int | None = 41207):
    car = CarMark.parse(mark)
    return log.record(
        Event(EventKind.RECEIVED, car, utc(2025, 1, day, hour), "ESC", road=CN, waybill=waybill)
    )


def delivered(log: EventLog, mark: str, day: int, hour: int = 12):
    car = CarMark.parse(mark)
    return log.record(Event(EventKind.DELIVERED, car, utc(2025, 1, day, hour), "MRN", road=CN))


def moved(log: EventLog, mark: str, day: int, tenths: int, station: str = "PWR"):
    car = CarMark.parse(mark)
    return log.record(Event(EventKind.MOVED, car, utc(2025, 1, day, 18), station, tenths=tenths))


def placed(log: EventLog, mark: str, day: int, industry: str = "ESCPAP1", waybill=41207):
    car = CarMark.parse(mark)
    station = "ESC" if industry.startswith("ESC") else "PWR"
    return log.record(
        Event(
            EventKind.PLACED,
            car,
            utc(2025, 1, day, 20),
            station,
            industry=industry,
            waybill=waybill,
        )
    )


def released(log: EventLog, mark: str, day: int, industry: str = "ESCPAP1"):
    car = CarMark.parse(mark)
    station = "ESC" if industry.startswith("ESC") else "PWR"
    return log.record(
        Event(EventKind.RELEASED, car, utc(2025, 1, day, 21), station, industry=industry)
    )


def pulled(log: EventLog, mark: str, day: int, industry: str = "ESCPAP1"):
    car = CarMark.parse(mark)
    station = "ESC" if industry.startswith("ESC") else "PWR"
    return log.record(
        Event(EventKind.PULLED, car, utc(2025, 1, day, 22), station, industry=industry)
    )

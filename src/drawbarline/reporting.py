"""Running a report against the books.

The command line and the http interface both wanted the same nine reports, and
both were doing the same three things to get each one: load the registers, run
the computation, lay it out. Two copies of that is two places to forget that
the demurrage report needs the tariff in force at the end of the month rather
than at the start, which is exactly what happened.

So it lives here once, beside the service layer and for the same reason. The
layout functions in the reports package still know nothing about a database
and the repositories still know nothing about a page.
"""

from __future__ import annotations

from datetime import date, datetime

from drawbarline.carhire.liability import liability
from drawbarline.distribution import distribute, empties_on_hand
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.reference import DRAWBAR_LINE
from drawbarline.repairs.card import price
from drawbarline.reports.carhire import detail_report, liability_report, owed_to
from drawbarline.reports.daily import daily_summary
from drawbarline.reports.demurrage import detention_report, shipper_report
from drawbarline.reports.fleet import age_profile, age_report, utilisation_report
from drawbarline.reports.interchange import (
    Crossing,
    balance_report,
    crossings,
    on_hand_report,
)
from drawbarline.reports.repairs import billing_report, exception_report
from drawbarline.reports.shop import shop_report
from drawbarline.service import books_of
from drawbarline.settlement.close import detentions_in
from drawbarline.settlement.period import Period


def car_hire(work: UnitOfWork, period: Period, road: str | None = None) -> list[str]:
    """Car hire liability, by road or car by car."""
    books = books_of(work)
    owed = liability(books.fleet, books.log, books.rates, period.window, DRAWBAR_LINE.mark)
    if road:
        return detail_report(owed, ReportingMark.parse(road), period)
    return liability_report(owed, period)


def car_hire_totals(work: UnitOfWork, period: Period) -> dict[str, Money]:
    """The same run as data, a road at a time."""
    books = books_of(work)
    owed = liability(books.fleet, books.log, books.rates, period.window, DRAWBAR_LINE.mark)
    return owed_to(owed)


def demurrage(work: UnitOfWork, period: Period, *, by_shipper: bool = False) -> list[str]:
    """Detention, car by car or customer by customer."""
    books = books_of(work)
    found = detentions_in(books, period)
    tariff = books.tariffs.in_effect_on(period.last_day)
    if by_shipper:
        return shipper_report(found, tariff, books.customers, period.label)
    return detention_report(found, tariff, period.label)


def utilisation(work: UnitOfWork, period: Period) -> list[str]:
    """Turn time against standing time, car by car."""
    return utilisation_report(work.events.log(), period.window, period.label)


def interchange(work: UnitOfWork, period: Period) -> list[str]:
    """What crossed each junction, both ways."""
    return balance_report(work.events.log(), period.window, period.label)


def interchange_crossings(work: UnitOfWork, period: Period) -> list[Crossing]:
    """The same counts as data."""
    return crossings(work.events.log(), period.window)


def on_hand(work: UnitOfWork, at: datetime) -> list[str]:
    """Every car we have taken and not handed back."""
    return on_hand_report(work.events.log(), at)


def fleet_age(work: UnitOfWork, on: date) -> list[str]:
    """How old the fleet is and what leaves interchange service soon."""
    return age_report(work.fleet.register(), on)


def fleet_age_profile(work: UnitOfWork, on: date) -> dict[str, int]:
    """The age profile as data."""
    return age_profile(work.fleet.register(), on)


def repairs(work: UnitOfWork, period: Period, *, exceptions: bool = False) -> list[str]:
    """Repair billing for a month, or what is still under exception."""
    master = work.repair_pricing.master()
    rates = work.repair_pricing.labor_rates()
    cards = work.cards.between(period.first_day, period.last_day)
    priced = [price(one, master, rates) for one in cards]
    if exceptions:
        by_number = {one.card.number: one for one in priced}
        return exception_report(work.cards.open_objections(), by_number, period.last_day)
    return billing_report(priced, period.label)


def shop(work: UnitOfWork, at: datetime) -> list[str]:
    """What is in the shop and how long it has been there."""
    return shop_report(work.events.log(), at)


def daily(work: UnitOfWork, day: date, at: datetime) -> list[str]:
    """The morning sheet, which is every other report boiled down to one page."""
    log = work.events.log()
    orders = work.orders.open_on(day)
    empties = empties_on_hand(work.fleet.register(), log, at)
    found = distribute(orders, empties, work.orders.stations())
    return daily_summary(log, work.embargoes.register(), found, day, at)

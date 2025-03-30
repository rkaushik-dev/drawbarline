"""Closing a month.

The order matters and it is the order a clerk would work in. Car hire first,
because it is the biggest number and the one most likely to be wrong. Then the
reclaims that come off it. Then demurrage, which is a different set of
customers and does not touch the road accounts at all. Then the statements,
and only then the month is shut.

Nothing here computes anything. Every number comes from the module that owns
it and this file decides what runs, in what order, and what gets posted where.
That separation is the reason a close can be run twice against the same books
and produce the same answer, and the reason a disagreement can be chased into
one module rather than into a thousand line procedure.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date

from drawbarline.carhire.liability import Liability, charges_for
from drawbarline.carhire.rates import RateTable
from drawbarline.carhire.reclaim import Reclaim, reclaims_for
from drawbarline.customers import CustomerRegister
from drawbarline.demurrage.counting import Detention, count
from drawbarline.demurrage.invoice import Invoice, assemble
from drawbarline.demurrage.tariff import TariffBook
from drawbarline.errors import UnknownCar
from drawbarline.events.spans import on_track_spans
from drawbarline.events.stream import EventLog
from drawbarline.fleet.register import FleetRegister
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.network import Network
from drawbarline.settlement.ledger import Ledger, Posting, PostingKind
from drawbarline.settlement.period import Period, PeriodRegister
from drawbarline.settlement.statement import Statement, cut_all


@dataclass(slots=True)
class Books:
    """Everything a close reads and the two registers it writes."""

    network: Network
    fleet: FleetRegister
    log: EventLog
    rates: RateTable
    tariffs: TariffBook
    customers: CustomerRegister
    ledger: Ledger
    periods: PeriodRegister
    home: ReportingMark


@dataclass(slots=True)
class CloseResult:
    """What a close produced, so it can be read without running it again."""

    period: Period
    liability: Liability
    reclaims: list[Reclaim] = field(default_factory=list)
    detentions: list[Detention] = field(default_factory=list)
    invoices: list[Invoice] = field(default_factory=list)
    statements: list[Statement] = field(default_factory=list)
    postings: int = 0

    @property
    def car_hire(self) -> Money:
        return self.liability.total()

    @property
    def reclaimed(self) -> Money:
        return Money(sum(one.amount.cents for one in self.reclaims))

    @property
    def demurrage(self) -> Money:
        return Money(sum(one.total.cents for one in self.invoices))


def car_hire_for(books: Books, period: Period) -> Liability:
    """Run the car hire for a month, skipping cars with no equipment record."""
    found = Liability(period.window)
    for car_mark in books.log.cars():
        try:
            charges = charges_for(
                books.fleet, books.log, books.rates, car_mark, period.window, books.home
            )
        except UnknownCar:
            continue
        for charge in charges:
            found.add(charge)
    return found


def reclaims_in(books: Books, period: Period) -> list[Reclaim]:
    found: list[Reclaim] = []
    for car_mark in books.log.cars():
        try:
            car = books.fleet.get(car_mark)
        except UnknownCar:
            continue
        if books.fleet.ownership(car_mark).holder_on(period.last_day) == books.home:
            continue
        found.extend(reclaims_for(car, books.log.stream(car_mark), books.rates, period.window))
    return found


def detentions_in(books: Books, period: Period) -> list[Detention]:
    """Every placement that touched a month, counted against the tariff in force."""
    tariff = books.tariffs.in_effect_on(period.last_day)
    found: list[Detention] = []
    for car_mark in books.log.cars():
        stream = books.log.stream(car_mark)
        for span in on_track_spans(stream, period.window.end):
            if not span.interval.overlaps(period.window):
                continue
            track = books.customers.industry(span.industry)
            station = books.network.station(track.station)
            loaded = span.opened_by.waybill is not None
            found.append(count(span, tariff, station, loaded=loaded))
    return found


def invoices_from(
    books: Books, period: Period, detentions: list[Detention], first_number: int
) -> list[Invoice]:
    """One invoice a shipper, in shipper order, numbered from where we are told."""
    tariff = books.tariffs.in_effect_on(period.last_day)
    grouped: dict[str, list[Detention]] = {}
    for one in detentions:
        shipper = books.customers.industry(one.industry).shipper.code
        grouped.setdefault(shipper, []).append(one)

    found = []
    for offset, shipper in enumerate(sorted(grouped)):
        found.append(
            assemble(first_number + offset, shipper, period.first_day, grouped[shipper], tariff)
        )
    return found


def close_month(
    books: Books,
    period: Period,
    *,
    first_invoice: int,
    first_statement: int,
    issued: date,
) -> CloseResult:
    """Work a month through in the order a clerk would, then shut it."""
    books.periods.require_open(period)

    liability = car_hire_for(books, period)
    result = CloseResult(period=period, liability=liability)

    for road, amount in liability.by_road().items():
        if amount.is_zero:
            continue
        books.ledger.post(Posting(road, period, PostingKind.CAR_HIRE, amount, period.last_day))
        result.postings += 1

    result.reclaims = reclaims_in(books, period)
    for reclaim in result.reclaims:
        if reclaim.amount.is_zero:
            continue
        road = books.fleet.ownership(reclaim.car).holder_on(period.last_day)
        books.ledger.post(
            Posting(road, period, PostingKind.RECLAIM, -reclaim.amount, period.last_day)
        )
        result.postings += 1

    result.detentions = detentions_in(books, period)
    result.invoices = invoices_from(books, period, result.detentions, first_invoice)

    result.statements = cut_all(books.ledger, period, first_number=first_statement, issued=issued)
    books.periods.close(period)
    return result

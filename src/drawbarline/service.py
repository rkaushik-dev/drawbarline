"""The application layer.

Everything below this file works on objects and knows nothing about a
database. Everything above it, the command line and the http api, knows about
a database and nothing about how car hire is worked out. This is the one place
the two meet, and it is deliberately thin: load the registers, run the thing
the domain already knows how to do, write back what came out of it.

Every decision a person makes goes through here and every one of them writes
an audit entry as it goes. The actor defaults to a placeholder rather than
being required, because a scheduled close genuinely has nobody behind it and
recording that honestly is better than recording somebody who was asleep.

The close is the interesting one. It runs against an in memory ledger loaded
out of the postings table, produces the postings it wants to make, and only
then are they written. That means a close that blows up half way leaves the
month exactly as it was, and it means the same close can be run against a
ledger built in a test with no database at all.
"""

from __future__ import annotations

from datetime import date, datetime

from drawbarline.agreements import InterchangeAgreement
from drawbarline.audit import Action
from drawbarline.interchange.delivery import Verdict, can_deliver
from drawbarline.lading import Commodity
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.network import Network
from drawbarline.persistence.numbering import INVOICES, STATEMENTS
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.reference import DRAWBAR_LINE, drawbar_line
from drawbarline.settlement.close import Books, CloseResult, close_month
from drawbarline.settlement.period import Period

UNSIGNED = "unsigned"


def books_of(
    work: UnitOfWork,
    *,
    network: Network | None = None,
    home: ReportingMark | None = None,
) -> Books:
    """Load every register out of the database into one working set."""
    return Books(
        network=network or drawbar_line(),
        fleet=work.fleet.register(),
        log=work.events.log(),
        rates=work.rates.table(),
        tariffs=work.tariffs.book(),
        customers=work.customers.register(),
        ledger=work.ledger.ledger(),
        periods=work.periods.register(),
        home=home or DRAWBAR_LINE.mark,
    )


def run_close(
    work: UnitOfWork,
    period: Period,
    *,
    issued: date,
    actor: str = UNSIGNED,
    network: Network | None = None,
    home: ReportingMark | None = None,
) -> CloseResult:
    """Close a month against the database, numbering the documents as it goes."""
    books = books_of(work, network=network, home=home)
    shippers = len({one.shipper.code for one in books.customers})
    first_invoice = work.numbering.take_many(INVOICES, max(shippers, 1))[0]
    first_statement = work.numbering.take_many(STATEMENTS, max(len(books.ledger.roads()), 1))[0]

    result = close_month(
        books,
        period,
        first_invoice=first_invoice,
        first_statement=first_statement,
        issued=issued,
    )

    for posting in result.postings:
        work.ledger.post(posting)
    work.periods.close(period, issued)
    work.audit.record(
        actor,
        Action.CLOSED_PERIOD,
        period.label,
        f"{result.posted} postings, car hire {result.car_hire}",
    )
    return result


def reopen_period(
    work: UnitOfWork, period: Period, *, on: date, reason: str, actor: str = UNSIGNED
) -> Period:
    """Open a closed month again, with the reason and the person on file."""
    work.periods.reopen(period, on, reason)
    work.audit.record(actor, Action.REOPENED_PERIOD, period.label, reason)
    return period


def sign_agreement(
    work: UnitOfWork, agreement: InterchangeAgreement, *, actor: str = UNSIGNED
) -> InterchangeAgreement:
    """File an interchange agreement and record who filed it."""
    work.agreements.sign(agreement)
    work.audit.record(
        actor,
        Action.SIGNED_AGREEMENT,
        agreement.code,
        f"cap {agreement.daily_cap}, {agreement.opens:%H:%M} to {agreement.closes:%H:%M}",
    )
    return agreement


def delivery_check(
    work: UnitOfWork,
    car: CarMark,
    *,
    junction: str,
    road: ReportingMark,
    local: datetime,
    commodity: Commodity | None = None,
    network: Network | None = None,
) -> Verdict:
    """Ask the equipment, the embargoes and the agreement in one go."""
    line = network or drawbar_line()
    return can_deliver(
        car,
        fleet=work.fleet.register(),
        log=work.events.log(),
        embargoes=work.embargoes.register(),
        agreement=work.agreements.at(junction, road, local.date()),
        station=line.station(junction),
        local=local,
        commodity=commodity,
    )

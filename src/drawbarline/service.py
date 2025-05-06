"""The application layer.

Everything below this file works on objects and knows nothing about a
database. Everything above it, the command line and the http api, knows about
a database and nothing about how car hire is worked out. This is the one place
the two meet, and it is deliberately thin: load the registers, run the thing
the domain already knows how to do, write back what came out of it.

The close is the interesting one. It runs against an in memory ledger loaded
out of the postings table, produces the postings it wants to make, and only
then are they written. That means a close that blows up half way leaves the
month exactly as it was, and it means the same close can be run against a
ledger built in a test with no database at all.
"""

from __future__ import annotations

from datetime import date

from drawbarline.marks import ReportingMark
from drawbarline.network import Network
from drawbarline.persistence.numbering import INVOICES, STATEMENTS
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.reference import DRAWBAR_LINE, drawbar_line
from drawbarline.settlement.close import Books, CloseResult, close_month
from drawbarline.settlement.period import Period


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
    return result

"""Report commands.

Every one of these loads the registers, runs the report the domain already
knows how to lay out, and prints the lines. Nothing is computed here and
nothing is formatted here, which is why a report printed from the command line
and the same report served over http cannot disagree.

They all read and none of them commit, so running a report against the live
books during a close is safe.
"""

from __future__ import annotations

from datetime import UTC, datetime

import click

from drawbarline.carhire.liability import liability
from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import a_date, a_month
from drawbarline.marks import ReportingMark
from drawbarline.reference import DRAWBAR_LINE
from drawbarline.reports.carhire import detail_report, liability_report
from drawbarline.reports.demurrage import detention_report, shipper_report
from drawbarline.reports.fleet import age_report, utilisation_report
from drawbarline.reports.interchange import balance_report, on_hand_report
from drawbarline.service import books_of
from drawbarline.settlement.close import detentions_in


def show(lines: list[str]) -> None:
    for line in lines:
        click.echo(line)


@cli.group()
def report() -> None:
    """What the office reads."""


@report.command("carhire")
@click.argument("month")
@click.option("--road", default=None, help="Print the car detail for one road.")
@pass_context
def carhire(ctx: Context, month: str, road: str | None) -> None:
    """Car hire liability, by road or car by car."""
    period = a_month(month)
    with ctx.work() as work:
        books = books_of(work)
        owed = liability(books.fleet, books.log, books.rates, period.window, DRAWBAR_LINE.mark)
    if road:
        show(detail_report(owed, ReportingMark.parse(road), period))
    else:
        show(liability_report(owed, period))


@report.command("utilisation")
@click.argument("month")
@pass_context
def utilisation(ctx: Context, month: str) -> None:
    """Turn time against standing time, car by car."""
    period = a_month(month)
    with ctx.work() as work:
        log = work.events.log()
    show(utilisation_report(log, period.window, period.label))


@report.command("demurrage")
@click.argument("month")
@click.option("--by-shipper", is_flag=True, help="Summarise by customer.")
@pass_context
def demurrage(ctx: Context, month: str, by_shipper: bool) -> None:
    """Detention, car by car or customer by customer."""
    period = a_month(month)
    with ctx.work() as work:
        books = books_of(work)
        found = detentions_in(books, period)
        tariff = books.tariffs.in_effect_on(period.last_day)
        customers = books.customers
    if by_shipper:
        show(shipper_report(found, tariff, customers, period.label))
    else:
        show(detention_report(found, tariff, period.label))


@report.command("interchange")
@click.argument("month")
@pass_context
def interchange(ctx: Context, month: str) -> None:
    """What crossed each junction, both ways."""
    period = a_month(month)
    with ctx.work() as work:
        log = work.events.log()
    show(balance_report(log, period.window, period.label))


@report.command("onhand")
@click.option("--at", default=None, help="A moment in utc, yyyy-mm-dd. Now by default.")
@pass_context
def onhand(ctx: Context, at: str | None) -> None:
    """Every car we have taken and not handed back."""
    moment = (
        datetime.combine(a_date(at), datetime.min.time(), tzinfo=UTC) if at else datetime.now(UTC)
    )
    with ctx.work() as work:
        log = work.events.log()
    show(on_hand_report(log, moment))


@report.command("age")
@click.option("--on", "on", default=None, help="A date, yyyy-mm-dd. Today by default.")
@pass_context
def age(ctx: Context, on: str | None) -> None:
    """How old the fleet is and what leaves interchange service soon."""
    day = a_date(on) if on else datetime.now(UTC).date()
    with ctx.work() as work:
        register = work.fleet.register()
    show(age_report(register, day))

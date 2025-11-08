"""Report commands.

Every one of these loads the registers, runs the report the reporting layer
already knows how to produce, and prints the lines. Nothing is computed here
and nothing is formatted here, which is why a report printed from the command
line and the same report served over http cannot disagree.

They all read and none of them commit, so running a report against the live
books during a close is safe.
"""

from __future__ import annotations

from datetime import UTC, datetime

import click

from drawbarline import reporting
from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import a_date, a_month


def show(lines: list[str]) -> None:
    for line in lines:
        click.echo(line)


def a_moment(text: str | None) -> datetime:
    """A day given as yyyy-mm-dd read as midnight utc, or now."""
    if text is None:
        return datetime.now(UTC)
    return datetime.combine(a_date(text), datetime.min.time(), tzinfo=UTC)


@cli.group()
def report() -> None:
    """What the office reads."""


@report.command("carhire")
@click.argument("month")
@click.option("--road", default=None, help="Print the car detail for one road.")
@pass_context
def carhire(ctx: Context, month: str, road: str | None) -> None:
    """Car hire liability, by road or car by car."""
    with ctx.work() as work:
        show(reporting.car_hire(work, a_month(month), road))


@report.command("utilisation")
@click.argument("month")
@pass_context
def utilisation(ctx: Context, month: str) -> None:
    """Turn time against standing time, car by car."""
    with ctx.work() as work:
        show(reporting.utilisation(work, a_month(month)))


@report.command("demurrage")
@click.argument("month")
@click.option("--by-shipper", is_flag=True, help="Summarise by customer.")
@pass_context
def demurrage(ctx: Context, month: str, by_shipper: bool) -> None:
    """Detention, car by car or customer by customer."""
    with ctx.work() as work:
        show(reporting.demurrage(work, a_month(month), by_shipper=by_shipper))


@report.command("interchange")
@click.argument("month")
@pass_context
def interchange(ctx: Context, month: str) -> None:
    """What crossed each junction, both ways."""
    with ctx.work() as work:
        show(reporting.interchange(work, a_month(month)))


@report.command("onhand")
@click.option("--at", default=None, help="A moment in utc, yyyy-mm-dd. Now by default.")
@pass_context
def onhand(ctx: Context, at: str | None) -> None:
    """Every car we have taken and not handed back."""
    with ctx.work() as work:
        show(reporting.on_hand(work, a_moment(at)))


@report.command("age")
@click.option("--on", "on", default=None, help="A date, yyyy-mm-dd. Today by default.")
@pass_context
def age(ctx: Context, on: str | None) -> None:
    """How old the fleet is and what leaves interchange service soon."""
    day = a_date(on) if on else datetime.now(UTC).date()
    with ctx.work() as work:
        show(reporting.fleet_age(work, day))


@report.command("repairs")
@click.argument("month")
@click.option("--exceptions", is_flag=True, help="What is open rather than what was billed.")
@pass_context
def repairs(ctx: Context, month: str, exceptions: bool) -> None:
    """Repair billing for a month, or what is still under exception."""
    with ctx.work() as work:
        show(reporting.repairs(work, a_month(month), exceptions=exceptions))

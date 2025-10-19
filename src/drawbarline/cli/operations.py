"""Operating commands: waybills, embargoes and the switch list.

The switch list is the one a crew actually reads off a printer at five in the
morning, so it prints as lines rather than as anything a machine would like,
and it prints the moves it could not make as loudly as the ones it could. A
crew that finds out at the industry that a track was full has already made the
trip.

Orders are written car=industry for the same reason they are on the http side.
The yard clerk writes the pairs down that way on the sheet the list is made up
from.
"""

from __future__ import annotations

from datetime import UTC, datetime

import click

from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import a_date
from drawbarline.distribution import CarOrder, distribute, empties_on_hand
from drawbarline.embargo import Embargo
from drawbarline.fleet.aar import CarKind
from drawbarline.lading import Commodity, Lading, Stcc
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.reports.distribution import distribution_report, empties_report
from drawbarline.reports.render import Align, Column, Table, heading
from drawbarline.switching import SwitchError, plan
from drawbarline.waybill import Waybill
from drawbarline.weight import Weight


def an_order(text: str) -> tuple[CarMark, str]:
    car, _, industry = text.partition("=")
    if not industry:
        raise click.ClickException(f"{text!r} is not an order, write it as car=industry")
    return CarMark.parse(car), industry.strip().upper()


@cli.group()
def waybill() -> None:
    """The paper a car moves on."""


@waybill.command("issue")
@click.argument("number", type=int)
@click.argument("car")
@click.option("--dated", required=True, help="The day it is issued.")
@click.option("--shipper", required=True, help="Who is shipping.")
@click.option("--consignee", required=True, help="Who is receiving.")
@click.option("--route", required=True, help="Station codes, comma separated.")
@click.option("--commodity", default=None, help="Seven digit commodity code.")
@click.option("--commodity-name", default=None, help="What it is called.")
@click.option("--pounds", default=None, type=int, help="What the lading weighs.")
@pass_context
def issue(
    ctx: Context,
    number: int,
    car: str,
    dated: str,
    shipper: str,
    consignee: str,
    route: str,
    commodity: str | None,
    commodity_name: str | None,
    pounds: int | None,
) -> None:
    """Issue a waybill. Leave the commodity off for an empty."""
    lading = None
    if commodity or pounds:
        if not (commodity and pounds):
            raise click.ClickException("a lading needs a commodity and a weight")
        lading = Lading(
            Commodity(Stcc.parse(commodity), commodity_name or "unnamed"),
            Weight(pounds),
        )
    bill = Waybill(
        number=number,
        dated=a_date(dated),
        car=CarMark.parse(car),
        shipper=shipper,
        consignee=consignee,
        route=tuple(one.strip().upper() for one in route.split(",") if one.strip()),
        lading=lading,
    )
    with ctx.work() as work:
        work.waybills.issue(bill)
        work.commit()
    click.echo(f"issued {bill}")


@waybill.command("show")
@click.argument("number", type=int)
@pass_context
def show_waybill(ctx: Context, number: int) -> None:
    """One waybill and the route it is authorised over."""
    with ctx.work() as work:
        bill = work.waybills.get(number)
    click.echo(f"waybill    {bill.number}")
    click.echo(f"dated      {bill.dated}")
    click.echo(f"car        {bill.car}")
    click.echo(f"shipper    {bill.shipper}")
    click.echo(f"consignee  {bill.consignee}")
    click.echo(f"route      {' '.join(bill.route)}")
    click.echo(f"miles      {bill.miles}")
    if bill.lading:
        click.echo(f"lading     {bill.lading}")
        if bill.hazardous:
            click.echo("           hazardous")
    else:
        click.echo("lading     empty")


@cli.group()
def embargo() -> None:
    """What is stopped, and where."""


@embargo.command("issue")
@click.argument("number", type=int)
@click.option("--effective", required=True, help="The day it takes effect.")
@click.option("--reason", required=True, help="Why.")
@click.option("--station", default=None, help="Restrict one station.")
@click.option("--commodity-family", default=None, help="Restrict a commodity family.")
@click.option("--road", default=None, help="Restrict one connection.")
@click.option("--expires", default=None, help="The day it ends, if it is known.")
@click.option("--permits", is_flag=True, help="Allow movement under a permit.")
@pass_context
def issue_embargo(
    ctx: Context,
    number: int,
    effective: str,
    reason: str,
    station: str | None,
    commodity_family: str | None,
    road: str | None,
    expires: str | None,
    permits: bool,
) -> None:
    """Issue an embargo. It has to restrict at least one thing."""
    found = Embargo(
        number=number,
        effective=a_date(effective),
        reason=reason,
        station=station.strip().upper() if station else None,
        commodity_family=commodity_family,
        road=ReportingMark.parse(road) if road else None,
        expires=a_date(expires) if expires else None,
        permits_allowed=permits,
    )
    with ctx.work() as work:
        work.embargoes.issue(found)
        work.commit()
    click.echo(f"issued {found}")


@embargo.command("lift")
@click.argument("number", type=int)
@click.option("--on", required=True, help="The day it comes off.")
@pass_context
def lift_embargo(ctx: Context, number: int, on: str) -> None:
    """Lift an embargo early."""
    with ctx.work() as work:
        found = work.embargoes.lift(number, a_date(on))
        work.commit()
    click.echo(f"lifted {found} on {found.expires}")


@embargo.command("list")
@click.option("--on", default=None, help="Only the ones live on a day.")
@pass_context
def list_embargoes(ctx: Context, on: str | None) -> None:
    """Every embargo, or only the live ones."""
    with ctx.work() as work:
        found = work.embargoes.in_force_on(a_date(on)) if on else work.embargoes.all()
    table = Table(
        [
            Column("number", Align.RIGHT),
            Column("from"),
            Column("to"),
            Column("restricts"),
            Column("reason"),
        ]
    )
    for one in found:
        restricts = " ".join(
            part
            for part in (
                one.station,
                one.commodity_family,
                str(one.road) if one.road else None,
            )
            if part
        )
        table.add(
            str(one.number),
            str(one.effective),
            str(one.expires) if one.expires else "open",
            restricts,
            one.reason,
        )
    table.total(f"{len(found)} embargoes", "", "", "", "")
    for line in [*heading("embargoes", on or "all"), *table.lines()]:
        click.echo(line)


@cli.command("switchlist")
@click.argument("station")
@click.option("--day", required=True, help="The day the crew works it.")
@click.option("--at", default=None, help="A moment in utc, yyyy-mm-dd. Now by default.")
@click.option(
    "--order",
    "orders",
    multiple=True,
    help="A car to spot, written as car=industry.",
)
@pass_context
def switchlist(
    ctx: Context, station: str, day: str, at: str | None, orders: tuple[str, ...]
) -> None:
    """The list for one station, pulls first so the placements see the room."""
    where = station.strip().upper()
    moment = (
        datetime.combine(a_date(at), datetime.min.time(), tzinfo=UTC) if at else datetime.now(UTC)
    )
    with ctx.work() as work:
        log = work.events.log()
        customers = work.customers.register()
    try:
        found = plan(log, customers, where, a_date(day), moment, [an_order(one) for one in orders])
    except SwitchError as bad:
        raise click.ClickException(str(bad)) from bad

    click.echo(f"SWITCH LIST {where} {day}")
    click.echo("")
    for move in found.moves:
        click.echo(f"  {move}")
    if not found.moves:
        click.echo("  nothing to do")
    click.echo("")
    click.echo(f"worked {found.worked} of {len(found)}")


@cli.group()
def order() -> None:
    """Cars a shipper has asked for."""


@order.command("place")
@click.argument("number", type=int)
@click.argument("shipper")
@click.option("--track", required=True, help="The industry track code.")
@click.option("--kind", required=True, help="Car kind letter, such as L or X.")
@click.option("--cars", required=True, type=int, help="How many.")
@click.option("--wanted", required=True, help="The day the cars are wanted.")
@click.option("--on", default=None, help="The day it is placed. Today by default.")
@pass_context
def place_order(
    ctx: Context,
    number: int,
    shipper: str,
    track: str,
    kind: str,
    cars: int,
    wanted: str,
    on: str | None,
) -> None:
    """Take a car order."""
    found = CarOrder(
        number=number,
        shipper=shipper.strip().upper(),
        industry=track.strip().upper(),
        kind=CarKind.from_letter(kind.strip().upper()),
        cars=cars,
        wanted_on=a_date(wanted),
    )
    placed = a_date(on) if on else datetime.now(UTC).date()
    with ctx.work() as work:
        work.orders.place(found, placed)
        work.commit()
    click.echo(f"took {found}")


@order.command("cancel")
@click.argument("number", type=int)
@click.option("--on", required=True, help="The day it is cancelled.")
@pass_context
def cancel_order(ctx: Context, number: int, on: str) -> None:
    """Cancel a car order."""
    with ctx.work() as work:
        work.orders.cancel(number, a_date(on))
        work.commit()
    click.echo(f"cancelled order {number}")


@cli.command("distribute")
@click.option("--on", required=True, help="The day the cars are wanted.")
@click.option("--at", default=None, help="A moment in utc, yyyy-mm-dd. The same day by default.")
@pass_context
def distribute_cars(ctx: Context, on: str, at: str | None) -> None:
    """Match the empties on hand against the open orders."""
    day = a_date(on)
    moment = datetime.combine(a_date(at) if at else day, datetime.min.time(), tzinfo=UTC)
    with ctx.work() as work:
        orders = work.orders.open_on(day)
        stations = work.orders.stations()
        empties = empties_on_hand(work.fleet.register(), work.events.log(), moment)
    found = distribute(orders, empties, stations)
    for line in distribution_report(found, orders, str(day)):
        click.echo(line)


@cli.command("empties")
@click.option("--at", default=None, help="A moment in utc, yyyy-mm-dd. Now by default.")
@pass_context
def empties(ctx: Context, at: str | None) -> None:
    """Every car standing empty on our rail."""
    moment = (
        datetime.combine(a_date(at), datetime.min.time(), tzinfo=UTC) if at else datetime.now(UTC)
    )
    with ctx.work() as work:
        found = empties_on_hand(work.fleet.register(), work.events.log(), moment)
    for line in empties_report(found, f"{moment:%Y-%m-%d}"):
        click.echo(line)

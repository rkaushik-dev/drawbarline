"""Repair billing commands.

A card is filed with one option a line, written as job:why:quantity:years. It
looks terse and it is what the shop foreman already writes on the card, so it
is what a clerk keying it can copy without translating anything.

Pricing a card is a separate command from filing it, because the price master
changes and a card filed today may be priced tomorrow at a rate that was
already in force. Filing and pricing being two steps is how the office works,
and pretending otherwise would mean re-keying cards after a filing.

The billing report is under the report group with every other report rather
than here, because a clerk looking for a report looks in one place.
"""

from __future__ import annotations

from pathlib import Path

import click

from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import SHEET, a_date, rows_of
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.money import Money
from drawbarline.repairs.card import Defect, RepairCard, price
from drawbarline.repairs.codes import JobCode, WhyMade
from drawbarline.repairs.objection import Ground, Objection, Outcome, Resolution
from drawbarline.repairs.pricing import Depreciation, LaborRate, PriceLine

DEFECT_FORM = "job:why:quantity:years"


def a_defect(text: str) -> Defect:
    """Read 4312:01:2:5 as a line of a card."""
    parts = text.split(":")
    if len(parts) not in (2, 3, 4):
        raise click.ClickException(f"{text!r} is not a line, write it as {DEFECT_FORM}")
    job, why = parts[0], parts[1]
    quantity = int(parts[2]) if len(parts) > 2 else 1
    years = int(parts[3]) if len(parts) > 3 else 0
    return Defect(JobCode(job.strip()), WhyMade(why.strip()), quantity, part_years=years)


@cli.group()
def repairs() -> None:
    """Repair cards and what they cost."""


@repairs.command("price")
@click.argument("sheet", type=SHEET)
@pass_context
def load_prices(ctx: Context, sheet: Path) -> None:
    """Load the price master from job,effective,labor_hundredths,material_cents."""
    taken = 0
    with ctx.work() as work:
        for row in rows_of(sheet):
            work.repair_pricing.add_price(
                PriceLine(
                    job=JobCode(row["job"].strip()),
                    effective=a_date(row["effective"]),
                    labor_hundredths=int(row["labor_hundredths"]),
                    material_cents=int(row["material_cents"]),
                )
            )
            taken += 1
        work.commit()
    click.echo(f"priced {taken} jobs")


@repairs.command("depreciation")
@click.argument("family")
@click.option("--percent", required=True, type=int, help="Percent used up a year.")
@click.option("--floor", required=True, type=int, help="The share always payable.")
@pass_context
def set_depreciation(ctx: Context, family: str, percent: int, floor: int) -> None:
    """File how fast one family of parts is used up."""
    with ctx.work() as work:
        work.repair_pricing.set_depreciation(Depreciation(family.strip(), percent, floor))
        work.commit()
    click.echo(f"family {family} loses {percent} a year down to {floor}")


@repairs.command("labor")
@click.argument("road")
@click.option("--effective", required=True, help="The day the rate takes effect.")
@click.option("--cents", required=True, type=int, help="Cents an hour.")
@pass_context
def file_labor(ctx: Context, road: str, effective: str, cents: int) -> None:
    """File what one road charges an hour."""
    with ctx.work() as work:
        work.repair_pricing.file_labor(
            LaborRate(ReportingMark.parse(road), a_date(effective), cents)
        )
        work.commit()
    click.echo(f"{road.upper()} at {Money(cents)} an hour from {effective}")


@repairs.command("card")
@click.argument("number", type=int)
@click.argument("car")
@click.option("--by", "road", required=True, help="The road that did the work.")
@click.option("--on", required=True, help="The day it was done.")
@click.option("--at", "station", required=True, help="Where it was done.")
@click.option(
    "--defect",
    "defects",
    multiple=True,
    required=True,
    help=f"A line, as {DEFECT_FORM}.",
)
@pass_context
def file_card(
    ctx: Context,
    number: int,
    car: str,
    road: str,
    on: str,
    station: str,
    defects: tuple[str, ...],
) -> None:
    """File a repair card."""
    card = RepairCard(
        number=number,
        car=CarMark.parse(car),
        performed_by=ReportingMark.parse(road),
        on=a_date(on),
        station=station.strip().upper(),
        defects=tuple(a_defect(one) for one in defects),
    )
    with ctx.work() as work:
        work.cards.file(card)
        work.commit()
    click.echo(f"filed {card}")


@repairs.command("show")
@click.argument("number", type=int)
@pass_context
def show_card(ctx: Context, number: int) -> None:
    """A card with the money on it."""
    with ctx.work() as work:
        card = work.cards.get(number)
        priced = price(card, work.repair_pricing.master(), work.repair_pricing.labor_rates())
    click.echo(str(card))
    for at, line in enumerate(priced.lines):
        click.echo(
            f"  {at}  {line.defect.job} why {line.defect.why} "
            f"{line.labor} labor {line.material} material "
            f"to {line.answered_by.text}"
        )
    click.echo(f"total      {priced.total}")
    click.echo(f"to owner   {priced.billed_to_owner}")
    click.echo(f"their cost {priced.borne_by_the_road}")


@repairs.command("except")
@click.argument("number", type=int)
@click.argument("line", type=int)
@click.option("--ground", required=True, help="Why the line is wrong.")
@click.option("--cents", required=True, type=int, help="How much is disputed.")
@click.option("--on", required=True, help="The day it is filed.")
@pass_context
def take_exception(ctx: Context, number: int, line: int, ground: str, cents: int, on: str) -> None:
    """Take exception to one line of a card."""
    try:
        why = Ground[ground.strip().upper()]
    except KeyError as bad:
        wanted = ", ".join(one.name.lower() for one in Ground)
        raise click.ClickException(f"no such ground, try one of {wanted}") from bad
    objection = Objection(number, line, why, Money(cents), a_date(on))
    with ctx.work() as work:
        work.cards.take_exception(objection)
        work.commit()
    click.echo(f"filed {objection}")


@repairs.command("answer")
@click.argument("number", type=int)
@click.argument("line", type=int)
@click.option("--allow", required=True, type=int, help="Cents allowed.")
@pass_context
def answer(ctx: Context, number: int, line: int, allow: int) -> None:
    """Answer an exception by allowing some or none of it."""
    with ctx.work() as work:
        standing = [
            one
            for one in work.cards.open_objections()
            if one.card_number == number and one.line == line
        ]
        if not standing:
            raise click.ClickException(f"no open exception on card {number} line {line}")
        objection = standing[0]
        allowed = Money(allow)
        if allowed.is_zero:
            outcome = Outcome.DECLINED
        elif allowed == objection.disputed:
            outcome = Outcome.ALLOWED
        else:
            outcome = Outcome.PART_ALLOWED
        work.cards.answer(objection, Resolution(objection, outcome, allowed))
        work.commit()
    click.echo(f"card {number} line {line} {outcome.value}")

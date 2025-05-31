"""Loading the tables the road files rather than computes.

Rates, tariffs, shippers and industry track all arrive as filings, and none of
them belong in the source. They come in as small comma separated files that a
clerk can read, and they are loaded in one transaction each so a file with a
bad line halfway down loads nothing.
"""

from __future__ import annotations

import csv
from datetime import datetime
from pathlib import Path

import click

from drawbarline.carhire.rates import Band, Rate
from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.customers import WEEKDAYS, Industry, Shipper
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.fleet.aar import CarTypeCode

SHEET = click.Path(exists=True, dir_okay=False, path_type=Path)

# What a track is switched on when the filing does not say otherwise.
WORKING_WEEK = frozenset(WEEKDAYS[:5])


def rows_of(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def a_date(text: str):
    return datetime.strptime(text.strip(), "%Y-%m-%d").date()


@cli.group()
def reference() -> None:
    """The tables the road files."""


@reference.command("rates")
@click.argument("sheet", type=SHEET)
@pass_context
def load_rates(ctx: Context, sheet: Path) -> None:
    """Load car hire bands from type_code,from_age,to_age,effective,hourly,mileage."""
    taken = 0
    with ctx.work() as work:
        for row in rows_of(sheet):
            work.rates.file(
                Band(
                    type_code=CarTypeCode.parse(row["type_code"]),
                    from_age=int(row["from_age"]),
                    to_age=int(row["to_age"]),
                    effective=a_date(row["effective"]),
                    rate=Rate(int(row["hourly"]), int(row["mileage"])),
                )
            )
            taken += 1
        work.commit()
    click.echo(f"filed {taken} bands")


@reference.command("tariff")
@click.argument("code")
@click.option("--effective", required=True, help="The day it takes effect.")
@click.option("--free-loaded", required=True, type=int, help="Free days on a load.")
@click.option("--free-empty", required=True, type=int, help="Free days on an empty.")
@click.option(
    "--step",
    "steps",
    multiple=True,
    required=True,
    help="A step as through:cents, with the last one 0:cents.",
)
@pass_context
def load_tariff(
    ctx: Context,
    code: str,
    effective: str,
    free_loaded: int,
    free_empty: int,
    steps: tuple[str, ...],
) -> None:
    """Publish a demurrage tariff."""
    read = []
    for one in steps:
        through, _, cents = one.partition(":")
        if not cents:
            raise click.ClickException(f"{one!r} is not a step, write it as 2:10000")
        read.append(Step(int(through), int(cents)))
    tariff = Tariff(code, a_date(effective), free_loaded, free_empty, tuple(read))
    with ctx.work() as work:
        work.tariffs.publish(tariff)
        work.commit()
    click.echo(f"published {tariff}")


@reference.command("shippers")
@click.argument("sheet", type=SHEET)
@pass_context
def load_shippers(ctx: Context, sheet: Path) -> None:
    """Load shippers from code,name,average_agreement."""
    taken = 0
    with ctx.work() as work:
        for row in rows_of(sheet):
            work.customers.add_shipper(
                Shipper(
                    code=row["code"].strip().upper(),
                    name=row["name"].strip(),
                    average_agreement=row.get("average_agreement", "").strip().lower()
                    in ("1", "yes", "true"),
                )
            )
            taken += 1
        work.commit()
    click.echo(f"filed {taken} shippers")


@reference.command("industries")
@click.argument("sheet", type=SHEET)
@pass_context
def load_industries(ctx: Context, sheet: Path) -> None:
    """Load industry track from code,name,station,shipper,spots,switched."""
    taken = 0
    with ctx.work() as work:
        for row in rows_of(sheet):
            shipper = work.customers.shipper(row["shipper"].strip().upper())
            written = row.get("switched", "").strip()
            days = frozenset(one.strip() for one in written.split(";") if one.strip())
            work.customers.add_industry(
                Industry(
                    code=row["code"].strip().upper(),
                    name=row["name"].strip(),
                    station=row["station"].strip().upper(),
                    shipper=shipper,
                    spots=int(row["spots"]),
                    switched=days or WORKING_WEEK,
                )
            )
            taken += 1
        work.commit()
    click.echo(f"filed {taken} industries")


@reference.command("show")
@pass_context
def show(ctx: Context) -> None:
    """What has been filed."""
    with ctx.work() as work:
        bands = len(work.rates)
        tariffs = len(work.tariffs.book())
        shippers = work.customers.shippers()
        industries = len(work.customers)
    click.echo(f"rate bands  {bands}")
    click.echo(f"tariffs     {tariffs}")
    click.echo(f"shippers    {len(shippers)}")
    click.echo(f"industries  {industries}")

"""Event commands.

Recording an event from a command line means reading a local clock reading at
a station, because that is what the paperwork carries. The conversion to a
moment happens here, once, and it refuses the two mornings a year the reading
is ambiguous rather than guessing which one the clerk meant.

There is a bulk load as well, because the connections send a day of interchange
as a file and keying it by hand is not a plan.
"""

from __future__ import annotations

import csv
from datetime import datetime
from pathlib import Path

import click

from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.clock import to_utc
from drawbarline.errors import EventError
from drawbarline.events.kinds import kind_named
from drawbarline.events.record import Event
from drawbarline.events.replay import replay
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.reference import drawbar_line

FIELDS = ("car", "kind", "when", "station", "road", "industry", "waybill", "tenths")


def a_moment(station_code: str, text: str, *, second_pass: bool = False):
    """Read a local clock reading at a station as a moment."""
    station = drawbar_line().station(station_code)
    return to_utc(station, datetime.strptime(text, "%Y-%m-%d %H:%M"), second_pass=second_pass)


def build(row: dict[str, str]) -> Event:
    """One event out of one row of a sheet."""
    station = row["station"].strip().upper()
    return Event(
        kind=kind_named(row["kind"]),
        car=CarMark.parse(row["car"]),
        when=a_moment(station, row["when"].strip()),
        station=station,
        road=ReportingMark.parse(row["road"]) if row.get("road") else None,
        industry=row["industry"].strip().upper() or None if row.get("industry") else None,
        waybill=int(row["waybill"]) if row.get("waybill") else None,
        tenths=int(row["tenths"]) if row.get("tenths") else None,
    )


@cli.group()
def events() -> None:
    """The car event log."""


@events.command("record")
@click.argument("car")
@click.argument("kind")
@click.option("--at", "station", required=True, help="Station code.")
@click.option("--when", required=True, help="Local clock reading, yyyy-mm-dd hh:mm.")
@click.option("--road", default=None, help="The road on the other side of an interchange.")
@click.option("--industry", default=None, help="The track, for a placement or a pull.")
@click.option("--waybill", default=None, type=int, help="Waybill number.")
@click.option("--tenths", default=None, type=int, help="Tenths of a mile, for a movement.")
@click.option("--second-pass", is_flag=True, help="The later of the two ambiguous hours.")
@pass_context
def record(
    ctx: Context,
    car: str,
    kind: str,
    station: str,
    when: str,
    road: str | None,
    industry: str | None,
    waybill: int | None,
    tenths: int | None,
    second_pass: bool,
) -> None:
    """Record one event against one car."""
    code = station.strip().upper()
    event = Event(
        kind=kind_named(kind),
        car=CarMark.parse(car),
        when=a_moment(code, when, second_pass=second_pass),
        station=code,
        road=ReportingMark.parse(road) if road else None,
        industry=industry.strip().upper() if industry else None,
        waybill=waybill,
        tenths=tenths,
    )
    with ctx.work() as work:
        work.events.record(event)
        work.commit()
    click.echo(f"recorded {event}")


@events.command("load")
@click.argument("sheet", type=click.Path(exists=True, dir_okay=False, path_type=Path))
@pass_context
def load(ctx: Context, sheet: Path) -> None:
    """Load a day of interchange out of a comma separated sheet."""
    with sheet.open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    missing = {"car", "kind", "when", "station"} - set(rows[0] if rows else {})
    if missing:
        raise click.ClickException(f"the sheet is missing {sorted(missing)}")

    taken = 0
    with ctx.work() as work:
        for number, row in enumerate(rows, start=2):
            try:
                work.events.record(build(row))
            except EventError as bad:
                raise click.ClickException(f"line {number}: {bad}") from bad
            taken += 1
        work.commit()
    click.echo(f"took {taken} events off {sheet.name}")


@events.command("show")
@click.argument("car")
@pass_context
def show(ctx: Context, car: str) -> None:
    """One car's stream and where it leaves the car."""
    mark = CarMark.parse(car)
    with ctx.work() as work:
        stream = work.events.stream(mark)
        state = replay(stream)
    for event in stream:
        click.echo(str(event))
    click.echo(f"-> {state}")

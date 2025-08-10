"""Interchange file commands.

Receiving is the one command in here that anybody uses in anger, and it is
built around the fact that the files are not always good. It reads the whole
file, reports every line it could not read, and then refuses to record
anything unless the caller says to take the good half. A file with three bad
lines out of two hundred is a file somebody has to look at, not one to load
quietly.

Sending is the other direction and it is dull, which is how it should be.
"""

from __future__ import annotations

from datetime import datetime, time
from pathlib import Path

import click

from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import SHEET, a_date
from drawbarline.clock import to_local
from drawbarline.errors import DrawbarlineError
from drawbarline.interchange.sheet import Header, parse, render
from drawbarline.marks import ReportingMark
from drawbarline.reference import drawbar_line


@cli.group()
def interchange() -> None:
    """The files the connections send and the ones we send back."""


@interchange.command("receive")
@click.argument("file", type=SHEET)
@click.option(
    "--partial",
    is_flag=True,
    help="Take the records that read even though some did not.",
)
@pass_context
def receive(ctx: Context, file: Path, partial: bool) -> None:
    """Read a sheet and record what is on it."""
    sheet = parse(file.read_text(encoding="utf-8"))
    for problem in sheet.problems:
        click.echo(f"  {problem}", err=True)
    if sheet.header is None:
        raise click.ClickException(f"{file.name} has no header")
    if not sheet.counted:
        click.echo(f"  trailer says {sheet.declared} and {len(sheet.events)} read", err=True)
    if (sheet.problems or not sheet.counted) and not partial:
        raise click.ClickException(f"{file.name} did not read cleanly, nothing taken")

    with ctx.work() as work:
        for event in sheet.events:
            try:
                work.events.record(event)
            except DrawbarlineError as bad:
                raise click.ClickException(f"{event.car}: {bad}") from bad
        work.commit()
    click.echo(
        f"took {len(sheet.events)} records off {file.name} "
        f"from {sheet.header.road} at {sheet.header.junction}"
    )


@interchange.command("send")
@click.option("--road", required=True, help="The road the file goes to.")
@click.option("--junction", required=True, help="The station code we hand over at.")
@click.option("--day", required=True, help="The day it covers, yyyy-mm-dd.")
@click.option("--sequence", default=1, show_default=True, help="File sequence number.")
@click.option(
    "--out",
    type=click.Path(dir_okay=False, path_type=Path),
    default=None,
    help="Where to write it. Standard output by default.",
)
@pass_context
def send(
    ctx: Context,
    road: str,
    junction: str,
    day: str,
    sequence: int,
    out: Path | None,
) -> None:
    """Write the day's interchange with one road as a sheet."""
    mark = ReportingMark.parse(road)
    station = junction.strip().upper()
    on = a_date(day)
    place = drawbar_line().station(station)

    with ctx.work() as work:
        log = work.events.log()
    events = [
        one
        for one in log.at_station(station)
        if one.road == mark and to_local(place, one.when).date() == on
    ]
    header = Header(mark, station, datetime.combine(on, time.min), sequence)
    text = render(header, events)
    if out is None:
        click.echo(text, nl=False)
    else:
        out.write_text(text, encoding="utf-8")
        click.echo(f"wrote {len(events)} records to {out.name}")

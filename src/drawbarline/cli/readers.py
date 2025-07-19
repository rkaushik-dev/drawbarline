"""Reading the things a command line argument can be.

Four command modules grew their own date reader, and by the time there were
four of them two accepted a month where the others wanted a day and one raised
a bare ValueError that reached the user as a traceback. So they are here, once,
and every command reads its arguments through them.

Everything in here refuses rather than guesses, and every refusal names the
argument and shows the form it wanted. A command line is the one interface
where the user cannot see a schema, so the error message is the schema.
"""

from __future__ import annotations

import csv
from datetime import date, datetime
from pathlib import Path

import click

from drawbarline.clock import to_utc
from drawbarline.reference import drawbar_line
from drawbarline.settlement.period import Period

SHEET = click.Path(exists=True, dir_okay=False, path_type=Path)

DAY_FORM = "yyyy-mm-dd"
MONTH_FORM = "yyyy-mm"
READING_FORM = "yyyy-mm-dd hh:mm"


def a_date(text: str) -> date:
    """A day, written the way every filing writes one."""
    try:
        return datetime.strptime(text.strip(), "%Y-%m-%d").date()
    except ValueError as bad:
        raise click.ClickException(f"{text!r} is not a date, write it as {DAY_FORM}") from bad


def a_month(text: str) -> Period:
    """An accounting month."""
    try:
        found = datetime.strptime(text.strip(), "%Y-%m")
    except ValueError as bad:
        raise click.ClickException(f"{text!r} is not a month, write it as {MONTH_FORM}") from bad
    return Period(found.year, found.month)


def a_moment(station_code: str, text: str, *, second_pass: bool = False) -> datetime:
    """A local clock reading at a station, read as a moment."""
    try:
        local = datetime.strptime(text.strip(), "%Y-%m-%d %H:%M")
    except ValueError as bad:
        raise click.ClickException(
            f"{text!r} is not a reading, write it as {READING_FORM}"
        ) from bad
    station = drawbar_line().station(station_code)
    return to_utc(station, local, second_pass=second_pass)


def rows_of(path: Path) -> list[dict[str, str]]:
    """Every row of a comma separated sheet, keyed on its heading."""
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def require_columns(rows: list[dict[str, str]], wanted: set[str], name: str) -> None:
    """Refuse a sheet that does not carry the columns a loader needs."""
    missing = wanted - set(rows[0] if rows else {})
    if missing:
        raise click.ClickException(f"{name} is missing {sorted(missing)}")

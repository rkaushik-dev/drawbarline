"""The command line.

One group, one database option, and every command opening its own unit of
work. Nothing here holds a connection between commands, because the office
runs these out of a scheduler one at a time and a long lived connection to a
file on a share is a way to lose a month.

Every command that writes commits at the end and only at the end. A command
that raises leaves the database exactly as it found it, and the error the user
sees is the domain error with its context, not a traceback.
"""

from __future__ import annotations

import sys
from pathlib import Path

import click

from drawbarline import __version__
from drawbarline.errors import DrawbarlineError
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.numbering import SERIES
from drawbarline.persistence.uow import UnitOfWork

DEFAULT_DATABASE = "drawbarline.db"

STARTING_NUMBERS = {
    "demurrage_invoice": 5001,
    "statement": 9001,
    "repair_card": 88001,
    "waybill": 40001,
}


def url_for(database: str) -> str:
    return f"sqlite+pysqlite:///{database}"


class Context:
    """What every command needs and nothing more."""

    def __init__(self, database: str) -> None:
        self.database = database
        self.engine = make_engine(url_for(database))

    def work(self) -> UnitOfWork:
        return UnitOfWork(self.engine)


pass_context = click.make_pass_decorator(Context)


@click.group()
@click.version_option(__version__)
@click.option(
    "--database",
    default=DEFAULT_DATABASE,
    show_default=True,
    help="The sqlite file the books live in.",
)
@click.pass_context
def cli(ctx: click.Context, database: str) -> None:
    """Car hire, demurrage and interchange settlement."""
    ctx.obj = Context(database)


@cli.command()
@pass_context
def init(ctx: Context) -> None:
    """Build the schema and open the document number series."""
    if Path(ctx.database).exists():
        raise click.ClickException(f"{ctx.database} is already there")
    create_all(ctx.engine)
    with ctx.work() as work:
        for name in SERIES:
            work.numbering.start(name, STARTING_NUMBERS[name])
        work.commit()
    click.echo(f"built {ctx.database}")


@cli.command()
@pass_context
def status(ctx: Context) -> None:
    """How much is on the books."""
    with ctx.work() as work:
        cars = len(work.fleet)
        events = len(work.events)
        postings = len(work.ledger)
        closed = work.periods.closed_periods()
    click.echo(f"cars      {cars}")
    click.echo(f"events    {events}")
    click.echo(f"postings  {postings}")
    click.echo(f"closed    {len(closed)} months")
    if closed:
        click.echo(f"latest    {closed[-1]}")


def run() -> int:
    """The console entry point, turning a domain refusal into a clean exit."""
    try:
        cli(standalone_mode=False)
    except click.ClickException as bad:
        click.echo(f"error: {bad.format_message()}", err=True)
        return 2
    except click.Abort:
        click.echo("aborted", err=True)
        return 130
    except DrawbarlineError as bad:
        click.echo(f"error: {bad}", err=True)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(run())

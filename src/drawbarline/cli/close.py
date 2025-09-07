"""Close and statement commands.

The close is the one command in here that changes a lot at once, so it prints
what it did rather than saying nothing and exiting zero. A clerk running it at
the start of the month wants to see the car hire, the reclaims, the demurrage
and the statements, and wants to see them before anybody sends them out.

A dry run does everything except commit. That is not a separate code path, it
is the same run with the commit left off, which is the only kind of dry run
worth having.
"""

from __future__ import annotations

import click

from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import a_date, a_month
from drawbarline.marks import ReportingMark
from drawbarline.service import UNSIGNED, reopen_period, run_close
from drawbarline.settlement.statement import cut


@cli.command("close")
@click.argument("month")
@click.option("--issued", required=True, help="The day the statements are cut, yyyy-mm-dd.")
@click.option("--dry-run", is_flag=True, help="Work the month out and change nothing.")
@click.option("--by", "actor", default=UNSIGNED, help="Whose initials go on it.")
@pass_context
def close(ctx: Context, month: str, issued: str, dry_run: bool, actor: str) -> None:
    """Work a month through and shut it."""
    period = a_month(month)
    on = a_date(issued)
    if on < period.last_day:
        raise click.ClickException("a month is closed after it ends")

    with ctx.work() as work:
        result = run_close(work, period, issued=on, actor=actor)
        click.echo(f"month      {result.period}")
        click.echo(f"car hire   {result.car_hire}")
        click.echo(f"reclaimed  {result.reclaimed}")
        click.echo(f"demurrage  {result.demurrage}")
        click.echo(f"postings   {result.posted}")
        for statement in result.statements:
            click.echo(f"statement  {statement}")
        for invoice in result.invoices:
            click.echo(f"invoice    {invoice}")
        if dry_run:
            click.echo("dry run, nothing written")
            return
        work.commit()
    click.echo(f"closed {period}")


@cli.command("statement")
@click.argument("road")
@click.argument("month")
@click.option("--number", default=1, show_default=True, help="Statement number to print on it.")
@pass_context
def statement(ctx: Context, road: str, month: str, number: int) -> None:
    """Print the statement for one road and one month."""
    period = a_month(month)
    mark = ReportingMark.parse(road)
    with ctx.work() as work:
        ledger = work.ledger.ledger()
    found = cut(ledger, mark, period, number=number, issued=period.last_day)
    click.echo(f"road      {found.road}")
    click.echo(f"month     {found.period}")
    click.echo(f"opening   {found.opening}")
    for line in found.lines:
        click.echo(f"  {line}")
    click.echo(f"closing   {found.closing}")
    click.echo(f"          {found.direction()}")


@cli.command("audit")
@click.option("--limit", default=20, show_default=True, help="How many to show.")
@click.option("--by", "actor", default=None, help="Only one person.")
@pass_context
def audit(ctx: Context, limit: int, actor: str | None) -> None:
    """Who decided what, newest first."""
    with ctx.work() as work:
        found = work.audit.by_actor(actor) if actor else work.audit.entries(limit)
    for entry in found:
        click.echo(str(entry))
    if not found:
        click.echo("nothing on the trail yet")


@cli.command("periods")
@pass_context
def periods(ctx: Context) -> None:
    """Which months are shut and which have been opened again."""
    with ctx.work() as work:
        closed = work.periods.closed_periods()
        reopened = work.periods.reopenings()
    for period in closed:
        click.echo(f"closed    {period}")
    for period, on, reason in reopened:
        click.echo(f"reopened  {period} on {on}, {reason}")
    if not closed and not reopened:
        click.echo("nothing closed yet")


@cli.command("reopen")
@click.argument("month")
@click.option("--on", required=True, help="The day it was opened again, yyyy-mm-dd.")
@click.option("--reason", required=True, help="Why it had to be.")
@click.option("--by", "actor", default=UNSIGNED, help="Whose initials go on it.")
@pass_context
def reopen(ctx: Context, month: str, on: str, reason: str, actor: str) -> None:
    """Open a closed month again, with the reason on file."""
    period = a_month(month)
    with ctx.work() as work:
        reopen_period(work, period, on=a_date(on), reason=reason, actor=actor)
        work.commit()
    click.echo(f"reopened {period}")

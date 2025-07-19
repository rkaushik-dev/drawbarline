"""Fleet commands.

Reading a car off a command line means parsing the same strings a clerk writes
on a wheel report, so the arguments are marks and stencil numbers rather than
identifiers somebody would have to look up. Anything that cannot be read comes
back as a refusal naming the argument.
"""

from __future__ import annotations

import click

from drawbarline.cli.main import Context, cli, pass_context
from drawbarline.cli.readers import a_date
from drawbarline.fleet.aar import CarTypeCode, MechanicalDesignation
from drawbarline.fleet.car import Car
from drawbarline.marks import CarMark
from drawbarline.measure import Length
from drawbarline.weight import Weight


@cli.group()
def fleet() -> None:
    """The equipment register."""


@fleet.command("add")
@click.argument("mark")
@click.option("--designation", required=True, help="Mechanical designation, such as LO.")
@click.option("--type-code", required=True, help="AAR car type code, such as L351.")
@click.option("--built", required=True, help="Build date, yyyy-mm-dd.")
@click.option("--light-weight", required=True, type=int, help="Light weight in pounds.")
@click.option("--gross-rail-load", required=True, type=int, help="Gross rail load in pounds.")
@click.option("--length", required=True, help="Length over pulling faces, such as 59-6.")
@click.option("--rebuilt", default=None, help="Rebuild date, yyyy-mm-dd.")
@pass_context
def add_car(
    ctx: Context,
    mark: str,
    designation: str,
    type_code: str,
    built: str,
    light_weight: int,
    gross_rail_load: int,
    length: str,
    rebuilt: str | None,
) -> None:
    """File an equipment record."""
    car = Car(
        mark=CarMark.parse(mark),
        designation=MechanicalDesignation.parse(designation),
        type_code=CarTypeCode.parse(type_code),
        built=a_date(built),
        light_weight=Weight(light_weight),
        gross_rail_load=Weight(gross_rail_load),
        outside_length=Length.parse(length),
        rebuilt=a_date(rebuilt) if rebuilt else None,
    )
    with ctx.work() as work:
        work.fleet.add(car)
        work.commit()
    click.echo(f"filed {car}")


@fleet.command("list")
@click.option("--owner", default=None, help="Only cars under one reporting mark.")
@pass_context
def list_cars(ctx: Context, owner: str | None) -> None:
    """Every car on the register."""
    with ctx.work() as work:
        cars = work.fleet.all()
    if owner:
        cars = [car for car in cars if str(car.owner) == owner.upper()]
    for car in cars:
        letters = f"{car.designation}"
        click.echo(f"{car.mark}  {letters:<4} {car.type_code}  {car.load_limit}")
    click.echo(f"{len(cars)} cars")


@fleet.command("show")
@click.argument("mark")
@pass_context
def show_car(ctx: Context, mark: str) -> None:
    """Everything on one car."""
    car_mark = CarMark.parse(mark)
    with ctx.work() as work:
        car = work.fleet.get(car_mark)
        ownership = work.fleet.ownership(car_mark)
        retired = work.fleet.retired_on(car_mark)
    click.echo(f"car          {car.mark}")
    click.echo(f"stencil      {car.mark.stencilled()}")
    click.echo(f"kind         {car.designation} {car.type_code} {car.kind.description}")
    click.echo(f"built        {car.built}")
    if car.rebuilt:
        click.echo(f"rebuilt      {car.rebuilt}")
    click.echo(f"light weight {car.light_weight}")
    click.echo(f"load limit   {car.load_limit}")
    click.echo(f"expires      {car.interchange_expires}")
    for term in ownership.terms:
        click.echo(f"lease        {term}")
    if retired:
        click.echo(f"retired      {retired}")


@fleet.command("retire")
@click.argument("mark")
@click.option("--on", required=True, help="The day it came off, yyyy-mm-dd.")
@click.option("--reason", default="", help="Why.")
@pass_context
def retire_car(ctx: Context, mark: str, on: str, reason: str) -> None:
    """Take a car off the roster."""
    with ctx.work() as work:
        work.fleet.retire(CarMark.parse(mark), a_date(on), reason)
        work.commit()
    click.echo(f"retired {mark} on {on}")

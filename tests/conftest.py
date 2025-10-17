"""Fixtures the command line tests share.

Six command line modules were each building the same railroad from scratch:
init the database, load the rates, publish the tariff, file the shippers and
the industry track, file a couple of cars. That is a dozen invocations a test
and it was most of the time the suite spent.

So it is built once and copied. A sqlite database is a file, and copying a
sixty kilobyte file is a great deal cheaper than replaying the commands that
made it. Each test still gets its own copy and can write to it freely, which
is the property that matters.
"""

from __future__ import annotations

import shutil
from pathlib import Path

import pytest
from click.testing import CliRunner

from drawbarline.cli import cli

RATES = """type_code,from_age,to_age,effective,hourly,mileage
L351,0,9,2024-07-01,4630,1210
L351,10,99,2024-07-01,3180,1210
"""

SHIPPERS = """code,name,average_agreement
ESCPAP,Escanaba Paper,no
PWRGRN,Powers Grain,yes
"""

INDUSTRIES = """code,name,station,shipper,spots,switched
ESCPAP1,mill house track,ESC,ESCPAP,2,
PWRGRN1,elevator,PWR,PWRGRN,20,
"""

PRICES = """job,effective,labor_hundredths,material_cents
4312,2024-07-01,250,128000
5104,2024-07-01,125,84000
"""

CARS = ("CN 400100", "CN 400101", "CN 400102", "DBL 104207")

TARIFF = (
    "reference",
    "tariff",
    "4200",
    "--effective",
    "2024-07-01",
    "--free-loaded",
    "2",
    "--free-empty",
    "1",
    "--step",
    "2:10000",
    "--step",
    "0:20000",
)


def run(database: str, *args: str):
    """One command against one database."""
    return CliRunner().invoke(cli, ["--database", database, *args])


def car_args(mark: str) -> list[str]:
    return [
        "fleet",
        "add",
        mark,
        "--designation",
        "LO",
        "--type-code",
        "L351",
        "--built",
        "2020-06-01",
        "--light-weight",
        "63500",
        "--gross-rail-load",
        "286000",
        "--length",
        "59-6",
    ]


def build(into: Path) -> str:
    """A railroad with its filings loaded and a few cars on the register."""
    database = str(into / "books.db")
    run(database, "init")
    for name, text in (
        ("rates.csv", RATES),
        ("shippers.csv", SHIPPERS),
        ("industries.csv", INDUSTRIES),
        ("prices.csv", PRICES),
    ):
        (into / name).write_text(text, encoding="utf-8")
    run(database, "reference", "rates", str(into / "rates.csv"))
    run(database, "reference", "shippers", str(into / "shippers.csv"))
    run(database, "reference", "industries", str(into / "industries.csv"))
    run(database, *TARIFF)
    run(database, "repairs", "price", str(into / "prices.csv"))
    run(database, "repairs", "depreciation", "43", "--percent", "4", "--floor", "20")
    run(database, "repairs", "labor", "CN", "--effective", "2024-07-01", "--cents", "4200")
    for mark in CARS:
        run(database, *car_args(mark))
    return database


@pytest.fixture(scope="session")
def template(tmp_path_factory) -> Path:
    """The loaded railroad, built once for the whole run."""
    return Path(build(tmp_path_factory.mktemp("template")))


@pytest.fixture
def books(template: Path, tmp_path: Path) -> str:
    """A private copy of the loaded railroad, for one test to write to."""
    target = tmp_path / "books.db"
    shutil.copyfile(template, target)
    return str(target)


@pytest.fixture
def empty(tmp_path: Path) -> str:
    """A database with the schema and the counters and nothing else."""
    database = str(tmp_path / "empty.db")
    run(database, "init")
    return database

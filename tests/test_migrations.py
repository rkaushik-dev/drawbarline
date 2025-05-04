"""The migration and the models have to describe the same schema.

A model changed without a migration is the most common way this kind of
application breaks, and it breaks on somebody else's machine rather than on
the one where the change was made, because the developer's database was built
by create_all and theirs by alembic. So the test builds one of each and
compares them column by column.
"""

from __future__ import annotations

from pathlib import Path

import pytest
from alembic import command
from alembic.config import Config
from sqlalchemy import inspect

from drawbarline.persistence import models  # noqa: F401  registers every table
from drawbarline.persistence.base import Base, create_all, make_engine

ROOT = Path(__file__).resolve().parent.parent
VERSIONS = ROOT / "migrations" / "versions"


def alembic_config(url: str) -> Config:
    config = Config(str(ROOT / "alembic.ini"))
    config.set_main_option("script_location", str(ROOT / "migrations"))
    config.set_main_option("sqlalchemy.url", url)
    return config


@pytest.fixture
def migrated(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'migrated.db'}"
    command.upgrade(alembic_config(url), "head")
    return make_engine(url)


@pytest.fixture
def built(tmp_path):
    engine = make_engine(f"sqlite+pysqlite:///{tmp_path / 'built.db'}")
    create_all(engine)
    return engine


def tables_of(engine):
    found = set(inspect(engine).get_table_names())
    return found - {"alembic_version"}


def columns_of(engine, table):
    return {column["name"] for column in inspect(engine).get_columns(table)}


def indexes_of(engine, table):
    return {index["name"] for index in inspect(engine).get_indexes(table)}


def test_the_revisions_are_numbered_in_one_line():
    names = [path.name for path in sorted(VERSIONS.glob("*.py"))]
    assert names[0] == "0001_baseline.py"
    assert [name[:4] for name in names] == [f"{n + 1:04d}" for n in range(len(names))]


def test_the_migration_builds_every_table_the_models_declare(migrated):
    assert tables_of(migrated) == set(Base.metadata.tables)


def test_the_two_ways_of_building_agree_on_the_tables(migrated, built):
    assert tables_of(migrated) == tables_of(built)


@pytest.mark.parametrize("table", sorted(Base.metadata.tables))
def test_the_two_ways_agree_on_the_columns(migrated, built, table):
    assert columns_of(migrated, table) == columns_of(built, table)


@pytest.mark.parametrize("table", sorted(Base.metadata.tables))
def test_the_two_ways_agree_on_the_indexes(migrated, built, table):
    assert indexes_of(migrated, table) == indexes_of(built, table)


def test_the_migration_can_be_taken_back_off(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'down.db'}"
    config = alembic_config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    assert tables_of(make_engine(url)) == set()


def test_the_migration_can_be_run_twice_over(tmp_path):
    url = f"sqlite+pysqlite:///{tmp_path / 'twice.db'}"
    config = alembic_config(url)
    command.upgrade(config, "head")
    command.downgrade(config, "base")
    command.upgrade(config, "head")
    assert tables_of(make_engine(url)) == set(Base.metadata.tables)

"""What has to be true of the thing that gets installed.

The suite runs against the source tree, so nothing in it notices a file that
is not in the wheel or an entry point that does not resolve. These read the
metadata and the filesystem instead.
"""

from __future__ import annotations

import importlib
import tomllib
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = ROOT / "src" / "drawbarline"


def metadata() -> dict:
    with (ROOT / "pyproject.toml").open("rb") as handle:
        return tomllib.load(handle)


def test_the_typing_marker_is_there():
    assert (PACKAGE / "py.typed").exists()


def test_the_typing_marker_is_shipped():
    data = metadata()["tool"]["setuptools"]["package-data"]
    assert "py.typed" in data["drawbarline"]


def test_the_console_script_is_declared():
    assert metadata()["project"]["scripts"] == {"drawbarline": "drawbarline.cli:run"}


def test_the_console_script_resolves():
    module, _, name = metadata()["project"]["scripts"]["drawbarline"].partition(":")
    found = getattr(importlib.import_module(module), name)
    assert callable(found)


def test_the_package_runs_as_a_module():
    assert (PACKAGE / "__main__.py").exists()
    module = importlib.import_module("drawbarline.__main__")
    assert hasattr(module, "run")


def test_importing_the_package_opens_nothing():
    """A package that touches a database on import cannot be imported twice."""
    source = (PACKAGE / "__init__.py").read_text(encoding="utf-8")
    for forbidden in ("import os", "make_engine", "logging.basicConfig", "getenv"):
        assert forbidden not in source


def test_the_version_is_a_release_number():
    module = importlib.import_module("drawbarline")
    assert module.__version__ == metadata()["project"]["version"]


def test_the_package_declares_a_readme():
    assert metadata()["project"]["readme"] == "README.md"
    assert (ROOT / "README.md").exists()


def test_the_package_declares_a_licence():
    assert metadata()["project"]["license"]["text"]


def test_every_subpackage_has_an_init():
    for folder in PACKAGE.rglob("*"):
        if not folder.is_dir() or folder.name == "__pycache__":
            continue
        assert (folder / "__init__.py").exists(), folder.name


@pytest.mark.parametrize(
    "folder",
    sorted(
        one.name
        for one in (ROOT / "src" / "drawbarline").iterdir()
        if one.is_dir() and one.name != "__pycache__"
    ),
)
def test_every_subpackage_says_what_it_is(folder):
    source = (PACKAGE / folder / "__init__.py").read_text(encoding="utf-8")
    assert source.lstrip().startswith('"""'), folder


def test_every_shipped_module_has_a_docstring():
    missing = []
    for path in sorted(PACKAGE.rglob("*.py")):
        source = path.read_text(encoding="utf-8").lstrip()
        if not source.startswith('"""'):
            missing.append(path.name)
    assert missing == []


def test_the_alembic_directory_is_outside_the_package():
    """Migrations are not importable code and do not belong in the wheel."""
    assert not (PACKAGE / "migrations").exists()
    assert (ROOT / "migrations").exists()

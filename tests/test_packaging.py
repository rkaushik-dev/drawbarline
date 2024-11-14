"""What the package declares has to match what the package imports.

A test suite is the one place a development dependency is always installed, so
a suite structurally cannot notice that a shipped module imports something the
wheel does not pull in. It runs green here and the installed command fails to
start on somebody else's machine. So this reads the metadata instead of
running the code.
"""

from __future__ import annotations

import ast
import sys
import tomllib
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
PACKAGE = ROOT / "src" / "drawbarline"
FIRST_PARTY = {"drawbarline"}

SHIPPED = sorted(PACKAGE.rglob("*.py"))


def metadata():
    with (ROOT / "pyproject.toml").open("rb") as handle:
        return tomllib.load(handle)


def requirement_name(spec: str) -> str:
    """The distribution name out of a requirement line."""
    head = spec.split(";", 1)[0]
    for mark in ("==", ">=", "<=", "~=", "!=", ">", "<", "["):
        head = head.split(mark, 1)[0]
    return head.strip().lower().replace("-", "_")


def imported_by(path: Path) -> set[str]:
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    found: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            found.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            found.add(node.module.split(".")[0])
    return found


def declared_runtime() -> set[str]:
    return {requirement_name(spec) for spec in metadata()["project"]["dependencies"]}


def declared_dev() -> set[str]:
    extras = metadata()["project"]["optional-dependencies"]
    return {requirement_name(spec) for group in extras.values() for spec in group}


def test_the_package_ships_at_least_one_module():
    assert SHIPPED


@pytest.mark.parametrize("path", SHIPPED, ids=lambda p: p.name)
def test_every_third_party_import_is_a_declared_runtime_dependency(path):
    outside = imported_by(path) - sys.stdlib_module_names - FIRST_PARTY
    assert outside <= declared_runtime(), f"{path.name} imports something undeclared"


@pytest.mark.parametrize("path", SHIPPED, ids=lambda p: p.name)
def test_no_shipped_module_imports_a_development_only_package(path):
    dev_only = declared_dev() - declared_runtime()
    assert not (imported_by(path) & dev_only)


def test_every_runtime_dependency_is_pinned():
    for spec in metadata()["project"]["dependencies"]:
        assert "==" in spec, f"{spec} is not pinned"


def test_every_development_dependency_is_pinned():
    extras = metadata()["project"]["optional-dependencies"]
    for group in extras.values():
        for spec in group:
            assert "==" in spec, f"{spec} is not pinned"


def test_the_package_is_found_under_src():
    assert metadata()["tool"]["setuptools"]["packages"]["find"]["where"] == ["src"]


def test_the_declared_python_floor_matches_what_ruff_targets():
    assert metadata()["project"]["requires-python"] == ">=3.12"
    assert metadata()["tool"]["ruff"]["target-version"] == "py312"


def test_mypy_and_ruff_agree_on_the_python_version():
    assert metadata()["tool"]["mypy"]["python_version"] == "3.12"


def test_the_suite_runs_from_the_tests_directory():
    assert metadata()["tool"]["pytest"]["ini_options"]["testpaths"] == ["tests"]

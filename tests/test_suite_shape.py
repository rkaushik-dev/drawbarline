"""What the suite itself has to look like.

The suite runs in parallel and it is split by file, so two tests in different
files never share a database and two in the same file run in order. That only
holds while every test builds its own fixtures, which is easy to break by
adding a module level engine that looks harmless.

These checks are cheap and they catch the shape problems that turn into a test
that passes alone and fails in a run.
"""

from __future__ import annotations

import ast
import tomllib
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
TESTS = sorted((ROOT / "tests").glob("test_*.py"))


def metadata():
    with (ROOT / "pyproject.toml").open("rb") as handle:
        return tomllib.load(handle)


def test_the_suite_runs_in_parallel_split_by_file():
    addopts = metadata()["tool"]["pytest"]["ini_options"]["addopts"]
    assert "-n auto" in addopts
    assert "--dist loadfile" in addopts


def test_the_parallel_runner_is_a_declared_development_dependency():
    dev = metadata()["project"]["optional-dependencies"]["dev"]
    assert any(one.startswith("pytest-xdist==") for one in dev)


def test_there_are_test_files_to_run():
    assert len(TESTS) > 20


@pytest.mark.parametrize("path", TESTS, ids=lambda p: p.name)
def test_no_test_module_opens_a_database_at_import_time(path):
    """A module level engine is shared by every worker that imports it."""
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    for node in tree.body:
        if not isinstance(node, ast.Assign):
            continue
        for call in ast.walk(node):
            if isinstance(call, ast.Call) and isinstance(call.func, ast.Name):
                assert call.func.id not in ("make_engine", "create_all")


@pytest.mark.parametrize("path", TESTS, ids=lambda p: p.name)
def test_every_test_module_holds_at_least_one_test(path):
    tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
    found = [
        node
        for node in tree.body
        if isinstance(node, ast.FunctionDef) and node.name.startswith("test_")
    ]
    assert found, f"{path.name} defines no tests"


def test_no_two_test_modules_share_a_name():
    names = [path.name for path in TESTS]
    assert len(names) == len(set(names))

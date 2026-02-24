"""Every refusal in this package looks the same.

There is one error taxonomy and a caller catching its base is supposed to have
caught everything the package raises on purpose. That only holds while nobody
reaches for a bare ValueError or leaves an assert in, and both of those are
easy to do and invisible until somebody's error handler misses one.

So this walks the source. It is a lint rule that ruff does not have and it has
already caught two.
"""

from __future__ import annotations

import ast
import importlib
import pkgutil
from pathlib import Path

import pytest

import drawbarline
from drawbarline.errors import DrawbarlineError

PACKAGE = Path(drawbarline.__file__).resolve().parent
SHIPPED = sorted(PACKAGE.rglob("*.py"))

# Pydantic reads a ValueError out of a validator and turns it into its own
# response. That is the one place a bare one is the right answer.
VALIDATOR_DECORATORS = {"field_validator", "model_validator"}


def tree_of(path: Path) -> ast.Module:
    return ast.parse(path.read_text(encoding="utf-8"), filename=str(path))


def validator_bodies(tree: ast.Module) -> set[int]:
    """Line numbers inside a pydantic validator."""
    found: set[int] = set()
    for node in ast.walk(tree):
        if not isinstance(node, ast.FunctionDef):
            continue
        names: set[str] = set()
        for one in node.decorator_list:
            target = one.func if isinstance(one, ast.Call) else one
            if isinstance(target, ast.Name):
                names.add(target.id)
            elif isinstance(target, ast.Attribute):
                names.add(target.attr)
        if names & VALIDATOR_DECORATORS:
            for inner in ast.walk(node):
                if hasattr(inner, "lineno"):
                    found.add(inner.lineno)
    return found


def every_error_class() -> list[type[DrawbarlineError]]:
    found: list[type[DrawbarlineError]] = []
    for module in pkgutil.walk_packages(drawbarline.__path__, "drawbarline."):
        loaded = importlib.import_module(module.name)
        for value in vars(loaded).values():
            if (
                isinstance(value, type)
                and issubclass(value, DrawbarlineError)
                and value.__module__ == module.name
            ):
                found.append(value)
    return found


def test_there_are_modules_to_walk():
    assert len(SHIPPED) > 50


@pytest.mark.parametrize("path", SHIPPED, ids=lambda p: p.name)
def test_no_shipped_module_asserts(path):
    """An assert vanishes under python -O, and a check that vanishes is not one."""
    tree = tree_of(path)
    lines = [node.lineno for node in ast.walk(tree) if isinstance(node, ast.Assert)]
    assert lines == [], f"{path.name} asserts at {lines}"


@pytest.mark.parametrize("path", SHIPPED, ids=lambda p: p.name)
def test_no_shipped_module_raises_a_builtin(path):
    tree = tree_of(path)
    allowed = validator_bodies(tree)
    bad = []
    for node in ast.walk(tree):
        if not isinstance(node, ast.Raise) or node.exc is None:
            continue
        call = node.exc
        name = None
        if isinstance(call, ast.Call) and isinstance(call.func, ast.Name):
            name = call.func.id
        elif isinstance(call, ast.Name):
            name = call.id
        if name in {"Exception", "RuntimeError", "ValueError", "TypeError", "KeyError"}:
            if node.lineno in allowed:
                continue
            bad.append((name, node.lineno))
    assert bad == [], f"{path.name} raises {bad}"


def test_every_error_class_has_its_own_code():
    classes = every_error_class()
    codes = [one.code for one in classes if one is not DrawbarlineError]
    assert len(codes) == len(set(codes))


def test_every_code_is_lower_case_with_underscores():
    for one in every_error_class():
        assert one.code == one.code.lower()
        assert " " not in one.code
        assert "-" not in one.code


def test_every_error_class_carries_a_docstring():
    for one in every_error_class():
        assert (one.__doc__ or "").strip(), one.__name__


def test_every_error_class_descends_from_the_base():
    for one in every_error_class():
        assert issubclass(one, DrawbarlineError)


def test_the_taxonomy_is_worth_having():
    assert len(every_error_class()) > 20


def test_catching_the_base_catches_every_leaf():
    for one in every_error_class():
        with pytest.raises(DrawbarlineError):
            raise one("something went wrong")


def test_every_error_carries_its_context_into_the_message():
    for one in every_error_class():
        raised = one("something went wrong", where="here")
        assert "where='here'" in str(raised)

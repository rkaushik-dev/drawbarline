"""What the build has to keep doing.

The workflow is not covered by the suite in any other way, and the ways it
breaks are all the same shape: a job quietly dropped, a python version that
drifts away from what the package declares, a coverage floor removed because
somebody was in a hurry. Reading the file and asserting on it is cheap and it
catches exactly those.
"""

from __future__ import annotations

import tomllib
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parent.parent
WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"

JOBS = ("lint", "types", "tests", "coverage", "migrations", "package")


def workflow() -> str:
    return WORKFLOW.read_text(encoding="utf-8")


def metadata() -> dict:
    with (ROOT / "pyproject.toml").open("rb") as handle:
        return tomllib.load(handle)


def test_there_is_a_workflow():
    assert WORKFLOW.exists()


@pytest.mark.parametrize("job", JOBS)
def test_every_job_is_still_there(job):
    assert f"\n  {job}:\n" in workflow()


def test_the_suite_runs_on_both_platforms():
    text = workflow()
    assert "ubuntu-latest" in text
    assert "windows-latest" in text


def test_the_types_job_states_the_platform():
    assert "mypy --platform linux" in workflow()


def test_the_workflow_asks_for_the_python_the_package_declares():
    declared = metadata()["project"]["requires-python"].removeprefix(">=")
    assert f'python-version: "{declared}"' in workflow()


def test_the_workflow_only_reads():
    assert "permissions:\n  contents: read" in workflow()


def test_a_second_push_cancels_the_first():
    assert "cancel-in-progress: true" in workflow()


def test_the_coverage_floor_is_set():
    assert metadata()["tool"]["coverage"]["report"]["fail_under"] >= 90


def test_coverage_measures_branches():
    assert metadata()["tool"]["coverage"]["run"]["branch"] is True


def test_coverage_measures_the_package_and_not_the_tests():
    assert metadata()["tool"]["coverage"]["run"]["source"] == ["drawbarline"]


def test_coverage_is_told_the_workers_run_in_parallel():
    run = metadata()["tool"]["coverage"]["run"]
    assert run["parallel"] is True
    assert "multiprocessing" in run["concurrency"]


def test_the_coverage_job_does_not_carry_its_own_floor():
    """The number lives in pyproject so it is the same here and locally."""
    assert "--cov-fail-under" not in workflow()


def test_the_runner_is_pinned_to_a_major_version():
    text = workflow()
    assert "actions/checkout@v4" in text
    assert "actions/setup-python@v5" in text


def test_the_wheel_is_built_and_installed_on_its_own():
    text = workflow()
    assert "python -m build" in text
    assert "import drawbarline" in text

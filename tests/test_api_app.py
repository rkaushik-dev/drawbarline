import pytest
from fastapi.testclient import TestClient

from drawbarline import __version__
from drawbarline.api.app import make_app
from drawbarline.api.errors import BAD_REQUEST, CONFLICT, NOT_FOUND, status_for
from drawbarline.errors import (
    ConfigurationError,
    DrawbarlineError,
    DuplicateCar,
    EventError,
    MarkError,
    SettlementError,
    UnknownCar,
)
from drawbarline.persistence.base import create_all, make_engine


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    return TestClient(make_app(engine))


def test_health_says_the_process_is_up(client):
    found = client.get("/health")
    assert found.status_code == 200
    assert found.json() == {"status": "up", "version": __version__}


def test_the_books_come_back_empty(client):
    found = client.get("/books")
    assert found.status_code == 200
    assert found.json() == {
        "cars": 0,
        "events": 0,
        "postings": 0,
        "closed_months": 0,
    }


def test_a_route_nobody_wrote_is_a_not_found(client):
    assert client.get("/nowhere").status_code == 404


def test_the_application_names_itself(client):
    schema = client.get("/openapi.json").json()
    assert schema["info"]["title"] == "drawbarline"
    assert schema["info"]["version"] == __version__


def test_a_missing_car_is_a_not_found():
    assert status_for(UnknownCar("no record")) == NOT_FOUND


def test_a_duplicate_car_is_a_conflict():
    assert status_for(DuplicateCar("already there")) == CONFLICT


def test_an_event_that_will_not_sit_is_a_conflict():
    assert status_for(EventError("out of order")) == CONFLICT


def test_a_closed_month_is_a_conflict():
    assert status_for(SettlementError("closed")) == CONFLICT


def test_something_unreadable_is_unprocessable():
    assert status_for(MarkError("cannot read that")) == 422


def test_a_configuration_problem_is_a_bad_request():
    assert status_for(ConfigurationError("no database")) == BAD_REQUEST


def test_an_error_with_no_entry_falls_back_to_a_bad_request():
    class Unheard(DrawbarlineError):
        code = "unheard"

    assert status_for(Unheard("nobody decided")) == BAD_REQUEST


def test_a_refusal_comes_back_as_code_message_and_context():
    from drawbarline.api.errors import body_of

    found = body_of(UnknownCar("no record of that car", car="WC 000001"))
    assert found == {
        "code": "unknown_car",
        "message": "no record of that car",
        "context": {"car": "WC 000001"},
    }


def test_an_application_with_no_database_refuses():
    from fastapi import FastAPI

    from drawbarline.api.deps import engine_of

    with pytest.raises(ConfigurationError):
        engine_of(_request_for(FastAPI()))


def _request_for(app):
    from fastapi import Request

    return Request({"type": "http", "app": app, "headers": []})


def test_every_error_class_the_domain_raises_has_a_status():
    """A class nobody decided on falls back to four hundred, which hides it."""
    import importlib
    import pkgutil

    import drawbarline
    from drawbarline.api.errors import STATUS_FOR

    found = set()
    for module in pkgutil.walk_packages(drawbarline.__path__, "drawbarline."):
        if ".api." in module.name or module.name.endswith(".api"):
            continue
        loaded = importlib.import_module(module.name)
        for value in vars(loaded).values():
            if (
                isinstance(value, type)
                and issubclass(value, DrawbarlineError)
                and value is not DrawbarlineError
                and value.__module__ == module.name
            ):
                found.add(value)
    undecided = {one.__name__ for one in found if one not in STATUS_FOR}
    assert undecided == set()

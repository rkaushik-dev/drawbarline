import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.audit import Action
from drawbarline.clock import utc
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork

WHEN = utc(2025, 2, 10, 14, 30)


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    return TestClient(make_app(engine))


@pytest.fixture
def written(client):
    engine = client.app.state.drawbarline_engine
    with UnitOfWork(engine) as work:
        work.audit.record("rjs", Action.CLOSED_PERIOD, "2025-01", "monthly close", WHEN)
        work.audit.record("mkb", Action.RETIRED_CAR, "DBL 104207", "wreck", WHEN)
        work.audit.record("rjs", Action.REOPENED_PERIOD, "2025-01", "cn sheet", WHEN)
        work.commit()
    return client


def test_an_empty_trail_reads_back_empty(client):
    assert client.get("/audit").json() == []


def test_the_trail_reads_back(written):
    found = written.get("/audit").json()
    assert len(found) == 3
    assert {one["actor"] for one in found} == {"rjs", "mkb"}


def test_an_entry_carries_its_action_and_detail(written):
    found = written.get("/audit").json()
    closed = [one for one in found if one["action"] == "closed_period"][0]
    assert closed["subject"] == "2025-01"
    assert closed["detail"] == "monthly close"


def test_the_trail_filters_by_actor(written):
    found = written.get("/audit", params={"actor": "mkb"}).json()
    assert [one["subject"] for one in found] == ["DBL 104207"]


def test_the_trail_filters_by_action(written):
    found = written.get("/audit", params={"action": "reopened_period"}).json()
    assert len(found) == 1


def test_an_action_nobody_publishes_is_refused(written):
    found = written.get("/audit", params={"action": "danced"})
    assert found.status_code == 400
    assert "no such action" in found.json()["message"]


def test_the_trail_filters_by_subject(written):
    found = written.get("/audit", params={"subject": "2025-01"}).json()
    assert len(found) == 2


def test_the_limit_holds(written):
    found = written.get("/audit", params={"limit": 1}).json()
    assert len(found) == 1


def test_a_limit_below_one_is_refused(written):
    assert written.get("/audit", params={"limit": 0}).status_code == 422


def test_a_limit_past_the_cap_is_refused(written):
    assert written.get("/audit", params={"limit": 5000}).status_code == 422


def test_the_actions_are_published(client):
    found = client.get("/audit/actions").json()
    assert set(found) == {one.name.lower() for one in Action}


def test_there_is_no_route_that_writes_an_entry(client):
    assert client.post("/audit", json={"actor": "rjs"}).status_code == 405


def test_a_person_who_did_nothing_has_no_trail(written):
    assert written.get("/audit", params={"actor": "abc"}).json() == []

import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.persistence.base import create_all, make_engine

CAR = {
    "mark": "CN 400100",
    "designation": "LO",
    "type_code": "L351",
    "built": "2020-06-01",
    "light_weight": 63500,
    "gross_rail_load": 286000,
    "outside_length": 714,
}

RECEIVED = {
    "car": "CN 400100",
    "kind": "received",
    "station": "ESC",
    "local": "2025-01-06T07:00:00",
    "road": "CN",
    "waybill": 41207,
}


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    client = TestClient(make_app(engine))
    client.post("/cars", json=CAR)
    return client


def test_an_event_is_recorded(client):
    found = client.post("/events", json=RECEIVED)
    assert found.status_code == 201
    assert found.json()["kind"] == "received"


def test_a_local_reading_is_read_at_the_station(client):
    found = client.post("/events", json=RECEIVED).json()
    assert found["moment"].startswith("2025-01-06T12:00:00")


def test_the_same_reading_at_marinette_is_an_hour_later(client):
    found = client.post(
        "/events",
        json={**RECEIVED, "station": "MRN", "local": "2025-01-06T07:00:00"},
    ).json()
    assert found["moment"].startswith("2025-01-06T13:00:00")


def test_an_instant_can_be_given_instead(client):
    found = client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "received",
            "station": "ESC",
            "moment": "2025-01-06T12:00:00Z",
            "road": "CN",
        },
    )
    assert found.status_code == 201


def test_giving_both_clocks_is_refused(client):
    found = client.post("/events", json={**RECEIVED, "moment": "2025-01-06T12:00:00Z"})
    assert found.status_code == 422


def test_giving_neither_clock_is_refused(client):
    body = {key: value for key, value in RECEIVED.items() if key != "local"}
    assert client.post("/events", json=body).status_code == 422


def test_a_local_reading_carrying_a_zone_is_refused(client):
    found = client.post("/events", json={**RECEIVED, "local": "2025-01-06T07:00:00Z"})
    assert found.status_code == 409


def test_a_reading_that_never_happened_is_refused(client):
    found = client.post(
        "/events",
        json={**RECEIVED, "station": "PWR", "local": "2025-03-09T02:30:00"},
    )
    assert found.status_code == 422


def test_the_ambiguous_hour_takes_the_flag(client):
    first = client.post(
        "/events",
        json={**RECEIVED, "station": "PWR", "local": "2025-11-02T01:30:00"},
    )
    assert first.status_code == 201
    second = client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "held",
            "station": "PWR",
            "local": "2025-11-02T01:30:00",
            "second_pass": True,
        },
    )
    assert second.status_code == 201


def test_an_interchange_with_no_road_is_refused(client):
    body = {key: value for key, value in RECEIVED.items() if key != "road"}
    assert client.post("/events", json=body).status_code == 409


def test_a_movement_with_no_distance_is_refused(client):
    found = client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "moved",
            "station": "PWR",
            "local": "2025-01-06T13:00:00",
        },
    )
    assert found.status_code == 409


def test_the_same_event_twice_is_a_conflict(client):
    client.post("/events", json=RECEIVED)
    assert client.post("/events", json=RECEIVED).status_code == 409


def test_a_kind_nobody_publishes_is_refused(client):
    assert client.post("/events", json={**RECEIVED, "kind": "shunted"}).status_code == 409


def test_a_stream_comes_back_in_order(client):
    client.post("/events", json=RECEIVED)
    client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "moved",
            "station": "PWR",
            "local": "2025-01-06T13:00:00",
            "tenths": 336,
        },
    )
    found = client.get("/events/cars/CN 400100").json()
    assert [one["kind"] for one in found] == ["received", "moved"]


def test_a_car_with_nothing_on_it_has_an_empty_stream(client):
    assert client.get("/events/cars/CN 400100").json() == []


def test_the_state_replays_the_stream(client):
    client.post("/events", json=RECEIVED)
    found = client.get("/events/cars/CN 400100/state").json()
    assert found["on_line"] is True
    assert found["station"] == "ESC"
    assert found["waybill"] == 41207
    assert found["events"] == 1


def test_a_car_off_line_reads_as_such(client):
    found = client.get("/events/cars/CN 400100/state").json()
    assert found["on_line"] is False
    assert found["events"] == 0


def test_a_bad_ordered_car_is_not_movable(client):
    client.post("/events", json=RECEIVED)
    client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "bad_ordered",
            "station": "ESC",
            "local": "2025-01-08T08:00:00",
        },
    )
    found = client.get("/events/cars/CN 400100/state").json()
    assert found["bad_ordered"] is True
    assert found["movable"] is False


def test_a_batch_goes_in_together(client):
    found = client.post(
        "/events/batch",
        json=[
            RECEIVED,
            {
                "car": "CN 400100",
                "kind": "moved",
                "station": "PWR",
                "local": "2025-01-06T13:00:00",
                "tenths": 336,
            },
        ],
    )
    assert found.status_code == 201
    assert found.json() == {"recorded": 2}


def test_a_batch_with_a_bad_line_records_none_of_it(client):
    client.post("/events/batch", json=[RECEIVED, RECEIVED])
    assert client.get("/events/cars/CN 400100").json() == []


def test_an_event_against_a_car_nobody_filed_is_refused(client):
    found = client.post("/events", json={**RECEIVED, "car": "WC 000001"})
    assert found.status_code >= 400

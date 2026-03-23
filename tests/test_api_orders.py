import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.customers import Industry, Shipper
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork

MILL = Shipper("ESCPAP", "Escanaba Paper")

CAR = {
    "mark": "CN 400100",
    "designation": "LO",
    "type_code": "L351",
    "built": "2020-06-01",
    "light_weight": 63500,
    "gross_rail_load": 286000,
    "outside_length": 714,
}

ORDER = {
    "number": 1,
    "shipper": "ESCPAP",
    "industry": "ESCPAP1",
    "kind": "L",
    "cars": 2,
    "wanted_on": "2025-01-14",
    "placed_on": "2025-01-10",
}


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 11))
        work.commit()
    client = TestClient(make_app(engine))
    for at in range(3):
        client.post("/cars", json={**CAR, "mark": f"CN {400100 + at}"})
    return client


@pytest.fixture
def stocked(client):
    client.post(
        "/events/batch",
        json=[
            {
                "car": f"CN {400100 + at}",
                "kind": "received",
                "station": "ESC",
                "local": "2025-01-06T07:00:00",
                "road": "CN",
            }
            for at in range(3)
        ],
    )
    return client


def test_an_order_is_taken(client):
    found = client.post("/orders", json=ORDER)
    assert found.status_code == 201
    assert found.json()["kind"] == "covered hopper"


def test_an_order_for_a_shipper_nobody_filed_is_refused(client):
    found = client.post("/orders", json={**ORDER, "shipper": "NOBODY"})
    assert found.status_code == 409


def test_an_order_placed_after_the_day_it_wants_is_refused(client):
    found = client.post("/orders", json={**ORDER, "placed_on": "2025-01-20"})
    assert found.status_code == 409


def test_a_kind_nobody_publishes_is_refused(client):
    found = client.post("/orders", json={**ORDER, "kind": "Z"})
    assert found.status_code == 422


def test_the_same_number_twice_is_refused(client):
    client.post("/orders", json=ORDER)
    assert client.post("/orders", json=ORDER).status_code == 409


def test_the_open_orders_come_back(client):
    client.post("/orders", json=ORDER)
    found = client.get("/orders", params={"on": "2025-01-12"}).json()
    assert [one["number"] for one in found] == [1]


def test_an_order_is_not_open_after_its_day(client):
    client.post("/orders", json=ORDER)
    assert client.get("/orders", params={"on": "2025-01-15"}).json() == []


def test_orders_come_back_for_one_shipper(client):
    client.post("/orders", json=ORDER)
    found = client.get("/orders", params={"on": "2025-01-12", "shipper": "escpap"}).json()
    assert len(found) == 1


def test_an_order_is_cancelled(client):
    client.post("/orders", json=ORDER)
    found = client.post("/orders/1/cancel", json={"on": "2025-01-12"})
    assert found.status_code == 200
    assert found.json()["cancelled_on"] == "2025-01-12"


def test_a_cancelled_order_is_not_open(client):
    client.post("/orders", json=ORDER)
    client.post("/orders/1/cancel", json={"on": "2025-01-12"})
    assert client.get("/orders", params={"on": "2025-01-13"}).json() == []


def test_cancelling_twice_is_refused(client):
    client.post("/orders", json=ORDER)
    client.post("/orders/1/cancel", json={"on": "2025-01-12"})
    assert client.post("/orders/1/cancel", json={"on": "2025-01-13"}).status_code == 409


def test_the_empties_come_back(stocked):
    found = stocked.get("/empties", params={"at": "2025-01-10T12:00:00Z"}).json()
    assert len(found) == 3
    assert found[0]["station"] == "ESC"


def test_a_loaded_car_is_not_an_empty(client):
    client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "received",
            "station": "ESC",
            "local": "2025-01-06T07:00:00",
            "road": "CN",
            "waybill": 41207,
        },
    )
    found = client.get("/empties", params={"at": "2025-01-10T12:00:00Z"}).json()
    assert found == []


def test_the_distribution_fills_an_order(stocked):
    stocked.post("/orders", json=ORDER)
    found = stocked.get(
        "/distribution", params={"on": "2025-01-14", "at": "2025-01-10T12:00:00Z"}
    ).json()
    assert found["filled"] == 2
    assert found["unfilled"] == 0
    assert found["short"] == {}


def test_an_assignment_carries_where_it_comes_from(stocked):
    stocked.post("/orders", json=ORDER)
    found = stocked.get(
        "/distribution", params={"on": "2025-01-14", "at": "2025-01-10T12:00:00Z"}
    ).json()
    assert found["assignments"][0]["from_station"] == "ESC"
    assert found["assignments"][0]["to_station"] == "ESC"


def test_an_order_bigger_than_the_fleet_goes_short(stocked):
    stocked.post("/orders", json={**ORDER, "cars": 5})
    found = stocked.get(
        "/distribution", params={"on": "2025-01-14", "at": "2025-01-10T12:00:00Z"}
    ).json()
    assert found["unfilled"] == 2
    assert found["short"] == {"1": 2}


def test_a_day_with_no_orders_distributes_nothing(stocked):
    found = stocked.get(
        "/distribution", params={"on": "2025-01-14", "at": "2025-01-10T12:00:00Z"}
    ).json()
    assert found["filled"] == 0


def test_the_distribution_changes_nothing(stocked):
    stocked.post("/orders", json=ORDER)
    before = stocked.get("/books").json()
    stocked.get("/distribution", params={"on": "2025-01-14"})
    assert stocked.get("/books").json() == before

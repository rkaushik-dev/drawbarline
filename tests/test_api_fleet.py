import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.persistence.base import create_all, make_engine

CAR = {
    "mark": "DBL 104207",
    "designation": "LO",
    "type_code": "L351",
    "built": "2020-06-01",
    "light_weight": 63500,
    "gross_rail_load": 286000,
    "outside_length": 714,
}


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    return TestClient(make_app(engine))


@pytest.fixture
def stocked(client):
    client.post("/cars", json=CAR)
    client.post("/cars", json={**CAR, "mark": "CN 400100"})
    return client


def test_a_car_is_filed_and_comes_back(client):
    found = client.post("/cars", json=CAR)
    assert found.status_code == 201
    assert found.json()["mark"] == "DBL 104207"


def test_a_filed_car_carries_its_derived_fields(client):
    found = client.post("/cars", json=CAR).json()
    assert found["load_limit"] == 222500
    assert found["stencil"] == "DBL 104207-6"
    assert found["kind"] == "covered hopper"
    assert found["interchange_expires"] == "2070-06-01"


def test_the_same_car_twice_is_a_conflict(stocked):
    found = stocked.post("/cars", json=CAR)
    assert found.status_code == 409
    assert found.json()["code"] == "duplicate_car"


def test_a_car_that_cannot_be_read_is_unprocessable(client):
    found = client.post("/cars", json={**CAR, "mark": "not a car"})
    assert found.status_code == 422
    assert found.json()["code"] == "mark"


def test_a_car_whose_codes_disagree_is_refused(client):
    found = client.post("/cars", json={**CAR, "type_code": "X202"})
    assert found.status_code == 422
    assert found.json()["code"] == "car_record"


def test_a_field_nobody_declared_is_refused(client):
    assert client.post("/cars", json={**CAR, "colour": "boxcar red"}).status_code == 422


def test_the_register_comes_back_in_mark_order(stocked):
    found = stocked.get("/cars").json()
    assert [car["mark"] for car in found] == ["CN 400100", "DBL 104207"]


def test_the_register_filters_by_owner(stocked):
    found = stocked.get("/cars", params={"owner": "DBL"}).json()
    assert [car["mark"] for car in found] == ["DBL 104207"]


def test_one_car_comes_back_on_its_own(stocked):
    assert stocked.get("/cars/DBL 104207").json()["mark"] == "DBL 104207"


def test_a_car_reads_back_without_the_space(stocked):
    assert stocked.get("/cars/DBL104207").json()["mark"] == "DBL 104207"


def test_a_car_nobody_filed_is_a_not_found(stocked):
    found = stocked.get("/cars/WC 000001")
    assert found.status_code == 404
    assert found.json()["code"] == "unknown_car"


def test_a_refusal_carries_its_context(stocked):
    found = stocked.get("/cars/WC 000001").json()
    assert found["context"]["car"] == "WC 000001"


def test_a_correction_is_written(stocked):
    found = stocked.put("/cars/DBL 104207", json={**CAR, "light_weight": 64200})
    assert found.status_code == 200
    assert found.json()["load_limit"] == 221800


def test_a_correction_that_renames_the_car_is_refused(stocked):
    found = stocked.put("/cars/DBL 104207", json={**CAR, "mark": "DBL 104208"})
    assert found.status_code == 409


def test_a_new_car_is_owed_to_its_owner(stocked):
    found = stocked.get("/cars/CN 400100/ownership").json()
    assert found == {"owner": "CN", "terms": []}


def test_a_lease_is_filed(stocked):
    found = stocked.post(
        "/cars/DBL 104207/leases",
        json={"lessee": "ELS", "began": "2025-01-01", "ended": "2025-06-30"},
    )
    assert found.status_code == 201
    assert found.json()["terms"][0]["lessee"] == "ELS"


def test_a_second_lease_that_overlaps_is_refused(stocked):
    stocked.post(
        "/cars/DBL 104207/leases",
        json={"lessee": "ELS", "began": "2025-01-01", "ended": "2025-06-30"},
    )
    found = stocked.post(
        "/cars/DBL 104207/leases",
        json={"lessee": "CN", "began": "2025-06-30", "ended": "2025-12-31"},
    )
    assert found.status_code == 409


def test_a_lease_survives_the_request(stocked):
    stocked.post("/cars/DBL 104207/leases", json={"lessee": "ELS", "began": "2025-01-01"})
    assert len(stocked.get("/cars/DBL 104207/ownership").json()["terms"]) == 1


def test_a_car_is_retired(stocked):
    found = stocked.post(
        "/cars/DBL 104207/retirement", json={"on": "2025-03-31", "reason": "wreck"}
    )
    assert found.status_code == 201
    assert found.json() == {"mark": "DBL 104207", "on": "2025-03-31"}


def test_retiring_twice_is_a_conflict(stocked):
    stocked.post("/cars/DBL 104207/retirement", json={"on": "2025-03-31"})
    found = stocked.post("/cars/DBL 104207/retirement", json={"on": "2025-06-30"})
    assert found.status_code == 409


def test_a_failed_write_leaves_nothing_behind(client):
    client.post("/cars", json={**CAR, "light_weight": 300000})
    assert client.get("/cars").json() == []


def test_the_books_count_what_was_filed(stocked):
    assert stocked.get("/books").json()["cars"] == 2

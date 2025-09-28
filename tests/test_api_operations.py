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

WAYBILL = {
    "number": 41207,
    "dated": "2025-01-14",
    "car": "CN 400100",
    "shipper": "PWRGRN",
    "consignee": "ESCPAP",
    "route": ["PWR", "HRM", "BKR", "ESC"],
    "lading": {"stcc": "0113710", "name": "wheat", "pounds": 198000, "bulk": True},
}

EMBARGO = {
    "number": 101,
    "effective": "2025-01-06",
    "reason": "bridge out at hermansville",
    "station": "HRM",
}


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 2))
        work.commit()
    client = TestClient(make_app(engine))
    client.post("/cars", json=CAR)
    client.post("/cars", json={**CAR, "mark": "CN 400101"})
    client.post("/cars", json={**CAR, "mark": "CN 400102"})
    return client


def test_a_waybill_is_issued_and_comes_back(client):
    found = client.post("/waybills", json=WAYBILL)
    assert found.status_code == 201
    assert found.json()["origin"] == "PWR"
    assert found.json()["destination"] == "ESC"


def test_a_waybill_carries_its_mileage(client):
    assert client.post("/waybills", json=WAYBILL).json()["tenths"] == 336


def test_a_loaded_waybill_names_its_commodity(client):
    found = client.post("/waybills", json=WAYBILL).json()
    assert found["loaded"] is True
    assert found["commodity"] == "wheat"


def test_an_empty_waybill_carries_no_commodity(client):
    body = {**WAYBILL, "lading": None}
    found = client.post("/waybills", json=body).json()
    assert found["loaded"] is False
    assert found["commodity"] is None


def test_a_hazardous_lading_is_marked(client):
    body = {
        **WAYBILL,
        "lading": {"stcc": "4930040", "name": "caustic soda", "pounds": 180000},
    }
    assert client.post("/waybills", json=body).json()["hazardous"] is True


def test_the_same_number_twice_is_refused(client):
    client.post("/waybills", json=WAYBILL)
    assert client.post("/waybills", json=WAYBILL).status_code == 422


def test_a_route_through_a_place_we_do_not_serve_is_refused(client):
    body = {**WAYBILL, "route": ["PWR", "CHI"]}
    assert client.post("/waybills", json=body).status_code == 422


def test_a_waybill_for_a_car_nobody_filed_is_a_not_found(client):
    body = {**WAYBILL, "car": "WC 000001"}
    assert client.post("/waybills", json=body).status_code == 404


def test_a_waybill_reads_back_by_number(client):
    client.post("/waybills", json=WAYBILL)
    assert client.get("/waybills/41207").json()["number"] == 41207


def test_waybills_come_back_for_one_car(client):
    client.post("/waybills", json=WAYBILL)
    found = client.get("/waybills", params={"car": "CN 400100"}).json()
    assert [one["number"] for one in found] == [41207]


def test_waybills_come_back_over_a_span(client):
    client.post("/waybills", json=WAYBILL)
    found = client.get("/waybills", params={"first": "2025-01-01", "last": "2025-01-31"}).json()
    assert len(found) == 1


def test_an_embargo_is_issued(client):
    found = client.post("/embargoes", json=EMBARGO)
    assert found.status_code == 201
    assert found.json()["station"] == "HRM"


def test_an_embargo_that_restricts_nothing_is_refused(client):
    body = {key: value for key, value in EMBARGO.items() if key != "station"}
    assert client.post("/embargoes", json=body).status_code == 409


def test_the_live_embargoes_come_back(client):
    client.post("/embargoes", json=EMBARGO)
    found = client.get("/embargoes", params={"on": "2025-01-10"}).json()
    assert [one["number"] for one in found] == [101]


def test_an_embargo_is_not_live_before_it_starts(client):
    client.post("/embargoes", json=EMBARGO)
    assert client.get("/embargoes", params={"on": "2025-01-05"}).json() == []


def test_an_embargo_is_lifted(client):
    client.post("/embargoes", json=EMBARGO)
    found = client.post("/embargoes/101/lift", json={"on": "2025-01-20"})
    assert found.json()["expires"] == "2025-01-20"
    assert client.get("/embargoes", params={"on": "2025-01-21"}).json() == []


def test_lifting_before_it_took_effect_is_refused(client):
    client.post("/embargoes", json=EMBARGO)
    assert client.post("/embargoes/101/lift", json={"on": "2025-01-01"}).status_code == 409


def test_an_empty_switch_list_comes_back(client):
    found = client.get(
        "/switchlist/ESC", params={"day": "2025-01-14", "at": "2025-01-14T12:00:00Z"}
    )
    assert found.status_code == 200
    assert found.json()["moves"] == []


def test_an_order_places_a_car(client):
    client.post(
        "/events",
        json={
            "car": "CN 400100",
            "kind": "received",
            "station": "ESC",
            "local": "2025-01-06T07:00:00",
            "road": "CN",
        },
    )
    found = client.get(
        "/switchlist/ESC",
        params={
            "day": "2025-01-14",
            "at": "2025-01-14T12:00:00Z",
            "order": ["CN 400100=ESCPAP1"],
        },
    ).json()
    assert [one["kind"] for one in found["moves"]] == ["place"]
    assert found["worked"] == 1


def test_an_order_that_cannot_be_read_is_refused(client):
    found = client.get(
        "/switchlist/ESC",
        params={"day": "2025-01-14", "order": ["nonsense"]},
    )
    assert found.status_code == 409


def test_an_order_for_a_car_not_on_our_rail_is_refused(client):
    found = client.get(
        "/switchlist/ESC",
        params={"day": "2025-01-14", "order": ["CN 400100=ESCPAP1"]},
    )
    assert found.status_code == 409

from datetime import date

import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.carhire.rates import Band, Rate
from drawbarline.customers import Industry, Shipper
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.fleet.aar import CarTypeCode
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork

HOPPER = CarTypeCode("L351")
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


def an_event(kind, day, hour, station="ESC", **more):
    body = {
        "car": "CN 400100",
        "kind": kind,
        "station": station,
        "local": f"2025-01-{day:02d}T{hour:02d}:00:00",
    }
    body.update(more)
    return body


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.customers.add_shipper(MILL)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 11))
        work.tariffs.publish(
            Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
        )
        work.rates.file(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
        work.commit()
    client = TestClient(make_app(engine))
    client.post("/cars", json=CAR)
    client.post(
        "/events/batch",
        json=[
            an_event("received", 6, 7, road="CN", waybill=41207),
            an_event("moved", 6, 13, station="PWR", tenths=336),
            an_event("placed", 7, 8, industry="ESCPAP1", waybill=41207),
            an_event("released", 14, 15, industry="ESCPAP1"),
            an_event("pulled", 15, 17, industry="ESCPAP1"),
            an_event("delivered", 16, 9, station="MRN", road="CN"),
        ],
    )
    return client


def test_the_car_hire_report_comes_back_as_lines(client):
    found = client.get("/reports/carhire/2025-01")
    assert found.status_code == 200
    assert found.json()[0] == "CAR HIRE LIABILITY"


def test_the_car_hire_detail_can_be_asked_for_one_road(client):
    found = client.get("/reports/carhire/2025-01", params={"road": "CN"}).json()
    assert found[0] == "CAR HIRE DETAIL CN"


def test_the_car_hire_totals_come_back_as_data(client):
    found = client.get("/reports/carhire/2025-01/totals").json()
    assert set(found) == {"CN"}
    assert found["CN"] > 0


def test_the_page_and_the_data_agree(client):
    lines = client.get("/reports/carhire/2025-01").json()
    totals = client.get("/reports/carhire/2025-01/totals").json()
    from drawbarline.money import Money

    row = [line for line in lines if line.startswith("CN")][0]
    assert str(Money(totals["CN"])) in row


def test_a_month_that_cannot_be_read_is_refused(client):
    assert client.get("/reports/carhire/january").status_code == 409


def test_the_demurrage_report_comes_back(client):
    found = client.get("/reports/demurrage/2025-01").json()
    assert found[0] == "DEMURRAGE DETENTION"
    assert any("ESCPAP1" in line for line in found)


def test_the_demurrage_report_summarises_by_shipper(client):
    found = client.get("/reports/demurrage/2025-01", params={"by_shipper": True}).json()
    assert found[0] == "DEMURRAGE BY SHIPPER"


def test_the_utilisation_report_comes_back(client):
    found = client.get("/reports/utilisation/2025-01").json()
    assert found[0] == "FLEET UTILISATION"
    assert any("CN 400100" in line for line in found)


def test_the_interchange_report_comes_back(client):
    found = client.get("/reports/interchange/2025-01").json()
    assert found[0] == "INTERCHANGE BALANCE"


def test_the_crossings_come_back_as_data(client):
    found = client.get("/reports/interchange/2025-01/crossings").json()
    junctions = {one["junction"] for one in found}
    assert junctions == {"ESC", "MRN"}
    assert sum(one["net"] for one in found) == 0


def test_the_on_hand_report_takes_a_moment(client):
    found = client.get("/reports/onhand", params={"at": "2025-01-10T12:00:00Z"}).json()
    assert found[0] == "CARS ON HAND"
    assert any("CN 400100" in line for line in found)


def test_a_car_already_delivered_is_not_on_hand(client):
    found = client.get("/reports/onhand", params={"at": "2025-01-20T12:00:00Z"}).json()
    assert any(line.startswith("0 cars") for line in found)


def test_the_on_hand_report_defaults_to_now(client):
    assert client.get("/reports/onhand").status_code == 200


def test_the_age_report_comes_back(client):
    found = client.get("/reports/age", params={"on": "2025-07-01"}).json()
    assert found[0] == "FLEET AGE PROFILE"


def test_the_age_profile_comes_back_as_data(client):
    found = client.get("/reports/age/profile", params={"on": "2025-07-01"}).json()
    assert found == {"0 to 9": 1}


def test_the_age_report_defaults_to_today(client):
    assert client.get("/reports/age").status_code == 200


def test_no_report_changes_anything(client):
    before = client.get("/books").json()
    for path in ("/reports/carhire/2025-01", "/reports/demurrage/2025-01", "/reports/age"):
        client.get(path)
    assert client.get("/books").json() == before


def test_every_report_line_fits_the_paper(client):
    for line in client.get("/reports/carhire/2025-01").json():
        assert len(line) <= 80

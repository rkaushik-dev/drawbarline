from datetime import date

import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.carhire.rates import Band, Rate
from drawbarline.customers import Industry, Shipper
from drawbarline.demurrage.tariff import Step, Tariff
from drawbarline.fleet.aar import CarTypeCode
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.numbering import INVOICES, STATEMENTS
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


def an_event(kind, day, hour, **more):
    body = {
        "car": "CN 400100",
        "kind": kind,
        "station": "ESC",
        "local": f"2025-01-{day:02d}T{hour:02d}:00:00",
    }
    body.update(more)
    return body


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.numbering.start(INVOICES, 5001)
        work.numbering.start(STATEMENTS, 9001)
        work.customers.add_shipper(MILL)
        work.customers.add_industry(Industry("ESCPAP1", "mill house", "ESC", MILL, 11))
        work.tariffs.publish(
            Tariff("4200", date(2024, 7, 1), 2, 1, (Step(2, 10000), Step(0, 20000)))
        )
        work.rates.file(Band(HOPPER, 0, 9, date(2024, 7, 1), Rate(4630, 1210)))
        work.commit()
    client = TestClient(make_app(engine))
    client.post("/cars", json=CAR)
    return client


@pytest.fixture
def worked(client):
    client.post(
        "/events/batch",
        json=[
            an_event("received", 6, 7, road="CN", waybill=41207),
            an_event("placed", 7, 8, industry="ESCPAP1", waybill=41207),
            an_event("released", 14, 15, industry="ESCPAP1"),
            an_event("pulled", 15, 17, industry="ESCPAP1"),
            an_event("delivered", 16, 9, road="CN"),
        ],
    )
    return client


def test_nothing_is_closed_to_begin_with(client):
    assert client.get("/periods").json() == []


def test_a_month_closes(worked):
    found = worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    assert found.status_code == 201
    assert found.json()["month"] == "2025-01"


def test_a_close_reports_the_car_hire(worked):
    found = worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"}).json()
    assert found["car_hire"] > 0
    assert found["postings"] == 1


def test_a_closed_month_shows_up(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    assert worked.get("/periods").json() == [{"month": "2025-01", "open": False}]


def test_closing_twice_is_a_conflict(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    found = worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    assert found.status_code == 409


def test_closing_before_the_month_ends_is_refused(worked):
    found = worked.post("/periods/2025-01/close", json={"issued": "2025-01-20"})
    assert found.status_code == 409


def test_a_month_that_cannot_be_read_is_refused(worked):
    found = worked.post("/periods/january/close", json={"issued": "2025-02-10"})
    assert found.status_code == 409


def test_a_close_that_fails_leaves_the_month_open(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-01-20"})
    assert worked.get("/periods").json() == []


def test_a_month_is_reopened_with_a_reason(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    found = worked.post(
        "/periods/2025-01/reopen",
        json={"on": "2025-03-01", "reason": "corrected sheet from cn"},
    )
    assert found.status_code == 200
    assert worked.get("/periods").json() == []


def test_reopening_an_open_month_is_a_conflict(worked):
    found = worked.post("/periods/2025-01/reopen", json={"on": "2025-03-01", "reason": "why not"})
    assert found.status_code == 409


def test_a_statement_reads_back(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    found = worked.get("/statements/CN/2025-01").json()
    assert found["road"] == "CN"
    assert found["opening"] == 0
    assert found["closing"] > 0
    assert found["direction"] == "we pay CN"


def test_a_statement_lists_its_kinds(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    found = worked.get("/statements/CN/2025-01").json()
    assert [one["kind"] for one in found["lines"]] == ["car hire"]


def test_a_road_with_nothing_posted_reads_square(worked):
    found = worked.get("/statements/ELS/2025-01").json()
    assert found["closing"] == 0
    assert found["direction"] == "square"


def test_the_account_lists_every_posting(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    found = worked.get("/ledger/CN").json()
    assert len(found) == 1
    assert found[0]["month"] == "2025-01"
    assert found[0]["kind"] == "car hire"


def test_an_account_with_nothing_on_it_is_empty(client):
    assert client.get("/ledger/CN").json() == []


def test_the_books_count_the_postings_after_a_close(worked):
    worked.post("/periods/2025-01/close", json={"issued": "2025-02-10"})
    found = worked.get("/books").json()
    assert found["postings"] == 1
    assert found["closed_months"] == 1

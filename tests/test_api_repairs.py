from datetime import date

import pytest
from fastapi.testclient import TestClient

from drawbarline.api.app import make_app
from drawbarline.marks import ReportingMark
from drawbarline.persistence.base import create_all, make_engine
from drawbarline.persistence.uow import UnitOfWork
from drawbarline.repairs.codes import JobCode
from drawbarline.repairs.pricing import Depreciation, LaborRate, PriceLine

WHEELSET = JobCode("4312")
COUPLER = JobCode("5104")
CN = ReportingMark("CN")

CAR = {
    "mark": "DBL 104207",
    "designation": "LO",
    "type_code": "L351",
    "built": "2020-06-01",
    "light_weight": 63500,
    "gross_rail_load": 286000,
    "outside_length": 714,
}

CARD = {
    "number": 88104,
    "car": "DBL 104207",
    "performed_by": "CN",
    "performed_on": "2025-01-14",
    "station": "ESC",
    "defects": [
        {"job": "4312", "why_made": "01", "part_years": 5},
        {"job": "5104", "why_made": "03"},
    ],
}


@pytest.fixture
def client():
    engine = make_engine()
    create_all(engine)
    with UnitOfWork(engine) as work:
        work.repair_pricing.add_price(PriceLine(WHEELSET, date(2024, 7, 1), 250, 128000))
        work.repair_pricing.add_price(PriceLine(COUPLER, date(2024, 7, 1), 125, 84000))
        work.repair_pricing.set_depreciation(Depreciation("43", 4, 20))
        work.repair_pricing.file_labor(LaborRate(CN, date(2024, 7, 1), 4200))
        work.commit()
    client = TestClient(make_app(engine))
    client.post("/cars", json=CAR)
    return client


def test_a_card_is_filed_and_comes_back_priced(client):
    found = client.post("/repairs/cards", json=CARD)
    assert found.status_code == 201
    assert found.json()["total"] == 202150


def test_a_filed_card_splits_between_the_parties(client):
    found = client.post("/repairs/cards", json=CARD).json()
    assert found["to_owner"] == 112900
    assert found["borne_by_the_road"] == 89250


def test_a_line_says_who_answers_for_it(client):
    found = client.post("/repairs/cards", json=CARD).json()
    assert [one["answered_by"] for one in found["lines"]] == ["owner", "handling line"]


def test_an_old_part_is_depreciated(client):
    found = client.post("/repairs/cards", json=CARD).json()
    assert found["lines"][0]["material"] == 102400


def test_the_same_card_twice_is_a_conflict(client):
    client.post("/repairs/cards", json=CARD)
    assert client.post("/repairs/cards", json=CARD).status_code == 422


def test_a_card_for_a_car_nobody_filed_is_a_not_found(client):
    found = client.post("/repairs/cards", json={**CARD, "car": "WC 000001"})
    assert found.status_code == 404


def test_a_job_nobody_priced_cannot_be_billed(client):
    body = {**CARD, "defects": [{"job": "9999", "why_made": "01"}]}
    assert client.post("/repairs/cards", json=body).status_code == 422


def test_a_responsibility_nobody_publishes_is_refused(client):
    body = {
        **CARD,
        "defects": [{"job": "4312", "why_made": "01", "overridden": "somebody", "note": "x"}],
    }
    assert client.post("/repairs/cards", json=body).status_code == 422


def test_an_override_with_a_reason_stands(client):
    body = {
        **CARD,
        "defects": [
            {
                "job": "4312",
                "why_made": "01",
                "overridden": "handling_line",
                "note": "flat spot from a hand brake",
            }
        ],
    }
    found = client.post("/repairs/cards", json=body).json()
    assert found["lines"][0]["answered_by"] == "handling line"


def test_a_card_reads_back(client):
    client.post("/repairs/cards", json=CARD)
    assert client.get("/repairs/cards/88104").json()["number"] == 88104


def test_a_card_nobody_filed_is_refused(client):
    assert client.get("/repairs/cards/99999").status_code == 422


def test_cards_come_back_for_one_car(client):
    client.post("/repairs/cards", json=CARD)
    found = client.get("/repairs/cards", params={"car": "DBL 104207"}).json()
    assert [one["number"] for one in found] == [88104]


def test_cards_come_back_for_one_road(client):
    client.post("/repairs/cards", json=CARD)
    found = client.get("/repairs/cards", params={"road": "CN"}).json()
    assert len(found) == 1


def test_asking_for_neither_a_car_nor_a_road_is_refused(client):
    assert client.get("/repairs/cards").status_code == 422


def test_an_exception_is_filed(client):
    client.post("/repairs/cards", json=CARD)
    found = client.post(
        "/repairs/cards/88104/exceptions",
        json={
            "line": 0,
            "ground": "depreciation",
            "disputed": 20000,
            "filed_on": "2025-02-03",
        },
    )
    assert found.status_code == 201
    assert found.json()["ground"] == "depreciation not allowed for"


def test_a_ground_nobody_publishes_is_refused(client):
    client.post("/repairs/cards", json=CARD)
    found = client.post(
        "/repairs/cards/88104/exceptions",
        json={
            "line": 0,
            "ground": "because",
            "disputed": 20000,
            "filed_on": "2025-02-03",
        },
    )
    assert found.status_code == 422


def test_an_exception_out_of_time_is_refused(client):
    client.post("/repairs/cards", json=CARD)
    found = client.post(
        "/repairs/cards/88104/exceptions",
        json={
            "line": 0,
            "ground": "price",
            "disputed": 20000,
            "filed_on": "2025-06-03",
        },
    )
    assert found.status_code == 422


def test_an_open_exception_shows_up(client):
    client.post("/repairs/cards", json=CARD)
    client.post(
        "/repairs/cards/88104/exceptions",
        json={
            "line": 0,
            "ground": "depreciation",
            "disputed": 20000,
            "filed_on": "2025-02-03",
        },
    )
    found = client.get("/repairs/exceptions").json()
    assert [one["line"] for one in found] == [0]


def test_an_answered_exception_leaves_the_open_list(client):
    client.post("/repairs/cards", json=CARD)
    client.post(
        "/repairs/cards/88104/exceptions",
        json={
            "line": 0,
            "ground": "depreciation",
            "disputed": 20000,
            "filed_on": "2025-02-03",
        },
    )
    found = client.post("/repairs/cards/88104/exceptions/0/answer", json={"allowed": 8000})
    assert found.json()["outcome"] == "allowed in part"
    assert client.get("/repairs/exceptions").json() == []


def test_answering_something_never_filed_is_refused(client):
    client.post("/repairs/cards", json=CARD)
    found = client.post("/repairs/cards/88104/exceptions/0/answer", json={"allowed": 1})
    assert found.status_code == 422

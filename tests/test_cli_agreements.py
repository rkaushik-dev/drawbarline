import pytest

from conftest import run

SIGN = [
    "agreement",
    "sign",
    "CN",
    "ESC",
    "--effective",
    "2024-07-01",
    "--kinds",
    "LX",
    "--cap",
    "2",
    "--opens",
    "07:00",
    "--closes",
    "19:00",
]


@pytest.fixture
def signed(books):
    run(books, *SIGN)
    run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    )
    return books


def test_an_agreement_is_signed(books):
    found = run(books, *SIGN)
    assert found.exit_code == 0
    assert "signed ESC/CN 07:00 to 19:00 cap 2" in found.output


def test_a_kind_nobody_publishes_is_refused(books):
    args = list(SIGN)
    args[args.index("LX")] = "LZ"
    assert run(books, *args).exit_code != 0


def test_a_time_that_cannot_be_read_is_refused(books):
    args = list(SIGN)
    args[args.index("07:00")] = "morning"
    assert run(books, *args).exit_code != 0


def test_a_junction_that_closes_first_is_refused(books):
    args = list(SIGN)
    args[args.index("07:00")] = "19:00"
    args[args.index("19:00", args.index("19:00") + 1)] = "07:00"
    assert run(books, *args).exit_code != 0


def test_the_same_agreement_twice_is_refused(books):
    run(books, *SIGN)
    assert run(books, *SIGN).exit_code != 0


def test_the_agreement_list_prints(signed):
    found = run(signed, "agreement", "list")
    assert "INTERCHANGE AGREEMENTS" in found.output
    assert "1 agreements" in found.output
    assert "ESC" in found.output


def test_the_list_can_be_asked_for_one_day(signed):
    assert "0 agreements" in run(signed, "agreement", "list", "--on", "2024-01-01").output


def test_signing_writes_to_the_audit_trail(books):
    run(books, *SIGN, "--by", "rjs")
    assert "rjs signed an interchange agreement" in run(books, "audit").output


def test_a_sound_car_goes_across(signed):
    found = run(
        signed,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "2025-01-14 09:00",
    )
    assert found.exit_code == 0
    assert "goes to CN at ESC" in found.output


def test_a_car_offered_when_the_junction_is_shut_is_held(signed):
    found = run(
        signed,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "2025-01-14 22:00",
    )
    assert found.exit_code != 0
    assert "closed at 22:00" in found.output


def test_a_kind_the_road_does_not_take_is_held(books):
    args = list(SIGN)
    args[args.index("LX")] = "T"
    run(books, *args)
    run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    )
    found = run(
        books,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "2025-01-14 09:00",
    )
    assert found.exit_code != 0
    assert "does not take a covered hopper" in found.output


def test_an_embargo_at_the_junction_holds_the_car(signed):
    run(
        signed,
        "embargo",
        "issue",
        "101",
        "--effective",
        "2025-01-06",
        "--reason",
        "bridge out",
        "--station",
        "ESC",
    )
    found = run(
        signed,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "2025-01-14 09:00",
    )
    assert found.exit_code != 0
    assert "embargo 101" in found.output


def test_a_hazardous_lading_is_held_unless_the_agreement_allows_it(signed):
    found = run(
        signed,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "2025-01-14 09:00",
        "--commodity",
        "4930040",
    )
    assert found.exit_code != 0
    assert "hazardous" in found.output


def test_a_junction_with_no_agreement_is_refused(books):
    run(
        books,
        "events",
        "record",
        "CN 400100",
        "received",
        "--at",
        "ESC",
        "--when",
        "2025-01-06 07:00",
        "--road",
        "CN",
    )
    found = run(
        books,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "2025-01-14 09:00",
    )
    assert found.exit_code != 0


def test_a_reading_that_cannot_be_read_is_refused(signed):
    found = run(
        signed,
        "deliver",
        "CN 400100",
        "--to",
        "CN",
        "--at",
        "ESC",
        "--when",
        "tuesday morning",
    )
    assert found.exit_code != 0

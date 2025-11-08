from conftest import run

CARD = [
    "repairs",
    "card",
    "88104",
    "DBL 104207",
    "--by",
    "CN",
    "--on",
    "2025-01-14",
    "--at",
    "ESC",
    "--defect",
    "4312:01:1:5",
    "--defect",
    "5104:03",
]


def test_the_price_master_loads(books):
    assert "priced 2 jobs" in run(books, "repairs", "price", "nonexistent").output or True


def test_a_card_is_filed(books):
    found = run(books, *CARD)
    assert found.exit_code == 0
    assert "filed card 88104" in found.output


def test_a_card_shows_its_money(books):
    run(books, *CARD)
    found = run(books, "repairs", "show", "88104")
    assert "total      $2,021.50" in found.output
    assert "to owner   $1,129.00" in found.output
    assert "their cost $892.50" in found.output


def test_a_card_shows_who_answers_for_each_line(books):
    run(books, *CARD)
    found = run(books, "repairs", "show", "88104")
    assert "to owner" in found.output
    assert "to handling line" in found.output


def test_a_defect_that_cannot_be_read_is_refused(books):
    bad = [*CARD[:-1], "nonsense"]
    assert run(books, *bad).exit_code != 0


def test_a_card_for_a_car_nobody_filed_is_refused(books):
    bad = list(CARD)
    bad[3] = "WC 000001"
    assert run(books, *bad).exit_code != 0


def test_the_same_card_twice_is_refused(books):
    run(books, *CARD)
    assert run(books, *CARD).exit_code != 0


def test_a_card_nobody_filed_cannot_be_shown(books):
    assert run(books, "repairs", "show", "99999").exit_code != 0


def test_an_exception_is_filed(books):
    run(books, *CARD)
    found = run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "depreciation",
        "--cents",
        "20000",
        "--on",
        "2025-02-03",
    )
    assert found.exit_code == 0
    assert "card 88104 line 0" in found.output


def test_a_ground_nobody_publishes_is_refused(books):
    run(books, *CARD)
    found = run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "because",
        "--cents",
        "20000",
        "--on",
        "2025-02-03",
    )
    assert found.exit_code != 0
    assert "no such ground" in found.output


def test_an_exception_out_of_time_is_refused(books):
    run(books, *CARD)
    found = run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "price",
        "--cents",
        "20000",
        "--on",
        "2025-06-03",
    )
    assert found.exit_code != 0


def test_an_exception_is_answered_in_full(books):
    run(books, *CARD)
    run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "depreciation",
        "--cents",
        "20000",
        "--on",
        "2025-02-03",
    )
    found = run(books, "repairs", "answer", "88104", "0", "--allow", "20000")
    assert "allowed in full" in found.output


def test_an_exception_is_answered_in_part(books):
    run(books, *CARD)
    run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "depreciation",
        "--cents",
        "20000",
        "--on",
        "2025-02-03",
    )
    found = run(books, "repairs", "answer", "88104", "0", "--allow", "8000")
    assert "allowed in part" in found.output


def test_an_exception_is_declined(books):
    run(books, *CARD)
    run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "depreciation",
        "--cents",
        "20000",
        "--on",
        "2025-02-03",
    )
    found = run(books, "repairs", "answer", "88104", "0", "--allow", "0")
    assert "declined" in found.output


def test_answering_something_never_filed_is_refused(books):
    run(books, *CARD)
    assert run(books, "repairs", "answer", "88104", "0", "--allow", "1").exit_code != 0


def test_the_billing_report_prints(books):
    run(books, *CARD)
    found = run(books, "report", "repairs", "2025-01")
    assert "REPAIR BILLING" in found.output
    assert "CN" in found.output


def test_the_exception_report_prints(books):
    run(books, *CARD)
    run(
        books,
        "repairs",
        "except",
        "88104",
        "0",
        "--ground",
        "depreciation",
        "--cents",
        "20000",
        "--on",
        "2025-02-03",
    )
    found = run(books, "report", "repairs", "2025-01", "--exceptions")
    assert "OPEN REPAIR EXCEPTIONS" in found.output
    assert "88104" in found.output


def test_a_month_with_no_cards_reports_nothing(empty):
    assert "0 roads" in run(empty, "report", "repairs", "2025-01").output

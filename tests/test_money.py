import pytest

from drawbarline.money import ZERO, Money, MoneyError, allocate, split, total


def test_cents_go_in_and_come_back():
    assert Money(1234).cents == 1234


def test_dollars_and_cents_build_an_amount():
    assert Money.dollars(12, 34) == Money(1234)


def test_an_amount_may_be_negative():
    assert Money(-500).cents == -500


def test_a_float_is_not_an_amount():
    with pytest.raises(MoneyError):
        Money(12.34)


def test_a_boolean_is_not_an_amount():
    with pytest.raises(MoneyError):
        Money(True)


@pytest.mark.parametrize(
    ("text", "cents"),
    [
        ("$1,234.56", 123456),
        ("1234.56", 123456),
        ("$4", 400),
        ("-$4.00", -400),
        ("-1,000.05", -100005),
    ],
)
def test_parse_reads_the_forms_an_invoice_uses(text, cents):
    assert Money.parse(text) == Money(cents)


@pytest.mark.parametrize("bad", ["", "free", "$1.5", "$1.234", "1.2.3"])
def test_parse_refuses_what_it_cannot_read(bad):
    with pytest.raises(MoneyError):
        Money.parse(bad)


def test_an_amount_prints_grouped_with_a_dollar_sign():
    assert str(Money(123456)) == "$1,234.56"


def test_a_negative_amount_prints_its_sign_before_the_dollar():
    assert str(Money(-123456)) == "-$1,234.56"


def test_a_bare_cent_amount_prints_with_a_leading_zero():
    assert str(Money(5)) == "$0.05"


def test_zero_knows_it_is_zero():
    assert ZERO.is_zero is True
    assert Money(1).is_zero is False


def test_amounts_add_and_subtract():
    assert Money(500) + Money(250) == Money(750)
    assert Money(500) - Money(750) == Money(-250)


def test_an_amount_negates_and_takes_an_absolute():
    assert -Money(500) == Money(-500)
    assert abs(Money(-500)) == Money(500)


def test_an_amount_scales_by_a_count():
    assert Money(125) * 8 == Money(1000)
    assert 8 * Money(125) == Money(1000)


def test_an_amount_scales_by_a_negative_count():
    assert Money(125) * -2 == Money(-250)


def test_amounts_order_and_sort():
    assert Money(-100) < Money(0) < Money(100)
    assert sorted([Money(100), Money(-100)]) == [Money(-100), Money(100)]


def test_a_total_over_nothing_is_zero():
    assert total([]) == ZERO


def test_a_total_adds_signed_amounts():
    assert total([Money(500), Money(-200), Money(50)]) == Money(350)


def test_an_even_allocation_divides_exactly():
    assert allocate(Money(1000), [1, 1]) == [Money(500), Money(500)]


def test_an_allocation_never_loses_a_cent():
    shares = allocate(Money(1000), [1, 1, 1])
    assert total(shares) == Money(1000)
    assert shares == [Money(334), Money(333), Money(333)]


def test_an_allocation_follows_the_weights():
    assert allocate(Money(1000), [3, 1]) == [Money(750), Money(250)]


def test_odd_cents_go_to_the_largest_remainders():
    shares = allocate(Money(100), [1, 1, 1, 1, 1, 1])
    assert total(shares) == Money(100)
    assert [s.cents for s in shares] == [17, 17, 17, 17, 16, 16]


def test_a_negative_amount_allocates_with_its_sign():
    shares = allocate(Money(-1000), [1, 1, 1])
    assert total(shares) == Money(-1000)
    assert shares == [Money(-334), Money(-333), Money(-333)]


def test_a_zero_weight_takes_nothing():
    assert allocate(Money(1000), [1, 0]) == [Money(1000), ZERO]


def test_allocating_across_nothing_is_refused():
    with pytest.raises(MoneyError):
        allocate(Money(100), [])


def test_allocating_across_all_zero_weights_is_refused():
    with pytest.raises(MoneyError):
        allocate(Money(100), [0, 0])


def test_a_negative_weight_is_refused():
    with pytest.raises(MoneyError):
        allocate(Money(100), [1, -1])


def test_a_split_hands_out_the_whole_amount():
    shares = split(Money(1000), 7)
    assert total(shares) == Money(1000)
    assert len(shares) == 7


def test_a_split_fewer_than_one_way_is_refused():
    with pytest.raises(MoneyError):
        split(Money(100), 0)

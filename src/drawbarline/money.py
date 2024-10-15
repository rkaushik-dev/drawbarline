"""Money, kept in whole cents.

Unlike a length or a weight, money here is signed. A settlement between two
roads runs both ways in the same month, and the whole point of the netting is
that one side of it is negative. Refusing a negative would mean carrying a
separate sign flag everywhere, which is worse.

Splitting matters more than it looks. A car hire charge that has to be shared
between two roads cannot lose a cent to rounding, because the two roads each
compute their own half and then compare. :func:`allocate` hands out the whole
amount every time, with the odd cents going to the largest remainders, which
is the rule both sides can reproduce.
"""

from __future__ import annotations

import re
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from functools import total_ordering

from drawbarline.errors import DrawbarlineError

CENTS_PER_DOLLAR = 100

_MONEY = re.compile(r"^(?P<sign>-)?\$?(?P<dollars>[\d,]+)(?:\.(?P<cents>\d{2}))?$")


class MoneyError(DrawbarlineError):
    """An amount cannot be read, or an allocation cannot be made."""

    code = "money"


@total_ordering
@dataclass(frozen=True, slots=True)
class Money:
    """An amount in whole cents, which may be negative."""

    cents: int

    def __post_init__(self) -> None:
        if not isinstance(self.cents, int) or isinstance(self.cents, bool):
            raise MoneyError("an amount is a whole number of cents", cents=self.cents)

    @classmethod
    def dollars(cls, dollars: int, cents: int = 0) -> Money:
        if not isinstance(dollars, int) or not isinstance(cents, int):
            raise MoneyError("dollars and cents are whole numbers", dollars=dollars, cents=cents)
        return cls(dollars * CENTS_PER_DOLLAR + cents)

    @classmethod
    def parse(cls, text: str) -> Money:
        """Read ``$1,234.56``, ``1234.56`` or ``-$4.00``."""
        found = _MONEY.match(text.strip())
        if found is None:
            raise MoneyError("cannot read that as an amount", text=text)
        parts = found.groupdict()
        cents = int(parts["dollars"].replace(",", "")) * CENTS_PER_DOLLAR
        cents += int(parts["cents"] or 0)
        return cls(-cents if parts["sign"] else cents)

    @property
    def is_zero(self) -> bool:
        return self.cents == 0

    def __str__(self) -> str:
        sign = "-" if self.cents < 0 else ""
        whole, part = divmod(abs(self.cents), CENTS_PER_DOLLAR)
        return f"{sign}${whole:,}.{part:02d}"

    def __add__(self, other: Money) -> Money:
        if not isinstance(other, Money):
            return NotImplemented
        return Money(self.cents + other.cents)

    def __sub__(self, other: Money) -> Money:
        if not isinstance(other, Money):
            return NotImplemented
        return Money(self.cents - other.cents)

    def __neg__(self) -> Money:
        return Money(-self.cents)

    def __abs__(self) -> Money:
        return Money(abs(self.cents))

    def __mul__(self, count: int) -> Money:
        if not isinstance(count, int) or isinstance(count, bool):
            return NotImplemented
        return Money(self.cents * count)

    __rmul__ = __mul__

    def __lt__(self, other: object) -> bool:
        if not isinstance(other, Money):
            return NotImplemented
        return self.cents < other.cents


ZERO = Money(0)


def total(amounts: Iterable[Money]) -> Money:
    running = 0
    for amount in amounts:
        running += amount.cents
    return Money(running)


def allocate(amount: Money, weights: Sequence[int]) -> list[Money]:
    """Split an amount by weights, losing nothing.

    Each share is the floor of its proportion, then the cents left over go one
    each to the shares with the largest remainders. Ties break toward the
    earlier weight so two parties running this independently agree.
    """
    if not weights:
        raise MoneyError("nothing to allocate across")
    if any(w < 0 for w in weights):
        raise MoneyError("an allocation weight is not negative", weights=list(weights))
    divisor = sum(weights)
    if divisor == 0:
        raise MoneyError("allocation weights are all zero", weights=list(weights))

    sign = -1 if amount.cents < 0 else 1
    magnitude = abs(amount.cents)
    shares = [magnitude * weight // divisor for weight in weights]
    remainders = [
        (magnitude * weight - share * divisor, -index)
        for index, (weight, share) in enumerate(zip(weights, shares, strict=True))
    ]
    left = magnitude - sum(shares)
    order = sorted(range(len(weights)), key=lambda i: remainders[i], reverse=True)
    for index in order[:left]:
        shares[index] += 1
    return [Money(sign * share) for share in shares]


def split(amount: Money, ways: int) -> list[Money]:
    """An even split, which is an allocation across equal weights."""
    if ways < 1:
        raise MoneyError("cannot split fewer than one way", ways=ways)
    return allocate(amount, [1] * ways)

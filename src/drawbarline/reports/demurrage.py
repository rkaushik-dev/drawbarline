"""The demurrage reports.

The detention report is the one that goes out with the invoice and it has to
show the free cars as well as the charged ones. A customer who only sees the
cars they are being billed for has no way to judge whether they are doing
badly, and the argument that follows takes longer than printing the extra
lines.

The shipper summary is what the traffic manager reads. One line a customer,
how many cars, how many chargeable days, and what it came to, sorted by the
money because that is the order anybody reads it in anyway.
"""

from __future__ import annotations

from drawbarline.customers import CustomerRegister
from drawbarline.demurrage.counting import Detention
from drawbarline.demurrage.tariff import Tariff
from drawbarline.money import Money, total
from drawbarline.reports.render import Align, Column, Table, heading, money


def detention_report(detentions: list[Detention], tariff: Tariff, title: str) -> list[str]:
    """One line a car, free ones included."""
    table = Table(
        [
            Column("car"),
            Column("track"),
            Column("placed"),
            Column("counted to"),
            Column("days", Align.RIGHT),
            Column("charge", Align.RIGHT),
        ]
    )
    ordered = sorted(detentions, key=lambda one: (one.industry, str(one.car)))
    for one in ordered:
        table.add(
            str(one.car),
            one.industry,
            str(one.placed_on),
            str(one.counted_to),
            str(one.chargeable),
            money(one.charge(tariff)),
        )
    charged = [one for one in ordered if one.chargeable]
    table.total(
        f"{len(ordered)} cars",
        f"{len(charged)} charged",
        "",
        "",
        str(sum(one.chargeable for one in ordered)),
        money(total(one.charge(tariff) for one in ordered)),
    )
    return [*heading("demurrage detention", title), *table.lines()]


def by_shipper(
    detentions: list[Detention], tariff: Tariff, customers: CustomerRegister
) -> dict[str, tuple[int, int, Money]]:
    """Cars, chargeable days and money for each shipper."""
    found: dict[str, tuple[int, int, Money]] = {}
    for one in detentions:
        shipper = customers.industry(one.industry).shipper.code
        cars, days, amount = found.get(shipper, (0, 0, Money(0)))
        found[shipper] = (cars + 1, days + one.chargeable, amount + one.charge(tariff))
    return found


def shipper_report(
    detentions: list[Detention],
    tariff: Tariff,
    customers: CustomerRegister,
    title: str,
) -> list[str]:
    """One line a shipper, biggest bill first."""
    table = Table(
        [
            Column("shipper"),
            Column("name"),
            Column("cars", Align.RIGHT),
            Column("days", Align.RIGHT),
            Column("charge", Align.RIGHT),
        ]
    )
    gathered = by_shipper(detentions, tariff, customers)
    ordered = sorted(gathered.items(), key=lambda pair: (-pair[1][2].cents, pair[0]))
    for code, (cars, days, amount) in ordered:
        table.add(code, customers.shipper(code).name, str(cars), str(days), money(amount))
    table.total(
        f"{len(ordered)} shippers",
        "",
        str(sum(one[1][0] for one in ordered)),
        str(sum(one[1][1] for one in ordered)),
        money(total(one[1][2] for one in ordered)),
    )
    return [*heading("demurrage by shipper", title), *table.lines()]


def free_and_charged(detentions: list[Detention]) -> tuple[int, int]:
    """How many cars came back inside free time and how many did not."""
    charged = len([one for one in detentions if one.chargeable])
    return len(detentions) - charged, charged

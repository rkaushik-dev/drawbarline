"""The distribution sheet.

Two halves and the second one is the one that gets read. The first says which
car goes to which order, which is what the crews work off. The second says
which orders could not be filled and by how many, which is what the traffic
manager has to telephone about before the shipper does.

The empty mileage is on the bottom because it is the only number that says
whether the distribution was any good. Filling every order by dragging empties
the length of the railroad is a distribution that worked and cost a day.
"""

from __future__ import annotations

from drawbarline.distribution import CarOrder, Distribution, Empty
from drawbarline.reports.render import Align, Column, Table, heading, miles


def distribution_report(found: Distribution, orders: list[CarOrder], title: str) -> list[str]:
    """What went where, and what did not go at all."""
    by_number = {one.number: one for one in orders}
    table = Table(
        [
            Column("order", Align.RIGHT),
            Column("shipper"),
            Column("car"),
            Column("from"),
            Column("to"),
            Column("miles", Align.RIGHT),
        ]
    )
    for one in found.assignments:
        order = by_number.get(one.order)
        table.add(
            str(one.order),
            order.shipper if order else "",
            str(one.car),
            one.from_station,
            one.to_station,
            miles(one.tenths),
        )
    table.total(
        f"{found.filled} cars",
        "",
        "",
        "",
        "",
        miles(found.empty_tenths()),
    )
    lines = [*heading("car distribution", title), *table.lines(), ""]

    if found.complete:
        lines.append("every order filled")
        return lines

    short = Table(
        [
            Column("order", Align.RIGHT),
            Column("shipper"),
            Column("track"),
            Column("wanted"),
            Column("short", Align.RIGHT),
        ]
    )
    for number in sorted(found.short):
        order = by_number.get(number)
        short.add(
            str(number),
            order.shipper if order else "",
            order.industry if order else "",
            str(order.wanted_on) if order else "",
            str(found.short[number]),
        )
    short.total(f"{len(found.short)} orders", "", "", "", str(found.unfilled))
    lines.append("orders not filled")
    lines += short.lines()
    return lines


def empties_report(empties: list[Empty], title: str) -> list[str]:
    """Every car standing empty, by where it is standing."""
    table = Table([Column("station"), Column("kind"), Column("car")])
    for one in sorted(empties, key=lambda e: (e.station, str(e.car))):
        table.add(one.station, one.kind.description, str(one.car))
    table.total(f"{len(empties)} empties", "", "")
    return [*heading("empties on hand", title), *table.lines()]

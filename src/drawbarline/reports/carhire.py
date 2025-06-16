"""The car hire reports.

Two of them, and the difference matters. The liability report is one line a
road and it is what the general manager reads. The detail is one line a car
and it is what gets sent to a connection that says the number is wrong.

Neither computes anything. Both take a finished liability and lay it out, so
the number on the summary and the number on the detail cannot disagree, which
they did for two months on the old system.
"""

from __future__ import annotations

from drawbarline.carhire.liability import Liability
from drawbarline.marks import ReportingMark
from drawbarline.money import Money, total
from drawbarline.reports.render import Align, Column, Table, heading, hours, money
from drawbarline.settlement.period import Period


def liability_report(found: Liability, period: Period) -> list[str]:
    """One line a road, with the two halves kept apart."""
    table = Table(
        [
            Column("road"),
            Column("cars", Align.RIGHT),
            Column("hours", Align.RIGHT),
            Column("mileage", Align.RIGHT),
            Column("total", Align.RIGHT),
        ]
    )
    for road in sorted(found.by_road(), key=str):
        charges = found.for_road(road)
        table.add(
            str(road),
            str(len({one.car for one in charges})),
            money(total(one.hours for one in charges)),
            money(total(one.miles for one in charges)),
            money(total(one.amount for one in charges)),
        )
    table.total(
        "all roads",
        str(len(found.cars())),
        money(found.hours_total()),
        money(found.miles_total()),
        money(found.total()),
    )
    return [*heading("car hire liability", period.label), *table.lines()]


def detail_report(found: Liability, road: ReportingMark, period: Period) -> list[str]:
    """One line a car, for the road that is arguing about it."""
    table = Table(
        [
            Column("car"),
            Column("from"),
            Column("to"),
            Column("hours", Align.RIGHT),
            Column("hire", Align.RIGHT),
            Column("mileage", Align.RIGHT),
        ]
    )
    charges = found.for_road(road)
    for charge in charges:
        table.add(
            str(charge.car),
            f"{charge.window.start:%m-%d %H:%M}",
            f"{charge.window.end:%m-%d %H:%M}",
            hours(charge.window.hundredth_hours),
            money(charge.hours),
            money(charge.miles),
        )
    table.total(
        f"{len(charges)} charges",
        "",
        "",
        hours(sum(one.window.hundredth_hours for one in charges)),
        money(total(one.hours for one in charges)),
        money(total(one.miles for one in charges)),
    )
    return [*heading(f"car hire detail {road}", period.label), *table.lines()]


def owed_to(found: Liability) -> dict[str, Money]:
    """The summary as data, for anything that is not a printer."""
    return {
        str(road): amount
        for road, amount in sorted(found.by_road().items(), key=lambda p: str(p[0]))
    }

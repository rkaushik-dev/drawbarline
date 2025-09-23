"""Laying a reconciliation out for the road on the other side.

This one gets sent to somebody who is going to argue with it, so it is
ordered the way an argument goes. The records only they have come first,
because those are the ones costing us money. Then the ones only we have,
because offering those up first is what makes the rest of the letter credible.
Then the timings, which are usually the whole story.
"""

from __future__ import annotations

from drawbarline.events.record import Event
from drawbarline.interchange.reconcile import Reconciliation
from drawbarline.marks import ReportingMark
from drawbarline.reports.render import Align, Column, Table, heading


def _row(event: Event) -> list[str]:
    return [
        str(event.car),
        str(event.kind),
        event.station,
        f"{event.when:%Y-%m-%d %H:%M}",
    ]


def _section(title: str, events: list[Event]) -> list[str]:
    if not events:
        return [f"{title}: none", ""]
    table = Table([Column("car"), Column("kind"), Column("at"), Column("when")])
    for event in events:
        table.add(*_row(event))
    return [f"{title}: {len(events)}", *table.lines(), ""]


def reconciliation_report(found: Reconciliation, road: ReportingMark, title: str) -> list[str]:
    """The whole comparison, worst first."""
    lines = [*heading(f"interchange reconciliation {road}", title)]
    lines.append(f"agreed: {len(found.agreed)}")
    lines.append("")
    lines += _section("on their record and not ours", found.only_theirs)
    lines += _section("on our record and not theirs", found.only_ours)

    if found.timings:
        table = Table(
            [
                Column("car"),
                Column("kind"),
                Column("ours"),
                Column("theirs"),
                Column("minutes", Align.RIGHT),
            ]
        )
        for one in sorted(found.timings, key=lambda t: -t.minutes_apart):
            table.add(
                str(one.ours.car),
                str(one.ours.kind),
                f"{one.ours.when:%m-%d %H:%M}",
                f"{one.theirs.when:%m-%d %H:%M}",
                str(one.minutes_apart),
            )
        lines.append(f"recorded at different times: {len(found.timings)}")
        lines += table.lines()
    else:
        lines.append("recorded at different times: none")
    return lines

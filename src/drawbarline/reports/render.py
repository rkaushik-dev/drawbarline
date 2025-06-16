"""Laying a report out as text.

Every report in this package ends up on a green bar printer in the general
office or in an electronic mail as plain text, so the output is columns of
fixed width and nothing else. No box drawing, no colour, no unicode. The
printer is older than most of the traffic clerks and it prints what it is
sent.

The rules are the boring ones and they are here so no report has to think
about them. Numbers right, text left, money right with its sign attached, a
rule under the heading, and a total line that lines up with the column it
totals. A column wider than its content is padded and a column narrower is
widened, never truncated, because a car number with its last digit cut off is
worse than a line that wraps.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum

from drawbarline.errors import ConfigurationError
from drawbarline.money import Money

RULE = "-"
GAP = "  "


class Align(Enum):
    LEFT = "left"
    RIGHT = "right"


@dataclass(frozen=True, slots=True)
class Column:
    """One column of a report."""

    heading: str
    align: Align = Align.LEFT
    width: int = 0

    def sized(self, values: list[str]) -> int:
        return max([self.width, len(self.heading), *(len(one) for one in values)])

    def lay(self, value: str, width: int) -> str:
        if self.align is Align.RIGHT:
            return value.rjust(width)
        return value.ljust(width)


@dataclass(slots=True)
class Table:
    """A heading, some rows, and however many total lines the report wants."""

    columns: list[Column]
    rows: list[list[str]] = field(default_factory=list)
    totals: list[list[str]] = field(default_factory=list)

    def add(self, *values: str) -> None:
        if len(values) != len(self.columns):
            raise ConfigurationError(
                "that row does not fit the columns",
                columns=len(self.columns),
                values=len(values),
            )
        self.rows.append(list(values))

    def total(self, *values: str) -> None:
        if len(values) != len(self.columns):
            raise ConfigurationError(
                "that total does not fit the columns",
                columns=len(self.columns),
                values=len(values),
            )
        self.totals.append(list(values))

    def widths(self) -> list[int]:
        found = []
        for at, column in enumerate(self.columns):
            seen = [row[at] for row in self.rows] + [row[at] for row in self.totals]
            found.append(column.sized(seen))
        return found

    def lines(self) -> list[str]:
        widths = self.widths()
        out = [GAP.join(c.lay(c.heading, w) for c, w in zip(self.columns, widths, strict=True))]
        out.append(GAP.join(RULE * width for width in widths))
        for row in self.rows:
            out.append(
                GAP.join(c.lay(v, w) for c, v, w in zip(self.columns, row, widths, strict=True))
            )
        if self.totals:
            out.append(GAP.join(RULE * width for width in widths))
            for row in self.totals:
                out.append(
                    GAP.join(c.lay(v, w) for c, v, w in zip(self.columns, row, widths, strict=True))
                )
        return out

    def __str__(self) -> str:
        return "\n".join(self.lines())


def heading(title: str, subtitle: str = "") -> list[str]:
    """The two lines every report opens with."""
    out = [title.upper()]
    if subtitle:
        out.append(subtitle)
    out.append("")
    return out


def money(amount: Money) -> str:
    return str(amount)


def hours(hundredths: int) -> str:
    return f"{hundredths / 100:.2f}"


def miles(tenths: int) -> str:
    return f"{tenths / 10:.1f}"

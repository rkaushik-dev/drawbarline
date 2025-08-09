"""The interchange sheet, as it actually arrives.

Three of the connections send a fixed width file once a day and have done
since long before anybody here had a choice about it. Eighty columns, one
record a line, a header naming the road and the junction, then the car
records, then a trailer with a count. The count is the only integrity check
the format has and it is worth more than it looks: a file truncated by a
transfer is the single most common thing that goes wrong.

The parser does not stop at the first bad line. A file of two hundred records
with one bad date in the middle has a hundred and ninety nine good records in
it, and a clerk needs to know which line to fix rather than being told the
file is bad. So parsing gives back what it read and what it could not, and the
caller decides whether to take the good half.

Kind codes are two letters and they are not ours. They are what the roads
agreed on, they do not spell the words they stand for, and the table below is
the only place they are understood.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime

from drawbarline.clock import to_local, to_utc
from drawbarline.errors import DrawbarlineError
from drawbarline.events.kinds import EventKind
from drawbarline.events.record import Event
from drawbarline.marks import CarMark, ReportingMark
from drawbarline.reference import drawbar_line

LINE_WIDTH = 80
HEADER = "HD"
RECORD = "IC"
TRAILER = "TR"

KIND_CODES: dict[str, EventKind] = {
    "RC": EventKind.RECEIVED,
    "DL": EventKind.DELIVERED,
    "MV": EventKind.MOVED,
    "NT": EventKind.NOTIFIED,
    "PL": EventKind.PLACED,
    "CP": EventKind.CONSTRUCTIVE,
    "RL": EventKind.RELEASED,
    "PU": EventKind.PULLED,
    "BO": EventKind.BAD_ORDERED,
    "RP": EventKind.REPAIRED,
    "HL": EventKind.HELD,
    "FR": EventKind.FREED,
    "WG": EventKind.WEIGHED,
}

CODE_FOR = {kind: code for code, kind in KIND_CODES.items()}


class SheetError(DrawbarlineError):
    """A sheet cannot be read or cannot be written."""

    code = "sheet"


@dataclass(frozen=True, slots=True)
class Header:
    """Who sent the file, from where, and for what day."""

    road: ReportingMark
    junction: str
    day: datetime
    sequence: int

    def render(self) -> str:
        return (
            f"{HEADER}{str(self.road):<4}{self.junction:<4}{self.day:%Y%m%d}{self.sequence:04d}"
        ).ljust(LINE_WIDTH)


@dataclass(frozen=True, slots=True)
class Problem:
    """One line that would not read, and why."""

    line: int
    text: str
    why: str

    def __str__(self) -> str:
        return f"line {self.line}: {self.why}"


@dataclass(slots=True)
class Sheet:
    """What came out of a file."""

    header: Header | None = None
    events: list[Event] = field(default_factory=list)
    problems: list[Problem] = field(default_factory=list)
    declared: int | None = None

    @property
    def clean(self) -> bool:
        return not self.problems and self.counted

    @property
    def counted(self) -> bool:
        """Whether the trailer agrees with what was read."""
        return self.declared is not None and self.declared == len(self.events)


def _field(line: str, start: int, width: int) -> str:
    return line[start : start + width].strip()


def parse_record(line: str, station: str, road: ReportingMark) -> Event:
    """One car record. Raises rather than reporting, because the caller catches."""
    code = _field(line, 2, 2)
    if code not in KIND_CODES:
        raise SheetError("no such kind code", code=code)
    initial = _field(line, 4, 4)
    number = _field(line, 8, 6)
    stamp = _field(line, 14, 8)
    clock = _field(line, 22, 4)
    waybill = _field(line, 26, 6) or "0"
    tenths = _field(line, 32, 5) or "0"
    industry = _field(line, 37, 12)

    kind = KIND_CODES[code]
    try:
        local = datetime.strptime(stamp + clock, "%Y%m%d%H%M")
    except ValueError as bad:
        raise SheetError("that is not a date and time", stamp=stamp + clock) from bad

    return Event(
        kind=kind,
        car=CarMark.parse(f"{initial} {number}"),
        when=to_utc(drawbar_line().station(station), local),
        station=station,
        road=road if kind.needs_road else None,
        industry=industry or None,
        # The format has no way to say a field is absent, so zero is how the
        # roads write one. A movement of no distance and a waybill numbered
        # zero are both impossible, which is what makes that safe to read.
        waybill=int(waybill) or None,
        tenths=int(tenths) or None,
    )


def parse(text: str) -> Sheet:
    """Read a whole file, keeping the good records and naming the bad lines."""
    sheet = Sheet()
    station = ""
    road: ReportingMark | None = None
    for number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        kind = line[:2]
        try:
            if kind == HEADER:
                road = ReportingMark.parse(_field(line, 2, 4))
                station = _field(line, 6, 4)
                drawbar_line().station(station)
                sheet.header = Header(
                    road=road,
                    junction=station,
                    day=datetime.strptime(_field(line, 10, 8), "%Y%m%d"),
                    sequence=int(_field(line, 18, 4) or 0),
                )
            elif kind == RECORD:
                if road is None:
                    raise SheetError("a record before the header")
                sheet.events.append(parse_record(line, station, road))
            elif kind == TRAILER:
                sheet.declared = int(_field(line, 2, 6) or 0)
            else:
                raise SheetError("no such record type", kind=kind)
        except (DrawbarlineError, ValueError) as bad:
            sheet.problems.append(Problem(number, line, str(bad)))
    return sheet


def render_record(event: Event) -> str:
    """One car record, as we send it."""
    local = to_local(drawbar_line().station(event.station), event.when)
    return (
        f"{RECORD}{CODE_FOR[event.kind]}"
        f"{str(event.car.mark):<4}{event.car.number:06d}"
        f"{local:%Y%m%d%H%M}"
        f"{event.waybill or 0:06d}"
        f"{event.tenths or 0:05d}"
        f"{(event.industry or ''):<12}"
    ).ljust(LINE_WIDTH)


def render(header: Header, events: list[Event]) -> str:
    """A whole file, header and trailer included."""
    lines = [header.render()]
    lines += [render_record(one) for one in events]
    lines.append(f"{TRAILER}{len(events):06d}".ljust(LINE_WIDTH))
    return "\n".join(lines) + "\n"

"""What a repair costs, before anybody argues about who pays it.

Two halves again. Labor is hours at the rate the road doing the work charges,
and the hours are not negotiable: the price master says a coupler takes an
hour and a quarter whether it took the machinist twenty minutes or all
afternoon. Material is the part, at the price the master carries on the day
the work was done.

Then depreciation, which is the part outsiders find strange. A car owner whose
forty year old wheelset is replaced does not pay for a new wheelset, because
they did not have a new wheelset. They pay the new price less what the old one
had already been used up, on a published percentage a year with a floor. The
floor matters. Without it a sufficiently old part would be free and every road
would wait for the owner's cars to get old before repairing anything.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import BillingError
from drawbarline.marks import ReportingMark
from drawbarline.money import Money
from drawbarline.repairs.codes import JobCode

HUNDREDTHS_PER_HOUR = 100
PERCENT = 100


@dataclass(frozen=True, slots=True)
class PriceLine:
    """One job in the master, from a date."""

    job: JobCode
    effective: date
    labor_hundredths: int
    material_cents: int

    def __post_init__(self) -> None:
        if self.labor_hundredths < 0:
            raise BillingError("labor does not run backwards", job=str(self.job))
        if self.material_cents < 0:
            raise BillingError("material does not pay the road", job=str(self.job))

    @property
    def material(self) -> Money:
        return Money(self.material_cents)

    @property
    def labor_hours(self) -> float:
        return self.labor_hundredths / HUNDREDTHS_PER_HOUR

    def __str__(self) -> str:
        return f"{self.job} {self.labor_hours:.2f} hours and {self.material}"


@dataclass(frozen=True, slots=True)
class Depreciation:
    """How fast a family of parts is used up."""

    family: str
    percent_a_year: int
    floor_percent: int

    def __post_init__(self) -> None:
        if not 0 <= self.percent_a_year <= PERCENT:
            raise BillingError("depreciation is a percentage", family=self.family)
        if not 0 <= self.floor_percent <= PERCENT:
            raise BillingError("the floor is a percentage", family=self.family)

    def payable_percent(self, years: int) -> int:
        """What share of the new price the owner still pays after so many years."""
        if years < 0:
            raise BillingError("a part has no negative age", family=self.family)
        taken = min(self.percent_a_year * years, PERCENT - self.floor_percent)
        return PERCENT - taken

    def applied(self, material: Money, years: int) -> Money:
        return Money(round(material.cents * self.payable_percent(years) / PERCENT))


class PriceMaster:
    """Every priced job, and the depreciation each family carries."""

    def __init__(self) -> None:
        self._lines: dict[JobCode, list[PriceLine]] = {}
        self._depreciation: dict[str, Depreciation] = {}

    def add(self, line: PriceLine) -> PriceLine:
        standing = self._lines.setdefault(line.job, [])
        if any(one.effective == line.effective for one in standing):
            raise BillingError(
                "that job is already priced from that date",
                job=str(line.job),
                effective=str(line.effective),
            )
        standing.append(line)
        standing.sort(key=lambda one: one.effective)
        return line

    def depreciate_family(self, rule: Depreciation) -> Depreciation:
        self._depreciation[rule.family] = rule
        return rule

    def line_for(self, job: JobCode, on: date) -> PriceLine:
        standing = [one for one in self._lines.get(job, []) if one.effective <= on]
        if not standing:
            raise BillingError("no price on file", job=str(job), on=str(on))
        return standing[-1]

    def depreciation_for(self, job: JobCode) -> Depreciation | None:
        return self._depreciation.get(job.family)

    def material_for(self, job: JobCode, on: date, years: int) -> Money:
        """The material price with any depreciation the family carries taken off."""
        line = self.line_for(job, on)
        rule = self.depreciation_for(job)
        if rule is None:
            return line.material
        return rule.applied(line.material, years)

    def __len__(self) -> int:
        return sum(len(lines) for lines in self._lines.values())


@dataclass(frozen=True, slots=True)
class LaborRate:
    """What one road charges an hour, from a date."""

    road: ReportingMark
    effective: date
    cents_an_hour: int

    def __post_init__(self) -> None:
        if self.cents_an_hour < 0:
            raise BillingError("labor does not pay the owner", road=str(self.road))

    def cost_of(self, hundredths: int) -> Money:
        return Money(round(self.cents_an_hour * hundredths / HUNDREDTHS_PER_HOUR))


class LaborRates:
    """The hourly rate every road in the pool charges."""

    def __init__(self) -> None:
        self._rates: dict[ReportingMark, list[LaborRate]] = {}

    def file(self, rate: LaborRate) -> LaborRate:
        standing = self._rates.setdefault(rate.road, [])
        if any(one.effective == rate.effective for one in standing):
            raise BillingError("that road already filed a rate that day", road=str(rate.road))
        standing.append(rate)
        standing.sort(key=lambda one: one.effective)
        return rate

    def rate_for(self, road: ReportingMark, on: date) -> LaborRate:
        standing = [one for one in self._rates.get(road, []) if one.effective <= on]
        if not standing:
            raise BillingError("no labor rate on file", road=str(road), on=str(on))
        return standing[-1]

    def roads(self) -> list[ReportingMark]:
        return sorted(self._rates)

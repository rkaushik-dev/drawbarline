"""The waybill, which is the paper the car moves on.

A waybill says which car, whose freight, from where to where, by what route,
and what is in it. Nothing moves without one and nothing is billed without
one. The route is not advisory. It is the sequence of our stations the car is
authorised over, and the chargeable mileage is measured along it, so a car
that went a different way needs a corrected waybill rather than a corrected
mileage figure.

An empty car moves on a waybill too, with no lading on it. That is the
distinction the whole file turns on: a lading of nothing is not a thing, but
an empty movement very much is.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import date

from drawbarline.errors import DrawbarlineError
from drawbarline.lading import Lading
from drawbarline.marks import CarMark
from drawbarline.mileage import TENTHS_PER_MILE, milepost, route_tenths


class WaybillError(DrawbarlineError):
    """A waybill contradicts itself or names a place we do not serve."""

    code = "waybill"


@dataclass(frozen=True, slots=True)
class Waybill:
    """One movement of one car, as the billing copy carries it."""

    number: int
    dated: date
    car: CarMark
    shipper: str
    consignee: str
    route: tuple[str, ...]
    lading: Lading | None = None

    def __post_init__(self) -> None:
        if self.number < 1:
            raise WaybillError("a waybill is numbered from one", number=self.number)
        if len(self.route) < 2:
            raise WaybillError(
                "a route runs from somewhere to somewhere else",
                number=self.number,
                route=list(self.route),
            )
        for station in self.route:
            milepost(station)
        for here, there in zip(self.route, self.route[1:], strict=False):
            if here == there:
                raise WaybillError(
                    "a route does not stop at the same place twice running",
                    number=self.number,
                    station=here,
                )
        if not self.shipper.strip() or not self.consignee.strip():
            raise WaybillError("a waybill names a shipper and a consignee", number=self.number)

    @property
    def origin(self) -> str:
        return self.route[0]

    @property
    def destination(self) -> str:
        return self.route[-1]

    @property
    def loaded(self) -> bool:
        return self.lading is not None

    @property
    def hazardous(self) -> bool:
        return self.lading is not None and self.lading.hazardous

    @property
    def tenths(self) -> int:
        """Chargeable tenths of a mile along the authorised route."""
        return route_tenths(list(self.route))

    @property
    def miles(self) -> float:
        return self.tenths / TENTHS_PER_MILE

    def passes(self, station: str) -> bool:
        return station in self.route

    def divert(self, at: str, to: str, *, number: int) -> Waybill:
        """Reconsign the car at a station it has not passed yet."""
        if at not in self.route:
            raise WaybillError("the car is not routed through there", at=at, number=self.number)
        if to == at:
            raise WaybillError("a diversion goes somewhere else", at=at, number=self.number)
        cut = self.route.index(at)
        if cut == 0:
            raise WaybillError("a diversion happens after the origin", at=at, number=self.number)
        return Waybill(
            number=number,
            dated=self.dated,
            car=self.car,
            shipper=self.shipper,
            consignee=self.consignee,
            route=(*self.route[: cut + 1], to),
            lading=self.lading,
        )

    def __str__(self) -> str:
        what = self.lading.commodity.name if self.lading else "empty"
        return f"wb {self.number} {self.car} {self.origin} to {self.destination} {what}"

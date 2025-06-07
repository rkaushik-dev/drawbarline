"""Turning a domain refusal into a status code.

The domain raises one of a dozen errors and says nothing about http, which is
right. This is the one table that decides what each of them means over the
wire, and it is a table rather than a chain of isinstance checks so that
adding an error class without deciding its status is visible rather than
silently a five hundred.

Every refusal comes back the same shape: the stable code, the message, and the
context the domain attached. A client that wants to tell one refusal from
another reads the code, never the prose.
"""

from __future__ import annotations

from typing import Any

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from drawbarline.clock import ClockError
from drawbarline.customers import CustomerError
from drawbarline.days import CalendarError
from drawbarline.errors import (
    BillingError,
    ConfigurationError,
    DrawbarlineError,
    DuplicateCar,
    EventError,
    MarkError,
    RateError,
    RegistryError,
    SettlementError,
    TariffError,
    UnknownCar,
)
from drawbarline.fleet.aar import EquipmentError
from drawbarline.fleet.car import CarRecordError
from drawbarline.fleet.ownership import OwnershipError
from drawbarline.fleet.stencil import StencilError
from drawbarline.lading import LadingError
from drawbarline.measure import MeasureError
from drawbarline.mileage import MileageError
from drawbarline.money import MoneyError
from drawbarline.network import NetworkError
from drawbarline.waybill import WaybillError
from drawbarline.weight import WeightError

NOT_FOUND = 404
CONFLICT = 409
UNPROCESSABLE = 422
BAD_REQUEST = 400

# Every error class the domain raises has a line here. A class without one
# falls back to a bad request, which is the reading somebody forgot rather
# than the reading somebody chose.
STATUS_FOR: dict[type[DrawbarlineError], int] = {
    UnknownCar: NOT_FOUND,
    NetworkError: NOT_FOUND,
    CustomerError: NOT_FOUND,
    DuplicateCar: CONFLICT,
    RegistryError: CONFLICT,
    EventError: CONFLICT,
    SettlementError: CONFLICT,
    OwnershipError: CONFLICT,
    TariffError: UNPROCESSABLE,
    RateError: UNPROCESSABLE,
    BillingError: UNPROCESSABLE,
    MarkError: UNPROCESSABLE,
    CarRecordError: UNPROCESSABLE,
    EquipmentError: UNPROCESSABLE,
    StencilError: UNPROCESSABLE,
    LadingError: UNPROCESSABLE,
    WaybillError: UNPROCESSABLE,
    MeasureError: UNPROCESSABLE,
    WeightError: UNPROCESSABLE,
    MoneyError: UNPROCESSABLE,
    MileageError: UNPROCESSABLE,
    ClockError: UNPROCESSABLE,
    CalendarError: UNPROCESSABLE,
    ConfigurationError: BAD_REQUEST,
}


def status_for(error: DrawbarlineError) -> int:
    """The status one refusal deserves, walking up to the nearest decided class."""
    for kind in type(error).__mro__:
        if kind in STATUS_FOR:
            return STATUS_FOR[kind]
    return BAD_REQUEST


def body_of(error: DrawbarlineError) -> dict[str, Any]:
    return {
        "code": error.code,
        "message": error.message,
        "context": {key: str(value) for key, value in sorted(error.context.items())},
    }


def install(app: FastAPI) -> FastAPI:
    """Hang the one handler every refusal comes through."""

    @app.exception_handler(DrawbarlineError)
    async def _refused(request: Request, error: DrawbarlineError) -> JSONResponse:
        return JSONResponse(status_code=status_for(error), content=body_of(error))

    return app

"""How long a field is allowed to be.

Every one of these numbers appeared twice: once in the domain object that
refuses a value too long, and once in the column that stores it. They agreed
when they were written and two of them had already drifted, so a note that the
domain accepted would not fit the column and the write failed a long way from
where the value came in.

They are here, once, and both sides import them. The numbers themselves are
not interesting and none of them is a rule about railroading. They are what
fits on the form.
"""

from __future__ import annotations

# What somebody writes on an event to explain a correction.
NOTE = 120

# The initials at the bottom of a form, and what the decision was about.
ACTOR = 32
SUBJECT = 60
DETAIL = 200

# Names as they appear on paperwork.
PARTY_NAME = 60
STATION_CODE = 4
SHIPPER_CODE = 8
INDUSTRY_CODE = 12
TARIFF_CODE = 8
BLOCK_CODE = 8

# Free text that travels on a document.
REASON = 200
REFERENCE = 60

# Codes that are not ours.
JOB_CODE = 4
WHY_MADE_CODE = 2
COMMODITY_CODE = 7
CAR_MARK = 11
REPORTING_MARK = 4
TYPE_CODE = 4
DESIGNATION = 4


def within(text: str, cap: int) -> bool:
    return len(text) <= cap


def fits(text: str, cap: int, what: str) -> str:
    """Return the text, or say which field it does not fit."""
    if not within(text, cap):
        raise ValueError(f"{what} is {len(text)} characters and the field holds {cap}")
    return text

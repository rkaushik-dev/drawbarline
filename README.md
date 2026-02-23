# drawbarline

Car hire, demurrage and interchange settlement for a short line railroad.

The road runs from the ore dock at Escanaba down the west shore of Green Bay
into Wisconsin, with a branch off Powers to Iron Mountain and the state line at
Niagara. It interchanges with three connections at seven junctions. Every car
that crosses one of those junctions starts or stops an obligation: hours of car
hire owed to the owner, days of demurrage owed by a shipper, a repair the owner
will be billed for. This is the system that keeps score.

## What it does

- **Fleet register.** Equipment records, lease terms, weight stencils and the
  fifty year interchange rule that quietly retires a car.
- **Event log.** Everything that happens to a car, sorted by when it happened
  rather than when the paperwork arrived, replayed into the car's state.
- **Car hire.** Hourly and mileage accrual off a date effective schedule, cut
  at every rate change and every car birthday, with reclaims under rules 5, 15
  and 24.
- **Demurrage.** A tariff that climbs, free time counted in chargeable days
  local to the station, bunching relief, average agreements whose credits
  expire with the month, and the conversion to storage.
- **Repair billing.** AAR job and why made codes, a price master with
  betterment, and exceptions under the ninety day filing clock.
- **Settlement.** Monthly close, netting by road, statements, disputes aged in
  thirty day buckets, adjustments to agreed months and remittances.
- **Operating.** Switch lists, blocking, consists and tonnage, car
  distribution, embargoes, clearances, placarding and interchange agreements.

## Running it

    pip install -e ".[dev]"
    alembic upgrade head
    drawbarline init
    drawbarline doctor

`doctor` says whether a set of books has everything a close needs. Load the
filings it asks for, then work a month:

    drawbarline reference rates rates.csv
    drawbarline events load interchange.csv
    drawbarline close 2025-01 --issued 2025-02-10 --by rjs

Every report is under `drawbarline report`, and the same reports are served by
the http interface:

    uvicorn "drawbarline.api.app:app_from" --factory

## Layout

    src/drawbarline/        the package
      carhire/              rates, accrual, reclaims, liability
      demurrage/            tariff, counting, average agreements, invoices
      events/               kinds, records, streams, replay, spans, positions
      fleet/                equipment, ownership, acceptance, stencils
      repairs/              codes, pricing, cards, exceptions
      settlement/           periods, ledger, statements, disputes, the close
      persistence/          models, mappers, repositories, unit of work
      api/  cli/  reports/  the three edges
    migrations/             alembic revisions
    tests/                  one module per unit

Nothing in the middle knows about the edges. The fleet register and the car
hire accrual work on objects and have never heard of a database, which is why
they can be handed a register built in a test or one backed by sqlite and
cannot tell the difference.

## The checks

    ruff check .
    ruff format --check .
    mypy
    pytest

All four run on every push, on Linux and on Windows, along with a coverage
floor, a migration check that the models and the revisions describe the same
schema, and a job that builds the wheel and imports it from a clean
environment.

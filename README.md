# drawbarline

Car hire, demurrage and interchange settlement for a short line railroad.

The road interchanges with three connections. Every car that crosses a junction
starts or stops an obligation: hours of car hire owed to the owner, days of
demurrage owed by a shipper, a repair the owner will be billed for. This is the
system that keeps score.

## Layout

    src/drawbarline/    the package
    tests/              one module per unit

## Running the checks

    ruff check .
    ruff format --check .
    mypy
    pytest

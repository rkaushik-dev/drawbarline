# drawbarline

[![CI](https://github.com/rkaushik-dev/drawbarline/actions/workflows/ci.yml/badge.svg)](https://github.com/rkaushik-dev/drawbarline/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-007396)
![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F)
![Gradle](https://img.shields.io/badge/build-Gradle-02303A)
![JUnit 5](https://img.shields.io/badge/tests-JUnit%205-25A162)

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

## Tech stack

- Java 21, built with Gradle 8.14.8 through the wrapper (Groovy DSL).
- Spring Boot 3.5.16: Spring Web MVC for the HTTP interface, Jakarta Bean
  Validation for request bodies, Spring Data JPA over Hibernate for the books.
- H2 as the database, a file by default. Flyway builds the schema from the
  migrations and Hibernate checks the mapping against it on start.
- picocli 4.7.7 for the command line.
- springdoc for the OpenAPI document and its browser.
- JUnit 5 and AssertJ for the suite, JaCoCo for coverage, Spotless with
  palantir-java-format for the layout of the sources.

## Building and testing

    ./gradlew build             # format check, compile, suite, coverage floor, jars
    ./gradlew spotlessCheck     # the sources are laid out as the formatter would
    ./gradlew spotlessApply     # lay them out
    ./gradlew test              # the suite
    ./gradlew installDist       # the command line, in build/install/drawbarline

Every compile runs with `-Xlint:all` and `-Werror`, so a warning fails the
build. `check` fails below 90% line coverage or 80% branch coverage of the main
sources; the HTML report is in `build/reports/jacoco/test/html`.

## Running it

    ./gradlew installDist
    export PATH="$PWD/build/install/drawbarline/bin:$PATH"
    drawbarline init
    drawbarline doctor

`init` builds the schema and opens the document number series. `doctor` says
whether a set of books has everything a close needs. Load the filings it asks
for, then work a month:

    drawbarline reference rates rates.csv
    drawbarline events load interchange.csv
    drawbarline close 2025-01 --issued 2025-02-10 --by rjs

Every report is under `drawbarline report`, and the same reports are served by
the HTTP interface:

    drawbarline serve --port 8000

The Boot jar runs the same command line on its own:

    java -jar build/libs/drawbarline-0.1.0.jar status

## Configuration

Settings come from what a command was given, then the environment, then the
defaults. No file is read. `drawbarline settings` prints what the process
ended up with.

| variable | default | what it is |
| --- | --- | --- |
| `DRAWBARLINE_DATABASE` | `drawbarline.db` | The file the books live in, or a JDBC URL. A plain name is stored as `<name>.mv.db`. `--database` overrides it. |
| `DRAWBARLINE_HOME_ROAD` | `DBL` | The reporting mark of the road these books belong to. |
| `DRAWBARLINE_LOG_LEVEL` | `info` | `debug`, `info`, `warning` or `error`. `--log-level` overrides it. |
| `DRAWBARLINE_ECHO_SQL` | off | `1`, `yes`, `true` or `on` logs every statement sent to the database; `0`, `no`, `false` or `off` does not. |

A setting that will not read is refused when the process starts rather than
the first time something needs it.

## Layout

    src/main/java/io/github/rkaushikdev/drawbarline/
      carhire/              rates, accrual, reclaims, liability
      demurrage/            tariff, counting, average agreements, invoices
      events/               kinds, records, streams, replay, spans, positions
      fleet/                equipment, ownership, acceptance, stencils
      repairs/              codes, pricing, cards, exceptions
      settlement/           periods, ledger, statements, disputes, the close
      persistence/          entities, mappers, repositories, unit of work
      service/  reporting/  where the edges meet the domain
      api/  cli/  reports/  the three edges
                            and one package for each smaller subject: money,
                            marks, network, clock, embargo, switching and so on
    src/main/resources/db/migration/
                            Flyway migrations, one a revision of the schema
    src/test/java/          one test class per unit; project/ checks the build,
                            the workflow and the sources themselves

Nothing in the middle knows about the edges. The fleet register and the car
hire accrual work on objects and have never heard of a database, which is why
they can be handed a register built in a test or one backed by the books and
cannot tell the difference.

## Command line

    drawbarline [--database=TEXT] [--log-level=LEVEL] COMMAND ...

`--help` on any command shows its options. A refusal about the books exits 1,
and arguments that are wrong exit 2; either way the refusal and its context go
to standard error.

| command | arguments | what it does |
| --- | --- | --- |
| `init` | | Build the schema and open the document number series. |
| `settings` | | What this process thinks it is, and where each setting can be set. |
| `status` | | How much is on the books. |
| `doctor` | `[--on=DATE]` | Whether these books are ready to be worked. |
| `serve` | `[--host=TEXT] [--port=INTEGER]` | Run the HTTP service until it is stopped. Listens on 127.0.0.1:8000 by default. |
| `fleet add` | `MARK --built --designation --gross-rail-load --length --light-weight --type-code [--rebuilt]` | File an equipment record. |
| `fleet list` | `[--owner]` | Every car on the register. |
| `fleet show` | `MARK` | Everything on one car. |
| `fleet retire` | `MARK --on [--reason]` | Take a car off the roster. |
| `events record` | `CAR KIND --at --when [--industry] [--road] [--tenths] [--waybill] [--second-pass]` | Record one event against one car. |
| `events load` | `SHEET` | Load a day of interchange out of a comma separated sheet. |
| `events show` | `CAR` | One car's stream and where it leaves the car. |
| `reference rates` | `SHEET` | Load car hire bands from `type_code,from_age,to_age,effective,hourly,mileage`. |
| `reference tariff` | `CODE --effective --free-empty --free-loaded --step...` | Publish a demurrage tariff. |
| `reference shippers` | `SHEET` | Load shippers from `code,name,average_agreement`. |
| `reference industries` | `SHEET` | Load industry track from `code,name,station,shipper,spots,switched`. |
| `reference show` | | What has been filed. |
| `close` | `MONTH --issued [--by] [--dry-run]` | Work a month through and shut it. |
| `reopen` | `MONTH --on --reason [--by]` | Open a closed month again, with the reason on file. |
| `periods` | | Which months are shut and which have been opened again. |
| `statement` | `ROAD MONTH [--number]` | Print the statement for one road and one month. |
| `audit` | `[--by] [--limit]` | Who decided what, newest first. |
| `export` | `MONTH [--out=FILE]` | Write a whole month as JSON, for the auditors. |
| `repairs price` | `SHEET` | Load the price master from `job,effective,labor_hundredths,material_cents`. |
| `repairs depreciation` | `FAMILY --floor --percent` | File how fast one family of parts is used up. |
| `repairs labor` | `ROAD --cents --effective` | File what one road charges an hour. |
| `repairs card` | `NUMBER CAR --at --by --on --defect...` | File a repair card. |
| `repairs show` | `NUMBER` | A card with the money on it. |
| `repairs except` | `NUMBER LINE --cents --ground --on` | Take exception to one line of a card. |
| `repairs answer` | `NUMBER LINE --allow` | Answer an exception by allowing some or none of it. |
| `report carhire` | `MONTH [--road]` | Car hire liability, by road or car by car. |
| `report utilisation` | `MONTH` | Turn time against standing time, car by car. |
| `report demurrage` | `MONTH [--by-shipper]` | Detention, car by car or customer by customer. |
| `report interchange` | `MONTH` | What crossed each junction, both ways. |
| `report onhand` | `[--at]` | Every car we have taken and not handed back. |
| `report age` | `[--on]` | How old the fleet is and what leaves interchange service soon. |
| `report repairs` | `MONTH [--exceptions]` | Repair billing for a month, or what is still under exception. |
| `report shop` | `[--at]` | Which cars are bad ordered and how long they have been down. |
| `report daily` | `--on [--at]` | The morning sheet for one day. |
| `report forecast` | `[--at] [--days]` | What the cars standing here cost a day if nothing moves. |
| `report balance` | `MONTH [--by-kind]` | The trial balance, which proves the other reports. |
| `report annual` | `YEAR [--road]` | The year on one page. |
| `report package` | `MONTH` | Everything that goes out after a close, in one document. |
| `interchange receive` | `FILE [--partial]` | Read a sheet and record what is on it. |
| `interchange send` | `--day --junction --road [--out=FILE] [--sequence]` | Write the day's interchange with one road as a sheet. |
| `interchange reconcile` | `FILE [--tolerance]` | Set a sheet they sent us against what we already have. |
| `agreement sign` | `ROAD JUNCTION --cap --closes --effective --kinds --opens [--expires] [--hazardous] [--by]` | File an interchange agreement. |
| `agreement list` | `[--on]` | Every agreement, or only the live ones. |
| `deliver` | `CAR --at --to --when [--commodity]` | Ask whether a car can be handed across right now. |
| `waybill issue` | `NUMBER CAR --consignee --dated --route --shipper [--commodity] [--commodity-name] [--pounds]` | Issue a waybill. Leave the commodity off for an empty. |
| `waybill show` | `NUMBER` | One waybill and the route it is authorised over. |
| `embargo issue` | `NUMBER --effective --reason [--station] [--road] [--commodity-family] [--expires] [--permits]` | Issue an embargo. It has to restrict at least one thing. |
| `embargo lift` | `NUMBER --on` | Lift an embargo early. |
| `embargo list` | `[--on]` | Every embargo, or only the live ones. |
| `switchlist` | `STATION --day [--at] [--order=CAR=INDUSTRY]...` | The list for one station, pulls first so the placements see the room. |
| `order place` | `NUMBER SHIPPER --cars --kind --track --wanted [--on]` | Take a car order. |
| `order cancel` | `NUMBER --on` | Cancel a car order. |
| `empties` | `[--at]` | Every car standing empty on our rail. |
| `distribute` | `--on [--at]` | Match the empties on hand against the open orders. |

## HTTP interface

`drawbarline serve` starts it. The OpenAPI document is at `/openapi.json` and
its browser at `/docs`. There is no authentication: the general office network
is the boundary.

A refusal about the books comes back as `{"code", "message", "context"}` with
the status its kind decides: 404 for something that is not on the books, 409
for a conflict with what is, 422 for a value the rules refuse. A request that
is not the shape its route takes comes back 422 with a `detail` list of what
was wrong, each entry a `type`, a `loc` and a `msg`.

| method | path | what it does |
| --- | --- | --- |
| GET | `/health` | Whether the process is up. It never touches the database. |
| GET | `/books` | How much is on the books. |
| POST | `/cars` | File an equipment record. |
| GET | `/cars` | Every car on the register in mark order, or every car one road owns. |
| GET | `/cars/{mark}` | One car. |
| PUT | `/cars/{mark}` | File a corrected record, keeping the lease history. |
| GET | `/cars/{mark}/ownership` | Who is owed for a car. |
| POST | `/cars/{mark}/leases` | File a lease term against a car. |
| POST | `/cars/{mark}/retirement` | Take a car off the roster. |
| POST | `/events` | Record one event against one car. |
| POST | `/events/batch` | A day of interchange in one transaction, all of it or none of it. |
| GET | `/events/cars/{mark}` | One car's stream, in the order it happened. |
| GET | `/events/cars/{mark}/state` | What the car is doing, replayed from its stream. |
| GET | `/periods` | Every month that has been closed. |
| POST | `/periods/{month}/close` | Work a month through and shut it. |
| POST | `/periods/{month}/reopen` | Open a closed month again, with the reason on file. |
| GET | `/statements/{road}/{month}` | The statement for one road and one month. |
| GET | `/ledger/{road}` | Every posting against one road, oldest first. |
| GET | `/reports/carhire/{month}` | Car hire liability, by road or car by car. |
| GET | `/reports/carhire/{month}/totals` | The same run as data, in cents a road. |
| GET | `/reports/demurrage/{month}` | Detention, car by car or customer by customer. |
| GET | `/reports/utilisation/{month}` | Turn time against standing time, car by car. |
| GET | `/reports/interchange/{month}` | What crossed each junction, both ways. |
| GET | `/reports/interchange/{month}/crossings` | The same counts as data, one row a junction and road. |
| GET | `/reports/onhand` | Every car we have taken and not handed back. |
| GET | `/reports/age` | How old the fleet is and what leaves interchange service soon. |
| GET | `/reports/age/profile` | The age profile as data, cars in each decade. |
| GET | `/reports/repairs/{month}` | Repair billing for a month, or what is still under exception. |
| GET | `/reports/shop` | Which cars are bad ordered and how long they have been down. |
| GET | `/reports/forecast` | What the cars standing here cost a day if nothing moves. |
| GET | `/reports/balance/{month}` | The trial balance, which proves the other reports. |
| GET | `/reports/annual/{year}` | The year on one page. |
| GET | `/reports/package/{month}` | Everything that goes out after a close, in one document. |
| GET | `/reports/package/{month}/sections` | What is in the package, for anybody checking one arrived whole. |
| POST | `/repairs/cards` | File a repair card. |
| GET | `/repairs/cards` | Cards for one car or one road. |
| GET | `/repairs/cards/{number}` | One card, priced as of now. |
| POST | `/repairs/cards/{number}/exceptions` | Take exception to one line of a card. |
| POST | `/repairs/cards/{number}/exceptions/{line}/answer` | Answer an exception by allowing some or none of it. |
| GET | `/repairs/exceptions` | Everything filed and not answered. |
| POST | `/waybills` | Issue a waybill. |
| GET | `/waybills` | Waybills for one car, over a span of days, or every loaded one. |
| GET | `/waybills/{number}` | One waybill. |
| POST | `/embargoes` | Issue an embargo. |
| GET | `/embargoes` | Every embargo, or only the ones live on a day. |
| POST | `/embargoes/{number}/lift` | Lift an embargo early. |
| GET | `/switchlist/{station}` | The list for one station on one day, pulls first. |
| POST | `/orders` | Take a car order. |
| GET | `/orders` | Orders still wanting cars on a day oldest first, or every order one shipper placed. |
| POST | `/orders/{number}/cancel` | Cancel a car order. |
| GET | `/empties` | Every car standing empty on our rail. |
| GET | `/distribution` | Match the empties on hand against the open orders. |
| GET | `/audit` | Decisions, newest first, filtered by whichever of actor, action and subject is given. |
| GET | `/audit/actions` | Every decision this application records. |

## The checks

    ./gradlew spotlessCheck
    ./gradlew compileJava compileTestJava
    ./gradlew test
    ./gradlew test jacocoTestCoverageVerification

All four run on every push, along with a migration job that checks the
entities and the migrations describe the same schema, and a job that installs
the distribution and runs the command on its own.

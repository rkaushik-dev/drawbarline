package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.embargo.Embargo;
import io.github.rkaushikdev.drawbarline.lading.Commodity;
import io.github.rkaushikdev.drawbarline.lading.Lading;
import io.github.rkaushikdev.drawbarline.lading.Stcc;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.switching.Placement;
import io.github.rkaushikdev.drawbarline.switching.SwitchError;
import io.github.rkaushikdev.drawbarline.switching.SwitchList;
import io.github.rkaushikdev.drawbarline.switching.Switching;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.github.rkaushikdev.drawbarline.waybill.Waybill;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Operating routes: waybills, embargoes and the switch list.
 *
 * <p>These are the ones the yard uses rather than the ones the general office uses, and they read
 * differently for it. A switch list is a get that computes something and stores nothing, so a crew
 * asking for one twice in five minutes gets the same answer both times.
 *
 * <p>An embargo is issued and lifted and never edited, which is why there is a post to lift one
 * rather than an edit of the embargo itself.
 */
@RestController
@Tag(name = "operating")
final class OperationsController {

    private final Work work;

    OperationsController(Work work) {
        this.work = work;
    }

    /** What a loaded car carries. */
    record LadingIn(@NotNull String stcc, @NotNull String name, @NotNull Long pounds, Boolean bulk) {

        LadingIn {
            bulk = bulk != null && bulk;
        }
    }

    /** A waybill as it is issued. */
    record WaybillIn(
            @NotNull Integer number,
            @NotNull LocalDate dated,
            @NotNull String car,
            @NotNull String shipper,
            @NotNull String consignee,
            @NotNull List<@NotNull String> route,
            @Valid LadingIn lading) {}

    /** A waybill as it reads back, with what the route makes of it. */
    record WaybillOut(
            int number,
            LocalDate dated,
            String car,
            String shipper,
            String consignee,
            List<String> route,
            String origin,
            String destination,
            boolean loaded,
            boolean hazardous,
            int tenths,
            Optional<String> commodity) {

        static WaybillOut of(Waybill bill) {
            return new WaybillOut(
                    bill.number(),
                    bill.dated(),
                    bill.car().toString(),
                    bill.shipper(),
                    bill.consignee(),
                    bill.route(),
                    bill.origin(),
                    bill.destination(),
                    bill.loaded(),
                    bill.hazardous(),
                    bill.tenths(),
                    bill.lading().map(lading -> lading.commodity().name()));
        }
    }

    /** An embargo as it is issued. */
    record EmbargoIn(
            @NotNull Integer number,
            @NotNull LocalDate effective,
            @NotNull String reason,
            String station,
            String commodityFamily,
            String road,
            LocalDate expires,
            Boolean permitsAllowed) {

        EmbargoIn {
            permitsAllowed = permitsAllowed != null && permitsAllowed;
        }
    }

    /** An embargo as it reads back. */
    record EmbargoOut(
            int number,
            LocalDate effective,
            Optional<LocalDate> expires,
            String reason,
            Optional<String> station,
            Optional<String> commodityFamily,
            Optional<String> road,
            boolean permitsAllowed) {

        static EmbargoOut of(Embargo one) {
            return new EmbargoOut(
                    one.number(),
                    one.effective(),
                    one.expires(),
                    one.reason(),
                    one.station(),
                    one.commodityFamily(),
                    one.road().map(ReportingMark::toString),
                    one.permitsAllowed());
        }
    }

    /** The day an embargo is lifted. */
    record LiftIn(@NotNull LocalDate on) {}

    /** One move on a switch list. */
    record MoveOut(String car, String kind, String industry, String note) {}

    /** The list for one station on one day. */
    record SwitchListOut(String station, LocalDate day, List<MoveOut> moves, int worked) {

        static SwitchListOut of(SwitchList found) {
            return new SwitchListOut(
                    found.station(),
                    found.day(),
                    found.moves().stream()
                            .map(one ->
                                    new MoveOut(one.car().toString(), one.kind().text(), one.industry(), one.note()))
                            .toList(),
                    found.worked());
        }
    }

    static Waybill waybillOf(WaybillIn filing) {
        Optional<Lading> lading = Optional.empty();
        if (filing.lading() != null) {
            LadingIn carried = filing.lading();
            lading = Optional.of(new Lading(
                    new Commodity(Stcc.parse(carried.stcc()), carried.name(), carried.bulk()),
                    new Weight(carried.pounds())));
        }
        return new Waybill(
                filing.number(),
                filing.dated(),
                CarMark.parse(filing.car()),
                filing.shipper(),
                filing.consignee(),
                filing.route().stream().map(one -> Text.upper(Text.strip(one))).toList(),
                lading);
    }

    /** Issue a waybill. */
    @PostMapping("/waybills")
    @ResponseStatus(HttpStatus.CREATED)
    WaybillOut issueWaybill(@Valid @RequestBody WaybillIn filing) {
        return work.apply(books -> WaybillOut.of(books.waybills().issue(waybillOf(filing))));
    }

    /** One waybill. */
    @GetMapping("/waybills/{number}")
    WaybillOut showWaybill(@PathVariable("number") int number) {
        return work.read(books -> WaybillOut.of(books.waybills().get(number)));
    }

    /** Waybills for one car, over a span of days, or every loaded one. */
    @GetMapping("/waybills")
    List<WaybillOut> listWaybills(
            @RequestParam(name = "car", required = false) String car,
            @RequestParam(name = "first", required = false) LocalDate first,
            @RequestParam(name = "last", required = false) LocalDate last) {
        return work.read(books -> {
            List<Waybill> found;
            if (car != null && !car.isEmpty()) {
                found = books.waybills().forCar(CarMark.parse(car));
            } else if (first != null && last != null) {
                found = books.waybills().datedBetween(first, last);
            } else {
                found = books.waybills().loadedOnly();
            }
            return found.stream().map(WaybillOut::of).toList();
        });
    }

    /** Issue an embargo. */
    @PostMapping("/embargoes")
    @ResponseStatus(HttpStatus.CREATED)
    EmbargoOut issueEmbargo(@Valid @RequestBody EmbargoIn filing) {
        return work.apply(books -> {
            Optional<String> station = Optional.ofNullable(filing.station())
                    .filter(given -> !given.isEmpty())
                    .map(given -> Text.upper(Text.strip(given)));
            Optional<ReportingMark> road = Optional.ofNullable(filing.road())
                    .filter(given -> !given.isEmpty())
                    .map(ReportingMark::parse);
            Embargo embargo = new Embargo(
                    filing.number(),
                    filing.effective(),
                    filing.reason(),
                    station,
                    Optional.ofNullable(filing.commodityFamily()),
                    road,
                    Optional.ofNullable(filing.expires()),
                    filing.permitsAllowed());
            return EmbargoOut.of(books.embargoes().issue(embargo));
        });
    }

    /** Every embargo, or only the ones live on a day. */
    @GetMapping("/embargoes")
    List<EmbargoOut> listEmbargoes(@RequestParam(name = "on", required = false) LocalDate on) {
        return work.read(books -> {
            List<Embargo> found =
                    on == null ? books.embargoes().all() : books.embargoes().inForceOn(on);
            return found.stream().map(EmbargoOut::of).toList();
        });
    }

    /** Lift an embargo early. */
    @PostMapping("/embargoes/{number}/lift")
    EmbargoOut liftEmbargo(@PathVariable("number") int number, @Valid @RequestBody LiftIn filing) {
        return work.apply(books -> EmbargoOut.of(books.embargoes().lift(number, filing.on())));
    }

    /**
     * The list for one station on one day, pulls first.
     *
     * <p>Each order is written {@code car=industry}, because a query parameter that is two things
     * needs a separator and an equals sign is the one nobody looks up. The parameter repeats, one
     * order each time, and each value is taken whole, commas and all.
     */
    @GetMapping("/switchlist/{station}")
    @Parameter(
            name = "order",
            in = ParameterIn.QUERY,
            description = "An order written car=industry, repeated for each car.",
            array = @ArraySchema(schema = @Schema(type = "string")))
    SwitchListOut switchList(
            @PathVariable("station") String station,
            @RequestParam(name = "day") LocalDate day,
            @RequestParam(name = "at", required = false) Optional<String> at,
            WebRequest request) {
        String[] order = request.getParameterValues("order");
        return work.read(books -> {
            List<Placement> orders = new ArrayList<>();
            for (String one : order == null ? new String[0] : order) {
                int split = one.indexOf('=');
                String industry = split < 0 ? "" : one.substring(split + 1);
                if (industry.isEmpty()) {
                    throw new SwitchError("an order is written as car=industry", Details.of("given", one));
                }
                orders.add(new Placement(CarMark.parse(one.substring(0, split)), Text.upper(Text.strip(industry))));
            }
            Instant moment = Moments.orNow(Moments.query("at", at));
            return SwitchListOut.of(Switching.plan(
                    books.events().log(),
                    books.customers().register(),
                    Text.upper(Text.strip(station)),
                    day,
                    moment,
                    orders));
        });
    }
}

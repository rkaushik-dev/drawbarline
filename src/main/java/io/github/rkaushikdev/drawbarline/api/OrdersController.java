package io.github.rkaushikdev.drawbarline.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.rkaushikdev.drawbarline.distribution.Assignment;
import io.github.rkaushikdev.drawbarline.distribution.CarOrder;
import io.github.rkaushikdev.drawbarline.distribution.Distribution;
import io.github.rkaushikdev.drawbarline.distribution.Empty;
import io.github.rkaushikdev.drawbarline.fleet.CarKind;
import io.github.rkaushikdev.drawbarline.util.Text;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Car order and distribution routes.
 *
 * <p>An order is taken and cancelled and never edited, so there is a post and a post to cancel and
 * nothing else. A shipper who wants three cars instead of four cancels and orders again, which is
 * what they do on the telephone, and it leaves a record of both.
 *
 * <p>The distribution is a get. It computes which empty goes against which order and stores none
 * of it, so two people asking during the same morning see the same answer and neither of them has
 * taken the cars.
 */
@RestController
@Tag(name = "orders")
final class OrdersController {

    private final Work work;

    OrdersController(Work work) {
        this.work = work;
    }

    /** A car order as it is taken. */
    record OrderIn(
            @NotNull Integer number,
            @NotNull String shipper,
            @NotNull String industry,
            @NotNull String kind,
            @NotNull Integer cars,
            @NotNull LocalDate wantedOn,
            @NotNull LocalDate placedOn) {}

    /** The day an order is cancelled. */
    record CancelIn(@NotNull LocalDate on) {}

    /** An order as it reads back, with the day it was cancelled once it was. */
    record OrderOut(
            int number,
            String shipper,
            String industry,
            String kind,
            int cars,
            LocalDate wantedOn,
            Optional<LocalDate> cancelledOn) {

        static OrderOut of(CarOrder order, Optional<LocalDate> cancelled) {
            return new OrderOut(
                    order.number(),
                    order.shipper(),
                    order.industry(),
                    order.kind().description(),
                    order.cars(),
                    order.wantedOn(),
                    cancelled);
        }
    }

    /** A car standing empty on our rail. */
    record EmptyOut(String car, String kind, String station) {

        static EmptyOut of(Empty one) {
            return new EmptyOut(one.car().toString(), one.kind().description(), one.station());
        }
    }

    /** One empty sent against one order. */
    record AssignmentOut(int order, String car, String fromStation, String toStation, int tenths) {

        static AssignmentOut of(Assignment one) {
            return new AssignmentOut(
                    one.order(), one.car().toString(), one.fromStation(), one.toStation(), one.tenths());
        }
    }

    /** The empties matched against a day's orders, and how many cars each order went short. */
    record DistributionOut(
            LocalDate wantedOn,
            List<AssignmentOut> assignments,
            @JsonProperty("short") Map<Integer, Integer> shortages,
            int filled,
            int unfilled,
            int emptyTenths) {}

    /** Take a car order. */
    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    OrderOut place(@Valid @RequestBody OrderIn filing) {
        return work.apply(books -> {
            CarOrder order = new CarOrder(
                    filing.number(),
                    Text.upper(Text.strip(filing.shipper())),
                    Text.upper(Text.strip(filing.industry())),
                    CarKind.fromLetter(Text.upper(Text.strip(filing.kind()))),
                    filing.cars(),
                    filing.wantedOn());
            books.orders().place(order, filing.placedOn());
            return OrderOut.of(order, Optional.empty());
        });
    }

    /** Orders still wanting cars on a day, oldest first, or every order one shipper placed. */
    @GetMapping("/orders")
    List<OrderOut> openOrders(
            @RequestParam(name = "on") LocalDate on, @RequestParam(name = "shipper", required = false) String shipper) {
        return work.read(books -> {
            List<CarOrder> found = shipper == null || shipper.isEmpty()
                    ? books.orders().openOn(on)
                    : books.orders().forShipper(Text.upper(Text.strip(shipper)));
            return found.stream()
                    .map(one -> OrderOut.of(one, books.orders().cancelledOn(one.number())))
                    .toList();
        });
    }

    /** Cancel a car order. */
    @PostMapping("/orders/{number}/cancel")
    OrderOut cancel(@PathVariable("number") int number, @Valid @RequestBody CancelIn filing) {
        return work.apply(books -> OrderOut.of(books.orders().cancel(number, filing.on()), Optional.of(filing.on())));
    }

    /** Every car standing empty on our rail. */
    @GetMapping("/empties")
    List<EmptyOut> empties(@RequestParam(name = "at", required = false) Optional<String> at) {
        return work.read(books -> {
            Instant moment = Moments.orNow(Moments.query("at", at));
            return Distribution.emptiesOnHand(
                            books.fleet().register(), books.events().log(), moment)
                    .stream()
                    .map(EmptyOut::of)
                    .toList();
        });
    }

    /** Match the empties on hand against the open orders. */
    @GetMapping("/distribution")
    DistributionOut distribution(
            @RequestParam(name = "on") LocalDate on, @RequestParam(name = "at", required = false) Optional<String> at) {
        return work.read(books -> {
            Instant moment = Moments.orNow(Moments.query("at", at));
            List<CarOrder> orders = books.orders().openOn(on);
            List<Empty> empty = Distribution.emptiesOnHand(
                    books.fleet().register(), books.events().log(), moment);
            Distribution found =
                    Distribution.distribute(orders, empty, books.orders().stations());
            return new DistributionOut(
                    on,
                    found.assignments().stream().map(AssignmentOut::of).toList(),
                    found.shortages(),
                    found.filled(),
                    found.unfilled(),
                    found.emptyTenths());
        });
    }
}

package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Whether the process is up, and how much is on the books. */
@RestController
@Tag(name = "service")
final class ServiceController {

    private final Work work;

    ServiceController(Work work) {
        this.work = work;
    }

    /** Whether the process is up. It says nothing about the database, and never touches it. */
    @GetMapping("/health")
    Map<String, String> health() {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", "up");
        body.put("version", Drawbarline.VERSION);
        return body;
    }

    /** How much is on the books, which does touch the database. */
    @GetMapping("/books")
    Map<String, Integer> books() {
        return work.read(books -> {
            Map<String, Integer> body = new LinkedHashMap<>();
            body.put("cars", books.fleet().size());
            body.put("events", books.events().size());
            body.put("postings", books.ledger().size());
            body.put("closed_months", books.periods().closedPeriods().size());
            return body;
        });
    }
}

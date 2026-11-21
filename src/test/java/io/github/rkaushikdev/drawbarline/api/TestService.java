package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.github.rkaushikdev.drawbarline.persistence.TestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** What the service is handed when a test slice starts it: the suite's database. */
@TestConfiguration(proxyBeanMethods = false)
public class TestService {

    /** The suite's database. The suite closes it, not the context. */
    @Bean(destroyMethod = "")
    Database database() {
        return TestDatabase.fresh();
    }
}

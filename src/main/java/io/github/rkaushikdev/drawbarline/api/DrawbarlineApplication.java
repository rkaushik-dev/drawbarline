package io.github.rkaushikdev.drawbarline.api;

import io.github.rkaushikdev.drawbarline.Drawbarline;
import io.github.rkaushikdev.drawbarline.config.Settings;
import io.github.rkaushikdev.drawbarline.logs.Logs;
import io.github.rkaushikdev.drawbarline.persistence.Database;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextClosedEvent;

/**
 * The HTTP service: the fleet, the event log, the month close, the reports, repair billing, the
 * yard's operating routes, the audit trail and car orders, over one set of books.
 *
 * <p>The database is handed in rather than built here, so a test stands the whole thing up
 * against a database of its own and the office stands it up against the file on the share. Nothing
 * reached from {@link #run(Database, String...)} reads an environment variable; {@link
 * #run(Settings, String...)} is the one that starts from settings, and it is what a server process
 * calls. Spring Boot's own data source, JPA and Flyway set up are switched off: with H2 on the class
 * path and no data source of its own, Boot would otherwise build an embedded database and migrate
 * that.
 *
 * <p>There is no authentication on it. The general office network is the boundary and this
 * application sits behind it, which is a decision worth writing down because it is the kind of
 * thing somebody assumes was an oversight.
 */
@SpringBootApplication(
        exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
        })
public class DrawbarlineApplication {

    /** What the service calls itself in its OpenAPI document. */
    public static final String TITLE = "drawbarline";

    /** The one line under the title. */
    public static final String SUMMARY = "Car hire, demurrage and interchange settlement";

    /**
     * Start the service over a database somebody else opened and will close. {@code args} are
     * Spring Boot's own, such as {@code --server.port=0} for a port of the system's choosing.
     */
    public static ConfigurableApplicationContext run(Database database, String... args) {
        return run(database, List.of(), args);
    }

    /** Start the service with more configuration beside its own: the routes a test adds, say. */
    public static ConfigurableApplicationContext run(Database database, List<Class<?>> extra, String... args) {
        List<Class<?>> sources = new ArrayList<>();
        sources.add(DrawbarlineApplication.class);
        sources.addAll(extra);
        return new SpringApplicationBuilder(sources.toArray(Class<?>[]::new))
                .initializers(context -> context.getBeanFactory().registerSingleton("database", database))
                .build()
                .run(args);
    }

    /**
     * Start the service from settings, which is what a server does: the books the settings name,
     * opened here and closed when the service stops, and the application's logging as the settings
     * set it.
     */
    public static ConfigurableApplicationContext run(Settings settings, String... args) {
        Database database = Database.open(settings);
        ConfigurableApplicationContext context;
        try {
            context = run(database, args);
        } catch (RuntimeException failed) {
            database.close();
            throw failed;
        }
        context.addApplicationListener((ApplicationListener<ContextClosedEvent>) closed -> database.close());
        // Spring Boot sets logging up again as it starts, so the settings are applied after it.
        Logs.configure(settings);
        return context;
    }

    @Bean
    Work work(ObjectProvider<Database> database) {
        return new Work(Optional.ofNullable(database.getIfAvailable()));
    }

    @Bean
    Jackson2ObjectMapperBuilderCustomizer serviceJson() {
        return Json::configure;
    }

    @Bean
    OpenAPI openApi() {
        return new OpenAPI().info(new Info().title(TITLE).summary(SUMMARY).version(Drawbarline.VERSION));
    }
}

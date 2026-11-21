package io.github.rkaushikdev.drawbarline.persistence;

import static io.github.rkaushikdev.drawbarline.clock.Clock.utc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.clock.Clock;
import io.github.rkaushikdev.drawbarline.clock.ClockError;
import io.github.rkaushikdev.drawbarline.fleet.CarTypeCode;
import io.github.rkaushikdev.drawbarline.fleet.MechanicalDesignation;
import io.github.rkaushikdev.drawbarline.marks.CarMark;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.measure.Length;
import io.github.rkaushikdev.drawbarline.money.Money;
import io.github.rkaushikdev.drawbarline.persistence.models.CounterRow;
import io.github.rkaushikdev.drawbarline.persistence.scratch.SampleOfEveryType;
import io.github.rkaushikdev.drawbarline.weight.Weight;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

class PersistenceTypesTest {

    private static final AtomicInteger SCRATCHES = new AtomicInteger();

    private HikariDataSource pool;
    private LocalContainerEntityManagerFactoryBean bean;
    private EntityManagerFactory factory;

    @BeforeEach
    void scratchTable() {
        // The sample table is built from its own mapping on a database of its own, so it never
        // reaches the books or the comparison with the migrations.
        pool = Database.pool("jdbc:h2:mem:scratch-" + SCRATCHES.incrementAndGet());
        bean = Database.factoryBean(
                pool,
                "create",
                new Statements(false),
                List.of(
                        SampleOfEveryType.class.getPackageName(),
                        "io.github.rkaushikdev.drawbarline.persistence.types"));
        bean.afterPropertiesSet();
        factory = bean.getObject();
    }

    @AfterEach
    void dropScratch() {
        bean.destroy();
        pool.close();
    }

    private void inSession(Consumer<EntityManager> work) {
        EntityManager session = factory.createEntityManager();
        try {
            session.getTransaction().begin();
            work.accept(session);
            session.getTransaction().commit();
        } finally {
            if (session.getTransaction().isActive()) {
                session.getTransaction().rollback();
            }
            session.close();
        }
    }

    private SampleOfEveryType roundTrip(Consumer<SampleOfEveryType> values) {
        inSession(session -> {
            SampleOfEveryType sample = new SampleOfEveryType(1);
            values.accept(sample);
            session.persist(sample);
        });
        EntityManager session = factory.createEntityManager();
        try {
            return session.find(SampleOfEveryType.class, 1);
        } finally {
            session.close();
        }
    }

    private OffsetDateTime storedMoment() throws SQLException {
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement();
                ResultSet found = statement.executeQuery("select moment from sample_of_every_type where id = 1")) {
            assertThat(found.next()).isTrue();
            return found.getObject(1, OffsetDateTime.class);
        }
    }

    @Test
    void moneyComesBackAsMoney() {
        assertThat(roundTrip(sample -> sample.amount = new Money(-12345)).amount)
                .isEqualTo(new Money(-12345));
    }

    @Test
    void aWeightComesBackAsAWeight() {
        assertThat(roundTrip(sample -> sample.weight = new Weight(286000)).weight)
                .isEqualTo(new Weight(286000));
    }

    @Test
    void aLengthComesBackAsALength() {
        assertThat(roundTrip(sample -> sample.length = Length.of(59, 6)).length).isEqualTo(Length.of(59, 6));
    }

    @Test
    void aReportingMarkComesBackWhole() {
        assertThat(roundTrip(sample -> sample.road = new ReportingMark("ELS")).road)
                .isEqualTo(new ReportingMark("ELS"));
    }

    @Test
    void aCarMarkComesBackWhole() {
        CarMark car = CarMark.parse("DBL 104207");
        assertThat(roundTrip(sample -> sample.car = car).car).isEqualTo(car);
    }

    @Test
    void aTypeCodeComesBackWhole() {
        assertThat(roundTrip(sample -> sample.typeCode = new CarTypeCode("L351")).typeCode)
                .isEqualTo(new CarTypeCode("L351"));
    }

    @Test
    void aDesignationComesBackWhole() {
        SampleOfEveryType found = roundTrip(sample -> sample.designation = new MechanicalDesignation("LO"));
        assertThat(found.designation).isEqualTo(new MechanicalDesignation("LO"));
    }

    @Test
    void aMomentComesBackAwareAndInUtc() throws SQLException {
        SampleOfEveryType found = roundTrip(sample -> sample.moment = utc(2025, 1, 14, 14, 30));
        assertThat(found.moment).isEqualTo(utc(2025, 1, 14, 14, 30));
        assertThat(storedMoment().getOffset()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void aMomentInAnotherZoneIsStoredAsUtc() throws SQLException {
        ZonedDateTime local = ZonedDateTime.of(2025, 1, 14, 9, 30, 0, 0, ZoneId.of("America/Detroit"));
        assertThat(roundTrip(sample -> sample.moment = Clock.requireAware(local)).moment)
                .isEqualTo(utc(2025, 1, 14, 14, 30));
        assertThat(storedMoment()).isEqualTo(OffsetDateTime.of(2025, 1, 14, 14, 30, 0, 0, ZoneOffset.UTC));
    }

    @Test
    void aNaiveMomentIsRefused() throws NoSuchFieldException {
        // A stored moment is an instant, so a wall clock reading cannot be written to the column;
        // one read from outside without its zone is refused before it gets that far.
        assertThat(SampleOfEveryType.class.getField("moment").getType()).isEqualTo(Instant.class);
        assertThatThrownBy(() -> Clock.requireAware(LocalDateTime.of(2025, 1, 14, 14, 30)))
                .isInstanceOf(ClockError.class);
    }

    @Test
    void everyColumnTakesNothing() {
        SampleOfEveryType found = roundTrip(sample -> {});
        assertThat(found.amount).isNull();
        assertThat(found.car).isNull();
        assertThat(found.moment).isNull();
    }

    @Test
    void aUrlOfNothingIsRefused() {
        assertThatThrownBy(() -> Database.open("   ")).isInstanceOf(ConfigurationError.class);
    }

    @Test
    void foreignKeysAreOn() throws SQLException {
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("create table parent_of_sample (id integer primary key)");
            statement.execute("create table child_of_sample (id integer primary key, parent integer "
                    + "references parent_of_sample (id))");
            assertThatThrownBy(() -> statement.execute("insert into child_of_sample values (1, 99)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Referential integrity");
        }
    }

    @Test
    void aFailedSessionRollsBack() {
        Database books = TestDatabase.fresh();
        assertThatThrownBy(() -> books.inTransaction(session -> {
                    session.persist(new CounterRow(Numbering.INVOICES, 5001));
                    session.flush();
                    throw new ConfigurationError("something went wrong");
                }))
                .isInstanceOf(ConfigurationError.class);
        List<CounterRow> left =
                books.fromTransaction(session -> session.createQuery("select c from CounterRow c", CounterRow.class)
                        .getResultList());
        assertThat(left).isEmpty();
    }

    @Test
    void theScratchTableStaysOffTheApplicationTables() {
        assertThat(Database.modelTables())
                .doesNotContain("sample_of_every_type")
                .contains("car");
    }
}

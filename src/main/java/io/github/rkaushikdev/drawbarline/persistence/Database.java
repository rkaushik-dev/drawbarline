package io.github.rkaushikdev.drawbarline.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.config.Settings;
import io.github.rkaushikdev.drawbarline.util.Text;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.util.ClassUtils;

/**
 * One set of books: a connection pool, the schema's migrations, and the entity manager factory
 * every unit of work is opened from.
 *
 * <p>Nothing in the domain knows any of this exists. The fleet register and the car hire accrual
 * work on objects, and everything that knows about a database is in this package.
 *
 * <p>The command line opens one of these from the settings and nothing else, so a scheduled
 * command starts without a framework and honours the database it was given before anything
 * connects. The entity manager factory is built the first time it is asked for, and it checks the
 * mapping against the schema when it is, so a database nobody has migrated can still be migrated
 * and dropped.
 *
 * <p>The database enforces its foreign keys on every connection: a car hire posting cannot point
 * at a road that does not exist and go unnoticed until a statement comes out short.
 */
public final class Database implements AutoCloseable {

    /** Where the entities and their column converters live. */
    public static final List<String> MAPPED_PACKAGES = List.of(
            "io.github.rkaushikdev.drawbarline.persistence.models",
            "io.github.rkaushikdev.drawbarline.persistence.types");

    /** Where the migrations live, one per revision of the schema. */
    public static final String MIGRATIONS = "classpath:db/migration";

    /** The table the migrations keep their own history in, which is not part of the books. */
    public static final String HISTORY_TABLE = "flyway_schema_history";

    private static final String H2_MEMORY = "jdbc:h2:mem:";
    private static final AtomicInteger MEMORIES = new AtomicInteger();

    private final String url;
    private final HikariDataSource pool;
    private final Statements statements;
    private LocalContainerEntityManagerFactoryBean factoryBean;
    private EntityManagerFactory factory;

    private Database(String url, boolean echoSql) {
        this.url = url;
        this.statements = new Statements(echoSql);
        this.pool = pool(url);
    }

    /** Open the books the settings name. Nothing connects until something is asked. */
    public static Database open(Settings settings) {
        return new Database(settings.url(), settings.echoSql());
    }

    /** Open the books at a JDBC URL. */
    public static Database open(String url) {
        if (Text.isBlank(url)) {
            throw new ConfigurationError("a database url is required");
        }
        return new Database(url, false);
    }

    /**
     * A database of its own in memory, which is what a test or a dry run wants. It lives until it
     * is closed, however many connections come and go in between.
     */
    public static Database inMemory() {
        return open(H2_MEMORY + "drawbarline-" + MEMORIES.incrementAndGet());
    }

    /**
     * The URL as the driver is given it. An H2 database in memory is told to outlive its
     * connections, because otherwise the schema built on the first connection is gone by the
     * second and every read finds an empty database.
     */
    public static String jdbcUrl(String configured) {
        if (configured.startsWith(H2_MEMORY) && !Text.upper(configured).contains("DB_CLOSE_DELAY")) {
            return configured + ";DB_CLOSE_DELAY=-1";
        }
        return configured;
    }

    /** A pool for a URL, sized for one process and connecting only when first used. */
    public static HikariDataSource pool(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(url));
        config.setPoolName("drawbarline");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        // A database that will not answer is reported within seconds, not after half a minute.
        config.setConnectionTimeout(3_000);
        return new HikariDataSource(config);
    }

    /**
     * An entity manager factory over a data source.
     *
     * @param ddl what Hibernate does to the schema when the factory is built: {@code validate} for
     *     the books, {@code create} only to build a schema straight from the mapping to compare it
     *     with what the migrations build
     * @param packages where the entities and converters are found
     */
    public static LocalContainerEntityManagerFactoryBean factoryBean(
            DataSource source, String ddl, StatementInspector inspector, List<String> packages) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(source);
        bean.setPackagesToScan(packages.toArray(String[]::new));
        bean.setPersistenceUnitName("drawbarline");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddl);
        properties.put("hibernate.jdbc.time_zone", "UTC");
        properties.put("hibernate.default_batch_fetch_size", "64");
        // Hibernate otherwise lays a table's columns out by type size, and a schema it builds
        // stops reading in the order the migrations declare.
        properties.put("hibernate.column_ordering_strategy", "legacy");
        properties.put("hibernate.session_factory.statement_inspector", inspector);
        bean.setJpaPropertyMap(properties);
        return bean;
    }

    /**
     * Every table the entities declare, sorted. This is what the migrations are checked against: a
     * table mapped in a class nobody scans is absent from it, and the check fails here rather than
     * as a missing table at runtime.
     */
    public static List<String> modelTables() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        TreeSet<String> names = new TreeSet<>();
        for (String where : MAPPED_PACKAGES) {
            for (BeanDefinition found : scanner.findCandidateComponents(where)) {
                Class<?> type = ClassUtils.resolveClassName(found.getBeanClassName(), Database.class.getClassLoader());
                names.add(type.getAnnotation(Table.class).name());
            }
        }
        return List.copyOf(names);
    }

    /** The URL as configured. */
    public String url() {
        return url;
    }

    public DataSource dataSource() {
        return pool;
    }

    /** The statements this database is sent, for a caller that wants to watch them. */
    public Statements statements() {
        return statements;
    }

    /** The factory, built and checked against the schema the first time it is asked for. */
    public synchronized EntityManagerFactory entityManagerFactory() {
        if (factory == null) {
            factoryBean = factoryBean(pool, "validate", statements, MAPPED_PACKAGES);
            factoryBean.afterPropertiesSet();
            factory = factoryBean.getObject();
        }
        return factory;
    }

    /** A unit of work on these books, open and with its transaction begun. */
    public UnitOfWork unitOfWork() {
        return new UnitOfWork(this).open();
    }

    /**
     * Run some work in one transaction that commits when the work returns and rolls back if it
     * throws, and hand back what it found.
     */
    public <T> T fromTransaction(Function<EntityManager, T> work) {
        EntityManager session = entityManagerFactory().createEntityManager();
        EntityTransaction transaction = session.getTransaction();
        try {
            transaction.begin();
            T found = work.apply(session);
            transaction.commit();
            return found;
        } finally {
            if (transaction.isActive()) {
                transaction.rollback();
            }
            session.close();
        }
    }

    /** Run some work in one transaction that commits when it returns and rolls back if it throws. */
    public void inTransaction(Consumer<EntityManager> work) {
        fromTransaction(session -> {
            work.accept(session);
            return null;
        });
    }

    /** Put the schema in a database that has none, or bring an old one up to date. */
    public void migrate() {
        flyway().migrate();
    }

    /** Take the whole schema out, and everything in it with it. */
    public void drop() {
        closeFactory();
        flyway().clean();
    }

    /** The tables in the database, lower case and sorted, leaving out the migrations' history. */
    public List<String> tableNames() {
        try (Connection connection = pool.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            TreeSet<String> names = new TreeSet<>();
            try (ResultSet tables = metadata.getTables(null, null, "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    String schema = tables.getString("TABLE_SCHEM");
                    if (schema == null || !schema.equalsIgnoreCase("INFORMATION_SCHEMA")) {
                        names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
            names.remove(HISTORY_TABLE);
            return List.copyOf(names);
        } catch (SQLException trouble) {
            throw StoreError.of("the tables cannot be listed", url, trouble);
        }
    }

    /** Close what this database opened. A database in memory is given back. Safe to call twice. */
    @Override
    public synchronized void close() {
        closeFactory();
        if (!pool.isClosed()) {
            pool.close();
            if (url.startsWith(H2_MEMORY)) {
                shutDown();
            }
        }
    }

    /**
     * A database in memory outlives its connections until it is told to go, and it is told on a
     * connection of its own once the pool has let go of the rest.
     */
    private void shutDown() {
        try (Connection connection = DriverManager.getConnection(jdbcUrl(url));
                Statement statement = connection.createStatement()) {
            statement.execute("SHUTDOWN");
        } catch (SQLException trouble) {
            throw StoreError.of("the database would not shut down", url, trouble);
        }
    }

    private synchronized void closeFactory() {
        if (factoryBean != null) {
            factoryBean.destroy();
            factoryBean = null;
            factory = null;
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(pool)
                .locations(MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }
}

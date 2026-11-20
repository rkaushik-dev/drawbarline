package io.github.rkaushikdev.drawbarline.config;

import io.github.rkaushikdev.drawbarline.ConfigurationError;
import io.github.rkaushikdev.drawbarline.Details;
import io.github.rkaushikdev.drawbarline.MarkError;
import io.github.rkaushikdev.drawbarline.marks.ReportingMark;
import io.github.rkaushikdev.drawbarline.util.Text;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Everything the process needs to know before it does anything.
 *
 * <p>Settings come from three places, in this order: what the caller passed, then the
 * environment, then the defaults. There is no fourth place and no file that is read if it happens
 * to exist, because a setting from a file nobody mentioned is the one that takes an afternoon to
 * find.
 *
 * <p>Everything is validated when it is read rather than when it is used. A log level of {@code
 * verbose} is a mistake somebody made when they started the process, and telling them then is
 * worth more than telling them the first time something wants to log a warning.
 *
 * @param database the file the books live in, or a JDBC URL for a database somewhere else
 * @param homeRoad the reporting mark of the road these books belong to
 * @param logLevel how much to say: one of {@link #LEVELS}
 * @param echoSql whether every statement sent to the database is logged
 */
public record Settings(String database, String homeRoad, String logLevel, boolean echoSql) {

    public static final String PREFIX = "DRAWBARLINE_";
    public static final String DEFAULT_DATABASE = "drawbarline.db";
    public static final String DEFAULT_HOME = "DBL";
    public static final String DEFAULT_LEVEL = "info";

    /** The levels, quietest last. */
    public static final List<String> LEVELS = List.of("debug", "info", "warning", "error");

    /** The suffix the database engine adds to the name of the file it stores the books in. */
    public static final String STORE_SUFFIX = ".mv.db";

    private static final String JDBC = "jdbc:";
    private static final String FILE_URL = "jdbc:h2:file:";

    public Settings {
        if (Text.isBlank(database)) {
            throw new ConfigurationError("a database is required");
        }
        if (!LEVELS.contains(logLevel)) {
            throw new ConfigurationError("no such log level", Details.of("level", logLevel, "known", LEVELS));
        }
        try {
            ReportingMark.parse(homeRoad);
        } catch (MarkError bad) {
            // A mark that will not read is a configuration problem here, not a data problem, and
            // it should reach the operator wearing that name.
            throw new ConfigurationError("the home road is not a reporting mark", Details.of("home_road", homeRoad));
        }
    }

    /** The defaults, standing on their own. */
    public Settings() {
        this(DEFAULT_DATABASE, DEFAULT_HOME, DEFAULT_LEVEL, false);
    }

    /** The home road as a mark. */
    public ReportingMark home() {
        return ReportingMark.parse(homeRoad);
    }

    /** Whether the database is named by a URL rather than by the file it lives in. */
    public boolean isUrl() {
        return database.startsWith(JDBC);
    }

    /**
     * The JDBC URL, which is the database file with the driver in front of it, or the setting
     * itself when it is already a URL.
     */
    public String url() {
        if (isUrl()) {
            return database;
        }
        Path file = Path.of(database);
        String named = file.isAbsolute() || database.startsWith(".") ? database : "./" + database;
        return FILE_URL + named;
    }

    /** The file the books are stored in on disk, which only a database named by its file has. */
    public Path storeFile() {
        if (isUrl()) {
            throw new ConfigurationError("a database named by a url has no file", Details.of("database", database));
        }
        return Path.of(database + STORE_SUFFIX);
    }

    public Settings withDatabase(String database) {
        return new Settings(database, homeRoad, logLevel, echoSql);
    }

    public Settings withHomeRoad(String homeRoad) {
        return new Settings(database, homeRoad, logLevel, echoSql);
    }

    public Settings withLogLevel(String logLevel) {
        return new Settings(database, homeRoad, logLevel, echoSql);
    }

    public Settings withEchoSql(boolean echoSql) {
        return new Settings(database, homeRoad, logLevel, echoSql);
    }

    /** {@code DBL on drawbarline.db at info}. */
    @Override
    public String toString() {
        return homeRoad + " on " + database + " at " + logLevel;
    }

    /** Read the settings out of the process environment, falling back to the defaults. */
    public static Settings fromEnvironment() {
        return fromEnvironment(System.getenv());
    }

    /** Read the settings out of an environment, falling back to the defaults. */
    public static Settings fromEnvironment(Map<String, String> env) {
        return new Settings(
                env.getOrDefault(PREFIX + "DATABASE", DEFAULT_DATABASE),
                env.getOrDefault(PREFIX + "HOME_ROAD", DEFAULT_HOME),
                Text.lower(Text.strip(env.getOrDefault(PREFIX + "LOG_LEVEL", DEFAULT_LEVEL))),
                flag(env.getOrDefault(PREFIX + "ECHO_SQL", ""), PREFIX + "ECHO_SQL"));
    }

    /** Every environment variable this application reads, for the help text. */
    public static List<String> names() {
        return List.of(PREFIX + "DATABASE", PREFIX + "HOME_ROAD", PREFIX + "LOG_LEVEL", PREFIX + "ECHO_SQL");
    }

    private static boolean flag(String text, String name) {
        String cleaned = Text.lower(Text.strip(text));
        return switch (cleaned) {
            case "1", "yes", "true", "on" -> true;
            case "0", "no", "false", "off", "" -> false;
            default ->
                throw new ConfigurationError("that is not a yes or a no", Details.of("setting", name, "given", text));
        };
    }
}

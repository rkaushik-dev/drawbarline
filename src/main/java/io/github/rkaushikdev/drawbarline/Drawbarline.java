package io.github.rkaushikdev.drawbarline;

/**
 * Car hire, demurrage and interchange settlement for a short line railroad.
 *
 * <p>The code is laid out by what a thing is rather than by what kind of thing it is. Car hire
 * lives in one package, demurrage in another, and the persistence and the two interfaces sit at
 * the edges knowing about everything while nothing in the middle knows about them.
 *
 * <p>Loading a class opens no database, reads no environment variable and configures no logging.
 */
public final class Drawbarline {

    /** The release, as the command line and the service report it. */
    public static final String VERSION = "0.1.0";

    private Drawbarline() {}
}

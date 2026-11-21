/**
 * Car hire, demurrage and interchange settlement for a short line railroad.
 *
 * <p>The code is laid out by what a thing is rather than by what kind of thing it is. Car hire
 * lives in one package, demurrage in another, and the persistence and the two interfaces sit at the
 * edges knowing about everything while nothing in the middle knows about them.
 *
 * <p>This package holds the error taxonomy every refusal travels in. A caller can catch {@link
 * DrawbarlineError} and be sure it caught something raised on purpose. Each error carries a short
 * stable code, which is what a report groups on, and the values that caused it, which is what an
 * operator reads. Loading anything here opens no database, reads no environment variable and
 * configures no logging.
 */
package io.github.rkaushikdev.drawbarline;

package io.github.rkaushikdev.drawbarline.cli;

/**
 * The command line will not do what it was asked as it was asked: an argument it cannot read, a
 * sheet without the columns a loader needs, or a request that makes no sense on its face, such as
 * closing a month before it ends.
 *
 * <p>This is the command line's own refusal and it sits outside the application's error taxonomy on
 * purpose: nothing below the command line raises it, and a caller catching {@code DrawbarlineError}
 * is catching refusals about the books, never about how a command was typed. The message is the
 * whole sentence, and it names the argument and the form it wanted, because a command line is the
 * one interface where the user cannot see a schema. A command that ends on one of these exits
 * {@value Main#MISUSED}, the same as arguments the parser itself refused.
 */
public final class CommandError extends RuntimeException {

    public CommandError(String message) {
        super(message);
    }
}

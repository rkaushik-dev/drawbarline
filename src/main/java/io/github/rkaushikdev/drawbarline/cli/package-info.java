/**
 * The command line the general office actually uses.
 *
 * <p>One root command with one database option, and every command opening its own unit of work and
 * committing only at the end. A refusal about the books exits 1 and arguments that were wrong exit
 * 2, each with the refusal and its context on standard error rather than a stack trace. Every
 * command group is named on the root command, so a group is either listed there or does not exist.
 */
package io.github.rkaushikdev.drawbarline.cli;

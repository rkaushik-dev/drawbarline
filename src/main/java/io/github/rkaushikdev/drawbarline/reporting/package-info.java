/**
 * Running a report against the books.
 *
 * <p>The command line and the HTTP interface want the same reports, and each one is the same three
 * steps: load the registers, run the computation, lay it out. Those steps live here once, beside
 * the service layer, so neither interface can forget that the demurrage report needs the tariff in
 * force at the end of the month rather than at the start. The layouts know nothing about a database
 * and the repositories nothing about a page.
 */
package io.github.rkaushikdev.drawbarline.reporting;

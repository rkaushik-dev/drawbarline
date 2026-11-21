package io.github.rkaushikdev.drawbarline.cli;

import io.github.rkaushikdev.drawbarline.export.Export;
import io.github.rkaushikdev.drawbarline.persistence.UnitOfWork;
import io.github.rkaushikdev.drawbarline.settlement.Period;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * The month close, the statements, the audit trail and the export.
 *
 * <p>The close is the one command in here that changes a lot at once, so it prints what it did
 * rather than saying nothing and exiting zero. A clerk running it at the start of the month wants to
 * see the car hire, the reclaims, the demurrage and the statements, and wants to see them before
 * anybody sends them out.
 *
 * <p>A dry run does everything except commit. That is not a separate code path, it is the same run
 * with the commit left off, which is the only kind of dry run worth having.
 */
final class CloseCommands {

    private CloseCommands() {}

    /** Write a whole month as JSON, for the auditors. */
    @Command(name = "export", description = "Write a whole month as json, for the auditors.")
    static final class ExportMonth implements Main.Action {

        @ParentCommand
        private Main main;

        @Parameters(paramLabel = "MONTH")
        private String month;

        @Option(
                names = "--out",
                paramLabel = "FILE",
                converter = Readers.Target.class,
                description = "Where to write it. Standard output by default.")
        private Path out;

        @Override
        public Integer call() {
            Period period = Readers.aMonth(month);
            Map<String, Object> bundle;
            try (UnitOfWork work = main.work()) {
                bundle = Export.month(work, period);
            }
            String text = JsonText.of(bundle) + "\n";
            if (out == null) {
                main.write(text);
                return Main.OK;
            }
            try {
                Files.writeString(out, text, StandardCharsets.UTF_8);
            } catch (IOException unwritable) {
                throw new CommandError(Readers.name(out) + " cannot be written");
            }
            String counted = Export.counts(bundle).entrySet().stream()
                    .map(entry -> entry.getValue() + " " + entry.getKey())
                    .collect(Collectors.joining(", "));
            main.out("wrote " + period + " to " + Readers.name(out) + ": " + counted);
            return Main.OK;
        }
    }
}

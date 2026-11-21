package io.github.rkaushikdev.drawbarline.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The layout an export is written in, pinned so that two exports of one month are the same bytes. */
class JsonTextTest {

    @Test
    void anObjectIsIndentedWithItsKeysSorted() {
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("version", 1);
        bundle.put("closed", true);
        bundle.put("events", List.of());
        bundle.put("audit", List.of(Map.of("actor", "rjs", "cents", 46300L)));
        bundle.put("taken", Map.of());
        bundle.put("road", null);
        assertThat(JsonText.of(bundle))
                .isEqualTo(String.join(
                        "\n",
                        "{",
                        "  \"audit\": [",
                        "    {",
                        "      \"actor\": \"rjs\",",
                        "      \"cents\": 46300",
                        "    }",
                        "  ],",
                        "  \"closed\": true,",
                        "  \"events\": [],",
                        "  \"road\": null,",
                        "  \"taken\": {},",
                        "  \"version\": 1",
                        "}"));
    }

    @Test
    void textIsWrittenInAscii() {
        assertThat(JsonText.of(Arrays.asList("say \"no\"\\", "tab\there\n", "caf\u00e9 \u20ac", "\ud83d\ude82", false)))
                .isEqualTo("[\n  \"say \\\"no\\\"\\\\\",\n  \"tab\\there\\n\",\n  \"caf\\u00e9 \\u20ac\",\n"
                        + "  \"\\ud83d\\ude82\",\n  false\n]");
    }

    @Test
    void aValueThatIsNotPlainDataIsRefused() {
        assertThatThrownBy(() -> JsonText.of(List.of(1.5))).isInstanceOf(CommandError.class);
    }
}

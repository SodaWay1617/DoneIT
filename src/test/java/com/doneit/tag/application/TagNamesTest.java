package com.doneit.tag.application;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TagNamesTest {
    @Test void normalizesAndDeduplicatesWithoutLosingDisplayName() {
        assertThat(TagNames.normalize(List.of(" #Prime ", "prime", "Ｐｒｉｍｅ", "Здоровый   сон")))
                .containsExactly("Prime", "Здоровый сон");
        assertThat(TagNames.normalize((List<String>) null)).isEmpty();
    }
    @Test void rejectsInvalidNamesAndExcessiveLabels() {
        for (String name : List.of("", "#", "a".repeat(41), "<script>", "a,b", "a/b"))
            assertThatThrownBy(() -> TagNames.normalize(name)).isInstanceOf(IllegalArgumentException.class);
        assertThat(TagNames.valid(Collections.nCopies(21, "Prime"))).isFalse();
        assertThat(TagNames.valid(List.of("я".repeat(40), "финансовая-грамотность", "Prime_2026"))).isTrue();
    }
}

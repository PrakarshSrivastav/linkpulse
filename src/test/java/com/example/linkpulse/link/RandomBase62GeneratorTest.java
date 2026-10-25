package com.example.linkpulse.link;

import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class RandomBase62GeneratorTest {

    private final RandomBase62Generator generator = new RandomBase62Generator();

    @Test
    void generatesSevenBase62Characters() {
        for (int i = 0; i < 1_000; i++) {
            assertThat(generator.generate()).matches("[0-9A-Za-z]{7}");
        }
    }

    @Test
    void codesAreEffectivelyUnique() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 100_000; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(100_000);
    }
}
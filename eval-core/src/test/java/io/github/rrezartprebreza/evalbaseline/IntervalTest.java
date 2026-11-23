package io.github.rrezartprebreza.evalbaseline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IntervalTest {

    @Test
    void wilsonForNineOfTen() {
        Interval i = Interval.wilson95(9, 10);
        assertEquals(0.596, i.low(), 0.001);
        assertEquals(0.982, i.high(), 0.001);
    }

    @Test
    void wilsonForZeroPassesStartsAtZero() {
        Interval i = Interval.wilson95(0, 10);
        assertEquals(0.0, i.low(), 1e-9);
        assertEquals(0.278, i.high(), 0.001);
    }

    @Test
    void rejectsMorePassesThanRuns() {
        assertThrows(IllegalArgumentException.class, () -> Interval.wilson95(11, 10));
    }
}

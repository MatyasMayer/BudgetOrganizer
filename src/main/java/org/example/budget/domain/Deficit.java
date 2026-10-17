package org.example.budget.domain;

import java.util.Objects;

/** An Uncovered Deficit: a completed Month whose Leftover is still negative. */
public record Deficit(Month month, Money leftover) {

    public Deficit {
        Objects.requireNonNull(month);
        Objects.requireNonNull(leftover);
    }
}

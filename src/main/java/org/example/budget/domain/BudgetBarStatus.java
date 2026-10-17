package org.example.budget.domain;

/** The colour of a limited Category's progress bar on the Month dashboard. */
public enum BudgetBarStatus {
    /** Spent below 80 % of the effective limit. */
    GREEN,
    /** Spent from 80 % up to 100 % of the effective limit. */
    ORANGE,
    /** Spent more than the effective limit. */
    RED
}

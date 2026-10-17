package org.example.budget.domain;

/** A Budget Limit change the service refuses. The {@link Problem} lets the UI show a translated message. */
public class InvalidBudgetLimitException extends RuntimeException {

    public enum Problem {
        UNKNOWN_CATEGORY,
        /** Budget Limits exist for Expense Categories only. */
        INCOME_CATEGORY,
        /** A Budget Limit must be more than zero; clearing it is how a Category gets no limit. */
        NOT_POSITIVE
    }

    private final Problem problem;

    public InvalidBudgetLimitException(Problem problem) {
        super(problem.name());
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}

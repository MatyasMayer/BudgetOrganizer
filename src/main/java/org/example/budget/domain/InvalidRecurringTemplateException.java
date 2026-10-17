package org.example.budget.domain;

/** A Recurring Template the service refuses to store. The {@link Problem} lets the UI show a translated message. */
public class InvalidRecurringTemplateException extends RuntimeException {

    public enum Problem {
        UNKNOWN_CATEGORY,
        CATEGORY_OF_OTHER_TYPE,
        /** A template repeats a payment; a Refund is never recurring, so the amount must be above zero. */
        AMOUNT_NOT_POSITIVE,
        /** The day of month must be 1 to 31; days beyond a shorter Month's length fall on its last day. */
        DAY_OUT_OF_RANGE,
        END_BEFORE_START
    }

    private final Problem problem;

    public InvalidRecurringTemplateException(Problem problem) {
        super(problem.name());
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}

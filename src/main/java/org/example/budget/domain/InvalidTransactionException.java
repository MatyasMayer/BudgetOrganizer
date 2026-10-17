package org.example.budget.domain;

/** A Transaction the service refuses to store. The {@link Problem} lets the UI show a translated message. */
public class InvalidTransactionException extends RuntimeException {

    public enum Problem {
        UNKNOWN_CATEGORY,
        CATEGORY_OF_OTHER_TYPE,
        ZERO_AMOUNT,
        /** Only an Expense may be negative (a Refund); money coming back is never a negative Income. */
        NEGATIVE_INCOME,
        /** Only an Expense can be paid from Savings; money taken out of Savings is a From Savings Income. */
        SAVINGS_FUNDED_INCOME,
        UNKNOWN_TRANSACTION
    }

    private final Problem problem;

    public InvalidTransactionException(Problem problem) {
        super(problem.name());
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}

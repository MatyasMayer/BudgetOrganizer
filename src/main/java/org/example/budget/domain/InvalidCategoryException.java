package org.example.budget.domain;

/** A Category change the service refuses. The {@link Problem} lets the UI show a translated message. */
public class InvalidCategoryException extends RuntimeException {

    public enum Problem {
        BLANK_NAME,
        /** Another Category of the same type already has this name (ignoring case). */
        DUPLICATE_NAME,
        UNKNOWN_CATEGORY,
        /** From Savings is needed by the Savings rules and can't be deleted or merged away. */
        SYSTEM_CATEGORY,
        /** The Category has Transactions, so deleting it needs a replacement to move them to. */
        REPLACEMENT_REQUIRED,
        REPLACEMENT_OF_OTHER_TYPE,
        REPLACEMENT_IS_SAME
    }

    private final Problem problem;

    public InvalidCategoryException(Problem problem) {
        super(problem.name());
        this.problem = problem;
    }

    public Problem problem() {
        return problem;
    }
}

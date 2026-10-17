package org.example.budget.domain;

/**
 * The currencies an amount can be entered in. CZK is the Base Currency; any other one is an Original Amount
 * converted with the ČNB Rate. Codes are ISO 4217, as used by ČNB.
 */
public enum Currency {
    CZK,
    EUR;

    public String code() {
        return name();
    }

    public boolean isBaseCurrency() {
        return this == CZK;
    }
}

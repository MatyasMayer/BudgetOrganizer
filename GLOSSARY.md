# BudgetOrganizer

A personal, single-user desktop app for tracking where money comes from and goes, planning monthly spending per category, and seeing how much is left over and saved.

## Money in and out

**Transaction**:
A single recorded movement of money on a specific date: either an Income or an Expense, with exactly one Category.
_Avoid_: Entry, record, item, payment

**Income**:
A Transaction that brings money in (salary, gift, money taken From Savings).
_Avoid_: Earning, revenue, inflow

**Expense**:
A Transaction that sends money out.
_Avoid_: Outcome, outgoing, spending (as a noun for a single item), cost

**Refund**:
Money returned for an earlier purchase, recorded as a negative Expense in the original purchase's Category, not as an Income.
_Avoid_: Return, reimbursement

**Category**:
A user-editable label grouping Transactions of one kind; Income and Expense each have their own list. Categories are flat, with no subcategories.
_Avoid_: Type, group, tag

**Note**:
Optional free text on a Transaction, also used for the merchant name (e.g. "Steam – Hades II").
_Avoid_: Description, memo, merchant

**Recurring Template**:
A definition of a Transaction that repeats monthly or yearly (rent, salary, subscriptions), from which the app generates the actual Transactions automatically. Changes apply only to future occurrences; a template is ended with an end date, never deleted.
_Avoid_: Recurring transaction, standing order, subscription (as the general term)

**Subscription**:
An Expense Recurring Template for a paid service (Spotify, Steam, Netflix).
_Avoid_: Membership

## Currency

**Base Currency**:
CZK. Every amount is ultimately counted in CZK for totals, charts and Savings.
_Avoid_: Home currency, main currency

**Original Amount**:
The amount and currency as paid, when the currency isn't CZK (e.g. €19.99).
_Avoid_: Foreign amount, source amount

**ČNB Rate**:
The Czech National Bank's official exchange rate for the Transaction's date, used to convert an Original Amount to CZK.
_Avoid_: Exchange rate (unqualified), FX rate

**Charged Amount**:
The CZK amount the bank actually took for a foreign-currency Transaction. When present, it overrides the ČNB Rate conversion.
_Avoid_: Real amount, final amount

**Provisional Rate**:
A cached older ČNB Rate used temporarily because the rate for the Transaction's date couldn't be fetched yet.

## Planning

**Month**:
A calendar month (1st to last day). The unit for limits, Leftover, and comparisons.
_Avoid_: Period, cycle, pay period

**Budget Limit**:
The maximum amount you plan to spend in one Expense Category in a Month.
_Avoid_: Budget (alone), cap, allowance

**Default Limit**:
The Budget Limit that applies to a Category in every Month unless overridden.

**Limit Override**:
A Budget Limit set for one specific Month that replaces the Default Limit for that Month.

**Fixed Spending**:
Expenses generated from Recurring Templates. Their amounts for a Month are known in advance.

**Variable Spending**:
All other Expenses, excluding Savings-Funded Expenses. Only this part is extrapolated when projecting.
_Avoid_: Discretionary spending, daily spending

**Pace**:
A projection of a Month's total spending (per Category or overall): Variable Spending extrapolated from the days passed so far, plus that Month's full Fixed Spending.
_Avoid_: Forecast, prediction, trend

**Same-Point Comparison**:
Comparing the current unfinished Month with the previous Month up to the same day of the month.

## Saving

**Leftover**:
A Month's total Income minus its total Expenses, not counting Savings-Funded Expenses.
_Avoid_: Balance, remainder, unspent money, net

**Savings**:
The single pot of money: Opening Savings, plus all positive Leftovers, minus From Savings Incomes, minus Savings-Funded Expenses. There are no manual deposits; saving happens only through Leftover. Savings may go negative.
_Avoid_: Savings account, reserve, fund, deposit

**Opening Savings**:
The amount already saved before the first Month tracked in the app.
_Avoid_: Starting balance, initial savings

**Savings-Funded Expense**:
An Expense paid directly out of Savings (e.g. a PC bought with saved money). It still belongs to its Category but affects neither its Month's Leftover, Budget Limits, nor Pace.
_Avoid_: Withdrawal, savings expense

**Deficit**:
A negative Leftover. It never reduces Savings by itself; it stays flagged until a Covering Income is added to that Month, however long that takes.
_Avoid_: Loss, overspend, debt

**Uncovered Deficit**:
A Deficit that hasn't been cancelled out by a Covering Income yet.

**Covering Income**:
An Income added to a Month to cancel out its Deficit.

**From Savings**:
The special Income Category for money taken out of Savings. It reduces Savings and is not counted as earnings in the Savings Rate.
_Avoid_: Withdrawal (as a Category name)

**Savings Rate**:
The share of a Month's Income, excluding From Savings, that became Leftover.

**Savings Goal**:
A named target amount with an optional deadline, tracked against Savings (e.g. "New PC – 40,000 CZK by June").
_Avoid_: Target, wish

package org.example.budget.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

/**
 * Renders Transactions as CSV that Czech-locale Excel opens correctly: semicolon-separated with CRLF line ends and
 * a leading byte order mark (so writing the text as UTF-8 gives "UTF-8 with BOM"). Amounts use a decimal comma.
 * <p>
 * Columns: date, type, Category, CZK amount, Original Amount, currency, ČNB Rate, Charged Amount, Savings-Funded
 * (1 or 0), Note. A text that Excel would run as a formula (starting with {@code = + - @}) gets a leading
 * apostrophe.
 */
public final class TransactionCsv {

    public static final int COLUMNS = 10;

    private static final String BYTE_ORDER_MARK = "﻿";
    private static final char SEPARATOR = ';';
    private static final String LINE_END = "\r\n";

    private TransactionCsv() {
    }

    /** The header row (one title per column) and a row per Transaction, in the order given. */
    public static String render(List<String> header, List<Transaction> transactions,
                                LongFunction<String> categoryName) {
        if (header.size() != COLUMNS) {
            throw new IllegalArgumentException("The header needs " + COLUMNS + " titles, not " + header.size());
        }
        StringBuilder csv = new StringBuilder(BYTE_ORDER_MARK);
        row(csv, header);
        for (Transaction transaction : transactions) {
            row(csv, fields(transaction, categoryName));
        }
        return csv.toString();
    }

    private static List<String> fields(Transaction transaction, LongFunction<String> categoryName) {
        TransactionDetails details = transaction.details();
        List<String> fields = new ArrayList<>(COLUMNS);
        fields.add(details.date().toString());
        fields.add(details.type().name());
        fields.add(text(categoryName.apply(details.categoryId())));
        fields.add(number(details.amount().halere(), 2));
        if (details.conversion().isPresent()) {
            Conversion conversion = details.conversion().get();
            fields.add(number(conversion.originalAmount().cents(), 2));
            fields.add(conversion.originalAmount().currency().code());
            fields.add(conversion.cnbRate().czkPerUnit().toPlainString().replace('.', ','));
            fields.add(conversion.chargedAmount().map(charged -> number(charged.halere(), 2)).orElse(""));
        } else {
            fields.addAll(List.of("", "", "", ""));
        }
        fields.add(details.savingsFunded() ? "1" : "0");
        fields.add(text(details.note()));
        return fields;
    }

    private static String number(long hundredths, int scale) {
        return BigDecimal.valueOf(hundredths, scale).toPlainString().replace('.', ',');
    }

    /** Defuses a spreadsheet formula; quoting for the separator happens when the row is written. */
    private static String text(String value) {
        if (!value.isEmpty() && "=+-@\t\r".indexOf(value.charAt(0)) >= 0) {
            return "'" + value;
        }
        return value;
    }

    private static void row(StringBuilder csv, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                csv.append(SEPARATOR);
            }
            csv.append(quoted(fields.get(i)));
        }
        csv.append(LINE_END);
    }

    private static String quoted(String field) {
        boolean needsQuotes = field.indexOf(SEPARATOR) >= 0 || field.indexOf('"') >= 0
                || field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0;
        return needsQuotes ? '"' + field.replace("\"", "\"\"") + '"' : field;
    }
}

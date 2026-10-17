package org.example.budget.currency;

import org.example.budget.domain.CnbRate;
import org.example.budget.domain.Currency;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One ČNB daily fixing, parsed from the Czech TXT file ("kurzy devizového trhu"):
 * <pre>
 * 16.10.2026 #200
 * země|měna|množství|kód|kurz
 * EMU|euro|1|EUR|24,325
 * Japonsko|jen|100|JPY|13,686
 * </pre>
 * The rate is CZK per {@code množství} units and uses a decimal comma. Rows are looked up by code, never
 * by position, and the fixing date is always read from line 1, since ČNB answers a weekend, holiday or
 * future date with the latest earlier fixing.
 */
public final class CnbDailyRates {

    private static final DateTimeFormatter HEADER_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final LocalDate validFor;
    private final Map<String, BigDecimal> czkPerUnit;

    private CnbDailyRates(LocalDate validFor, Map<String, BigDecimal> czkPerUnit) {
        this.validFor = validFor;
        this.czkPerUnit = Map.copyOf(czkPerUnit);
    }

    public static CnbDailyRates parse(String text) {
        List<String> lines = text.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
        if (lines.size() < 2) {
            throw new CnbFormatException("Too short for a ČNB daily rates file");
        }
        LocalDate date = parseDate(lines.get(0));
        Map<String, BigDecimal> rates = new HashMap<>();
        for (String line : lines.subList(2, lines.size())) {
            String[] fields = line.split("\\|");
            if (fields.length != 5) {
                throw new CnbFormatException("Unexpected ČNB rate line: " + line);
            }
            rates.put(fields[3].strip(), perUnit(fields[4], fields[2], line));
        }
        return new CnbDailyRates(date, rates);
    }

    /** The date of the fixing these rates belong to. */
    public LocalDate validFor() {
        return validFor;
    }

    /** CZK for one unit of the currency with this ISO 4217 code. */
    public Optional<BigDecimal> czkPerUnit(String code) {
        return Optional.ofNullable(czkPerUnit.get(code));
    }

    public Optional<CnbRate> rate(Currency currency) {
        return czkPerUnit(currency.code()).map(rate -> new CnbRate(currency, validFor, rate));
    }

    public int currencyCount() {
        return czkPerUnit.size();
    }

    // "16.10.2026 #200": ČNB documents two spaces before '#', the real file has one.
    private static LocalDate parseDate(String header) {
        String[] parts = header.split("\\s+");
        try {
            return LocalDate.parse(parts[0], HEADER_DATE);
        } catch (DateTimeParseException e) {
            throw new CnbFormatException("Unexpected ČNB header line: " + header, e);
        }
    }

    private static BigDecimal perUnit(String rate, String amount, String line) {
        try {
            BigDecimal czk = new BigDecimal(rate.strip().replace(',', '.'));
            BigDecimal units = new BigDecimal(amount.strip());
            return czk.divide(units, MathContext.DECIMAL64).stripTrailingZeros();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new CnbFormatException("Unexpected ČNB rate line: " + line, e);
        }
    }
}

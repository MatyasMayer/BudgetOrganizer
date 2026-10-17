package org.example.budget.currency;

import org.example.budget.domain.CnbRate;
import org.example.budget.domain.Currency;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The ČNB Rate source, with the HTTP download replaced by the saved sample. Never touches the network. */
class CnbRateSourceTest {

    private static final String BASE_URL = "https://cnb.example/denni_kurz.txt";

    private final List<URI> requested = new ArrayList<>();

    @Test
    void asksForTheDateInTheCzechFormat() {
        source(uri -> sample()).fetch(Currency.EUR, LocalDate.of(2026, 10, 16));

        assertEquals(List.of(URI.create(BASE_URL + "?date=16.10.2026")), requested);
    }

    @Test
    void returnsTheEuroRateOfTheFixing() {
        Optional<CnbRate> rate = source(uri -> sample()).fetch(Currency.EUR, LocalDate.of(2026, 10, 16));

        assertEquals(Optional.of(new CnbRate(Currency.EUR, LocalDate.of(2026, 10, 16), new BigDecimal("24.325"))),
                rate);
    }

    @Test
    void aWeekendDateGetsTheFixingDateChosenByCnb() {
        Optional<CnbRate> rate = source(uri -> sample()).fetch(Currency.EUR, LocalDate.of(2026, 10, 18));

        assertEquals(LocalDate.of(2026, 10, 16), rate.orElseThrow().validFor());
    }

    @Test
    void aFailedDownloadIsReportedAsUnavailable() {
        Optional<CnbRate> rate = source(uri -> {
            throw new IOException("Connection reset");
        }).fetch(Currency.EUR, LocalDate.of(2026, 10, 16));

        assertEquals(Optional.empty(), rate);
    }

    @Test
    void anUnreadableResponseIsReportedAsUnavailable() {
        Optional<CnbRate> rate = source(uri -> "<html>Maintenance</html>")
                .fetch(Currency.EUR, LocalDate.of(2026, 10, 16));

        assertEquals(Optional.empty(), rate);
    }

    private CnbRateSource source(CnbRateSource.Download download) {
        return new CnbRateSource(BASE_URL, uri -> {
            requested.add(uri);
            return download.text(uri);
        });
    }

    private static String sample() {
        return CnbDailyRatesTest.fixture(CnbDailyRatesTest.SAMPLE);
    }
}

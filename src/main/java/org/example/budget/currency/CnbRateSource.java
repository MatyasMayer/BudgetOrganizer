package org.example.budget.currency;

import org.example.budget.domain.CnbRate;
import org.example.budget.domain.Currency;
import org.example.budget.domain.RateSource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Downloads ČNB's daily rates TXT file for a date. Any failure (offline, timeout, HTTP error, unexpected
 * content) is reported as "unavailable". Caching is the domain's job, so each call is one request.
 */
public class CnbRateSource implements RateSource {

    /** The Czech TXT file; its path has moved during cnb.cz redesigns, so it can be overridden. */
    public static final String DEFAULT_URL = "https://www.cnb.cz/cs/financni-trhy/devizovy-trh/"
            + "kurzy-devizoveho-trhu/kurzy-devizoveho-trhu/denni_kurz.txt";

    /** System property overriding {@link #DEFAULT_URL}. */
    public static final String URL_PROPERTY = "budget.cnbUrl";

    private static final System.Logger LOG = System.getLogger(CnbRateSource.class.getName());
    private static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** Fetches a URL as text. */
    @FunctionalInterface
    public interface Download {
        String text(URI uri) throws IOException, InterruptedException;
    }

    private final String baseUrl;
    private final Download download;

    public CnbRateSource(String baseUrl, Download download) {
        this.baseUrl = baseUrl;
        this.download = download;
    }

    /** The real ČNB service over HTTP, at {@link #DEFAULT_URL} unless overridden by {@value #URL_PROPERTY}. */
    public static CnbRateSource overHttp() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return new CnbRateSource(System.getProperty(URL_PROPERTY, DEFAULT_URL), uri -> {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(uri).timeout(TIMEOUT).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new IOException("ČNB answered HTTP " + response.statusCode());
            }
            return response.body();
        });
    }

    @Override
    public Optional<CnbRate> fetch(Currency currency, LocalDate date) {
        URI uri = URI.create(baseUrl + "?date=" + date.format(QUERY_DATE));
        try {
            return CnbDailyRates.parse(download.text(uri)).rate(currency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | CnbFormatException e) {
            LOG.log(System.Logger.Level.WARNING, "ČNB Rate unavailable from {0}: {1}", uri, e.toString());
            return Optional.empty();
        }
    }
}

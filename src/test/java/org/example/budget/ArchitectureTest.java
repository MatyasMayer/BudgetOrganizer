package org.example.budget;

import org.example.budget.domain.Money;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Scans compiled classes: a class file names every type it uses in its constant pool,
 * so a forbidden package appearing there means a dependency on it.
 */
class ArchitectureTest {

    private static final List<String> OUTSIDE_DOMAIN = List.of(
            "org/example/budget/ui/",
            "org/example/budget/storage/",
            "org/example/budget/currency/",
            "javafx/",
            "java/sql/");

    @Test
    void theDomainDependsOnNothingInUiStorageOrCurrency() throws Exception {
        assertEquals(List.of(), classesIn("domain", OUTSIDE_DOMAIN));
    }

    @Test
    void theScanDetectsARealDependency() throws Exception {
        assertFalse(classesIn("storage", List.of("org/example/budget/domain/")).isEmpty());
    }

    /** Classes in the given layer whose bytecode references any of the forbidden packages. */
    private static List<String> classesIn(String layer, List<String> forbidden) throws IOException, URISyntaxException {
        Path classesRoot = Path.of(Money.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path layerDir = classesRoot.resolve("org/example/budget").resolve(layer);
        try (Stream<Path> files = Files.walk(layerDir)) {
            return files
                    .filter(file -> file.toString().endsWith(".class"))
                    .filter(file -> referencesAny(file, forbidden))
                    .map(file -> classesRoot.relativize(file).toString())
                    .toList();
        }
    }

    private static boolean referencesAny(Path classFile, List<String> packages) {
        try {
            String bytes = new String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1);
            return packages.stream().anyMatch(bytes::contains);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

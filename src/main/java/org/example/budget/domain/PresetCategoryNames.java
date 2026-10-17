package org.example.budget.domain;

/** The name of a preset Category in a language, e.g. "Z úspor" for From Savings in Czech. */
@FunctionalInterface
public interface PresetCategoryNames {

    String name(PresetCategory preset, Language language);
}

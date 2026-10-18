package org.example.budget.ui;

import java.nio.file.Path;

/** Copies the database into a folder; the UI doesn't need to know how. */
@FunctionalInterface
public interface DatabaseBackup {

    /** Writes a timestamped copy of the database into the folder and returns the new file. */
    Path to(Path folder);
}

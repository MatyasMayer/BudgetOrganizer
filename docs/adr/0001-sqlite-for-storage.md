# SQLite for storage, not PostgreSQL

The app is single-user and runs locally on one machine. We chose an embedded SQLite file (`budget.db`) over PostgreSQL, even though PostgreSQL and pgAdmin are already installed. PostgreSQL needs a running server for one user's data. SQLite needs no setup, and a backup is a copy of one file. Reports still use real SQL, so moving to PostgreSQL later is possible if multi-device sync is ever needed.

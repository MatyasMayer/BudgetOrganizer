# Plain JDBC with hand-written SQL, no ORM

Storage uses `sqlite-jdbc` with hand-written SQL and numbered migration scripts (`V1__init.sql`, …), not Hibernate/JPA. The reports (spending per Category per Month, year totals, Same-Point Comparison) are aggregate queries that are clearer as SQL than as ORM mappings. The project is also meant for learning SQL. The data model is small enough that the mapping boilerplate stays manageable.

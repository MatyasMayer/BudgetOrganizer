-- depends on V2; sorts before it lexically, so this checks numeric ordering
CREATE TABLE c (id INTEGER PRIMARY KEY);
INSERT INTO c (id) SELECT id FROM b;

CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

create table client (
    id uuid NOT NULL,
    email varchar(256) NOT NULL,
    password varchar(512) NOT NULL,
    PRIMARY KEY (id);
);

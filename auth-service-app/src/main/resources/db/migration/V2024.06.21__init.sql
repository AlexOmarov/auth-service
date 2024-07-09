CREATE TABLE shedlock(
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);

CREATE TABLE client (
    id uuid NOT NULL,
    login varchar(256) NOT NULL,
    password varchar(512) NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE revoked_authorization (
    id uuid NOT NULL,
    token varchar(512) NOT NULL,
    expiration timestamptz NOT NULL,
    PRIMARY KEY (id)
);

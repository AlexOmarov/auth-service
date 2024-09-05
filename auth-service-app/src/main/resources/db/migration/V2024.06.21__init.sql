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
	password varchar(256) NOT NULL,
	email varchar(128) NOT NULL,
	CONSTRAINT client_pk PRIMARY KEY (id)
);

CREATE TABLE authentication_provider (
	id smallint NOT NULL,
	code varchar(64) NOT NULL,
	CONSTRAINT authentication_provider_pk PRIMARY KEY (id)
);

CREATE TABLE client_authentication_provider_info (
	id uuid NOT NULL,
	authentication_provider_id smallint NOT NULL,
	client_id uuid NOT NULL,
	CONSTRAINT client_authentication_provider_pk PRIMARY KEY (id)
);

CREATE TABLE revoked_token (
	id uuid NOT NULL,
	token varchar(128) NOT NULL,
	client_id uuid NOT NULL,
	CONSTRAINT revoked_token_pk PRIMARY KEY (id)
);

CREATE TABLE game_session (
	id uuid NOT NULL,
	name varchar(128) NOT NULL,
	game_session_status_id smallint NOT NULL,
	CONSTRAINT game_session_pk PRIMARY KEY (id)
);

CREATE TABLE game_session_status (
	id smallint NOT NULL,
	code varchar(64) NOT NULL,
	CONSTRAINT game_session_status_pk PRIMARY KEY (id)
);

CREATE TABLE chat_record (
	id uuid NOT NULL,
	message text NOT NULL,
	creation_datetime timestamptz NOT NULL,
	game_session_id uuid NOT NULL,
	hero_id uuid NOT NULL,
	CONSTRAINT chat_record_pk PRIMARY KEY (id)
);

CREATE TABLE hero (
	id uuid NOT NULL,
	name varchar(64) NOT NULL,
	client_id uuid NOT NULL,
	game_session_id uuid NOT NULL,
	CONSTRAINT hero_pk PRIMARY KEY (id)
);

ALTER TABLE client_authentication_provider_info ADD CONSTRAINT authentication_provider_fk FOREIGN KEY (authentication_provider_id)
REFERENCES authentication_provider (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE client_authentication_provider_info ADD CONSTRAINT client_fk FOREIGN KEY (client_id)
REFERENCES client (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE revoked_token ADD CONSTRAINT client_fk FOREIGN KEY (client_id)
REFERENCES client (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE game_session ADD CONSTRAINT game_session_status_fk FOREIGN KEY (game_session_status_id)
REFERENCES game_session_status (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE chat_record ADD CONSTRAINT game_session_fk FOREIGN KEY (game_session_id)
REFERENCES game_session (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE hero ADD CONSTRAINT client_fk FOREIGN KEY (client_id)
REFERENCES client (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE hero ADD CONSTRAINT game_session_fk FOREIGN KEY (game_session_id)
REFERENCES game_session (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE chat_record ADD CONSTRAINT hero_fk FOREIGN KEY (hero_id)
REFERENCES hero (id) MATCH FULL
ON DELETE RESTRICT ON UPDATE CASCADE;

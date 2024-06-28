CREATE TABLE revoked_authorization (
    id uuid NOT NULL,
    access varchar(512) NOT NULL,
    refresh varchar(512) NOT NULL,
    PRIMARY KEY (id)
);

insert into revoked_authorization values('45f5af68-9c53-4875-b63b-1caa01e7dc25', 'a7b2f1b1-d51a-4d9e-9ebf-54ad334d3ae0', '535dc2a2-156f-4b5e-a673-ba298fd039e4');

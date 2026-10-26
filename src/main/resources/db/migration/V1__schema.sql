CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE professional (
    id          text PRIMARY KEY,
    name        text NOT NULL,
    work_start  time NOT NULL,
    work_end    time NOT NULL,
    lunch_start time NOT NULL,
    lunch_end   time NOT NULL,
    CHECK (work_start < work_end),
    CHECK (lunch_start < lunch_end)
);

CREATE TABLE professional_weekday (
    professional_id text     NOT NULL REFERENCES professional (id),
    iso_weekday     smallint NOT NULL CHECK (iso_weekday BETWEEN 1 AND 7),
    PRIMARY KEY (professional_id, iso_weekday)
);

CREATE TABLE service (
    id               text PRIMARY KEY,
    name             text NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes > 0),
    price            numeric(10, 2) NOT NULL
);

CREATE TABLE professional_service (
    professional_id text NOT NULL REFERENCES professional (id),
    service_id      text NOT NULL REFERENCES service (id),
    PRIMARY KEY (professional_id, service_id)
);

CREATE TABLE holiday (
    date date PRIMARY KEY,
    name text NOT NULL
);

CREATE TABLE absence (
    professional_id text NOT NULL REFERENCES professional (id),
    date            date NOT NULL,
    reason          text NOT NULL,
    PRIMARY KEY (professional_id, date)
);

CREATE TABLE booking (
    id              uuid PRIMARY KEY,
    professional_id text NOT NULL REFERENCES professional (id),
    service_id      text NOT NULL REFERENCES service (id),
    patient_name    text NOT NULL,
    starts_at       timestamptz NOT NULL,
    ends_at         timestamptz NOT NULL,
    status          text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELLED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    cancelled_at    timestamptz,
    CHECK (ends_at > starts_at),
    -- Dois atendimentos ativos do mesmo profissional nao podem ter intervalos que se sobrepoem.
    -- '[)' deixa um atendimento comecar exatamente quando outro termina; o WHERE libera o horario das canceladas.
    CONSTRAINT booking_no_overlap EXCLUDE USING gist (
        professional_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status = 'ACTIVE')
);

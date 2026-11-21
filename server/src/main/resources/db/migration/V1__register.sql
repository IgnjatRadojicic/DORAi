-- Register, last scan and AI suggestions.
-- Every register field carries its provenance in four sibling columns: <field>_label, <field>_updated_at,
-- <field>_citation (jsonb) and <field>_reason. Lists keep their order in a position column.
-- References the validator reports on (contract to provider, service to function) have no foreign key,
-- so a broken register can still be stored and shown with its validation issues.

-- pgvector is enabled per database; the regulation tables that use it come in a later migration.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE financial_entities (
    lei     text PRIMARY KEY,
    name    text        NOT NULL,
    country text        NOT NULL,
    as_of   timestamptz NOT NULL
);

CREATE TABLE providers (
    id       text PRIMARY KEY,
    position int  NOT NULL,
    name     text NOT NULL,
    lei text, lei_label text NOT NULL, lei_updated_at timestamptz NOT NULL, lei_citation jsonb, lei_reason text,
    headquarters_country text, headquarters_country_label text NOT NULL,
    headquarters_country_updated_at timestamptz NOT NULL, headquarters_country_citation jsonb, headquarters_country_reason text
);

CREATE TABLE business_functions (
    id          text PRIMARY KEY,
    position    int     NOT NULL,
    name        text    NOT NULL,
    is_critical boolean NOT NULL
);

CREATE TABLE contracts (
    id          text PRIMARY KEY,
    position    int  NOT NULL,
    provider_id text NOT NULL,
    start_date  date NOT NULL,
    end_date    date,
    annual_cost text, annual_cost_label text NOT NULL, annual_cost_updated_at timestamptz NOT NULL,
    annual_cost_citation jsonb, annual_cost_reason text,
    currency    text
);

CREATE TABLE contract_services (
    id          text PRIMARY KEY,
    contract_id text NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    position    int  NOT NULL,
    service_type text, service_type_label text NOT NULL, service_type_updated_at timestamptz NOT NULL,
    service_type_citation jsonb, service_type_reason text,
    function_id text, function_id_label text NOT NULL, function_id_updated_at timestamptz NOT NULL,
    function_id_citation jsonb, function_id_reason text,
    data_location text, data_location_label text NOT NULL, data_location_updated_at timestamptz NOT NULL,
    data_location_citation jsonb, data_location_reason text,
    resource_ids text[] NOT NULL DEFAULT '{}'
);

CREATE INDEX contract_services_contract ON contract_services (contract_id);

CREATE TABLE resources (
    id                  text PRIMARY KEY,
    position            int   NOT NULL,
    name                text  NOT NULL,
    type                text  NOT NULL,
    provider            text  NOT NULL,
    location            text  NOT NULL,
    country             text,
    source              text  NOT NULL,
    change_action       text  NOT NULL,
    tags                jsonb NOT NULL DEFAULT '{}',
    service_type        text,
    redundancy          text  NOT NULL,
    previous_redundancy text,
    function_id         text,
    contract_id         text,
    monthly_cost        double precision,
    currency            text
);

CREATE TABLE cost_entries (
    resource_id text             NOT NULL,
    month       date             NOT NULL,
    position    int              NOT NULL,
    provider    text             NOT NULL,
    amount      double precision NOT NULL,
    currency    text             NOT NULL,
    PRIMARY KEY (resource_id, month)
);

CREATE TABLE suggestions (
    id            text PRIMARY KEY,
    seq           bigserial        NOT NULL,
    resource_id   text             NOT NULL,
    resource_name text             NOT NULL,
    kind          text             NOT NULL,
    value         text             NOT NULL,
    reason        text             NOT NULL,
    citations     jsonb            NOT NULL DEFAULT '[]',
    confidence    double precision,
    status        text             NOT NULL,
    created_at    timestamptz      NOT NULL,
    decided_at    timestamptz
);

CREATE INDEX suggestions_status ON suggestions (status);

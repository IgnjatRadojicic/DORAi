-- Register of Information columns beyond the named fields.
-- Each item keeps them in one jsonb map from CIR column code (for example "B_02.02.0100") to a register
-- field: {"value": ..., "provenance": {...}}. Templates with no item of their own (other group entities,
-- branches, subcontractors at rank 2 or more, the entity's definitions) are rows in roi_rows.

ALTER TABLE financial_entities ADD COLUMN roi jsonb NOT NULL DEFAULT '{}';
ALTER TABLE providers          ADD COLUMN roi jsonb NOT NULL DEFAULT '{}';
ALTER TABLE business_functions ADD COLUMN roi jsonb NOT NULL DEFAULT '{}';
ALTER TABLE contracts          ADD COLUMN roi jsonb NOT NULL DEFAULT '{}';
ALTER TABLE contract_services  ADD COLUMN roi jsonb NOT NULL DEFAULT '{}';

CREATE TABLE roi_rows (
    template text  NOT NULL,
    id       text  NOT NULL,
    position int   NOT NULL,
    fields   jsonb NOT NULL DEFAULT '{}',
    PRIMARY KEY (template, id)
);

-- What the scan saw beyond the region: copies in other countries and backups (DORA Art. 12).
ALTER TABLE resources ADD COLUMN replica_countries text[] NOT NULL DEFAULT '{}';
ALTER TABLE resources ADD COLUMN backup jsonb;

-- The RoI column a suggestion of kind RoiValue is for.
ALTER TABLE suggestions ADD COLUMN roi_column text;

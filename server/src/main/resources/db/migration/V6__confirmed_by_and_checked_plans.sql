-- Who confirmed each register value, and who decided each AI suggestion.
ALTER TABLE providers ADD COLUMN lei_confirmed_by text;
ALTER TABLE providers ADD COLUMN headquarters_country_confirmed_by text;
ALTER TABLE contracts ADD COLUMN annual_cost_confirmed_by text;
ALTER TABLE contract_services ADD COLUMN service_type_confirmed_by text;
ALTER TABLE contract_services ADD COLUMN function_id_confirmed_by text;
ALTER TABLE contract_services ADD COLUMN data_location_confirmed_by text;
ALTER TABLE suggestions ADD COLUMN decided_by text;

-- Terraform plans the firm checked, so the PDF report uses the firm's own plan and not the demo one.
CREATE TABLE checked_plans (
    id bigserial PRIMARY KEY,
    plan_json text NOT NULL,
    checked_by text NOT NULL,
    checked_at timestamptz NOT NULL
);

-- Open findings from Defender for Cloud and Security Hub, kept with the scanned resource.
ALTER TABLE resources ADD COLUMN alerts jsonb NOT NULL DEFAULT '[]';

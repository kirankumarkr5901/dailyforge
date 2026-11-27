-- Where a one-tap food came from: the list (starter foods and Settings), or saved from a
-- meal photo or a barcode scan. Nutrition shows the saved ones in their own section.
-- Foods that exist already can't be told apart, so they stay on the list.
ALTER TABLE protein_food ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'LIST';
ALTER TABLE protein_food ADD CONSTRAINT ck_protein_food_source CHECK (source IN ('LIST', 'PHOTO', 'BARCODE'));

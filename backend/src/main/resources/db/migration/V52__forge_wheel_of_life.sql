-- Forge 66 covers the Wheel of Life (owner request): four areas join the six stats,
-- Finance, Career, Relationships and Society. Every table that checks an area learns
-- them, and the area columns widen: 'RELATIONSHIPS' is 13 characters. The checks are
-- dropped first, since a column a check refers to can't change type on H2.
ALTER TABLE forge_quest_template DROP CONSTRAINT ck_forge_quest_template_area;
ALTER TABLE forge_quest DROP CONSTRAINT ck_forge_quest_area;
ALTER TABLE forge_quest DROP CONSTRAINT ck_forge_quest_custom;
ALTER TABLE forge_boss DROP CONSTRAINT ck_forge_boss_area;

ALTER TABLE forge_quest_template ALTER COLUMN area SET DATA TYPE VARCHAR(16);
ALTER TABLE forge_quest ALTER COLUMN area SET DATA TYPE VARCHAR(16);
ALTER TABLE forge_boss ALTER COLUMN area SET DATA TYPE VARCHAR(16);

ALTER TABLE forge_quest_template ADD CONSTRAINT ck_forge_quest_template_area CHECK (area IN
    ('FITNESS', 'FOCUS', 'HEALTH', 'MINDSET', 'LEARNING', 'SOCIAL', 'FINANCE', 'CAREER', 'RELATIONSHIPS', 'SOCIETY'));
ALTER TABLE forge_quest ADD CONSTRAINT ck_forge_quest_area CHECK (area IS NULL OR area IN
    ('FITNESS', 'FOCUS', 'HEALTH', 'MINDSET', 'LEARNING', 'SOCIAL', 'FINANCE', 'CAREER', 'RELATIONSHIPS', 'SOCIETY'));
ALTER TABLE forge_quest ADD CONSTRAINT ck_forge_quest_custom CHECK (
    template_code IS NOT NULL OR (title IS NOT NULL AND area IS NOT NULL AND tier BETWEEN 1 AND 3));
ALTER TABLE forge_boss ADD CONSTRAINT ck_forge_boss_area CHECK (area IN
    ('FITNESS', 'FOCUS', 'HEALTH', 'MINDSET', 'LEARNING', 'SOCIAL', 'FINANCE', 'CAREER', 'RELATIONSHIPS', 'SOCIETY'));

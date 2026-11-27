-- AI-built Forge 66 plans. An AI program's quests are written for the person, not drawn
-- from the catalogue, so a quest carries its own title, description, area and tier when it
-- has no template. The plan is written a week at a time and re-tuned from what was done:
-- planned_through is the last day with quests, ai_profile what the interview found out,
-- ai_outline the three phases' themes.
ALTER TABLE forge_quest ALTER COLUMN template_code DROP NOT NULL;
ALTER TABLE forge_quest ADD COLUMN title VARCHAR(80);
ALTER TABLE forge_quest ADD COLUMN description VARCHAR(400);
ALTER TABLE forge_quest ADD COLUMN area VARCHAR(12);
ALTER TABLE forge_quest ADD COLUMN tier INTEGER;
ALTER TABLE forge_quest ADD CONSTRAINT ck_forge_quest_custom CHECK (
    template_code IS NOT NULL OR (title IS NOT NULL AND area IS NOT NULL AND tier BETWEEN 1 AND 3));
ALTER TABLE forge_quest ADD CONSTRAINT ck_forge_quest_area CHECK (
    area IS NULL OR area IN ('FITNESS', 'FOCUS', 'HEALTH', 'MINDSET', 'LEARNING', 'SOCIAL'));

ALTER TABLE forge_program ADD COLUMN ai_profile VARCHAR(4000);
ALTER TABLE forge_program ADD COLUMN ai_outline VARCHAR(2000);
ALTER TABLE forge_program ADD COLUMN planned_through DATE;

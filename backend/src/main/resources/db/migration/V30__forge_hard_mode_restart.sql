-- Hard mode now restarts: a day left missed or partial ends the run, and a new run
-- starts at day 1. The old run is kept, with everything it earned, as RESTARTED.
ALTER TABLE forge_program DROP CONSTRAINT ck_forge_program_status;
ALTER TABLE forge_program ADD CONSTRAINT ck_forge_program_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED', 'RESTARTED'));

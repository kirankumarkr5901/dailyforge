-- A set logged with no signal waits on the phone and is sent later, possibly more than
-- once (a send can reach the server even when its answer is lost). The phone gives each
-- set an id up front; a second arrival of the same id returns the set already saved
-- instead of logging it twice.
ALTER TABLE workout_set ADD COLUMN client_id UUID;
CREATE UNIQUE INDEX uq_workout_set_client_id ON workout_set (client_id);

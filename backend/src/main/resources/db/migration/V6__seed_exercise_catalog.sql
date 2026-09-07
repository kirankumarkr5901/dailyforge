-- A small starter catalog (owner_user_id NULL — visible to everyone, spec §6), so the
-- planner's exercise search is never empty on a fresh install. Anyone can still add
-- their own on top of it; nothing here is special beyond having no owner.

INSERT INTO exercise (id, owner_user_id, name, search_name, kind, equipment, muscle_groups, is_elite, created_at, updated_at) VALUES
('0c2f5b20-0000-4000-8000-000000000001', NULL, 'Bench press', 'bench press', 'STRENGTH', 'BARBELL', 'chest,triceps', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000002', NULL, 'Squat', 'squat', 'STRENGTH', 'BARBELL', 'legs,glutes', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000003', NULL, 'Deadlift', 'deadlift', 'STRENGTH', 'BARBELL', 'back,legs', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000004', NULL, 'Overhead press', 'overhead press', 'STRENGTH', 'BARBELL', 'shoulders,triceps', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000005', NULL, 'Bent-over row', 'bent-over row', 'STRENGTH', 'BARBELL', 'back', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000006', NULL, 'Dumbbell curl', 'dumbbell curl', 'STRENGTH', 'DUMBBELL', 'arms', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000007', NULL, 'Dumbbell shoulder press', 'dumbbell shoulder press', 'STRENGTH', 'DUMBBELL', 'shoulders', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000008', NULL, 'Incline dumbbell press', 'incline dumbbell press', 'STRENGTH', 'DUMBBELL', 'chest', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000009', NULL, 'Lat pulldown', 'lat pulldown', 'STRENGTH', 'MACHINE', 'back', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-00000000000a', NULL, 'Leg press', 'leg press', 'STRENGTH', 'MACHINE', 'legs', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-00000000000b', NULL, 'Seated cable row', 'seated cable row', 'STRENGTH', 'MACHINE', 'back', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-00000000000c', NULL, 'Pull-up', 'pull-up', 'STRENGTH', 'BODYWEIGHT', 'back,arms', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-00000000000d', NULL, 'Push-up', 'push-up', 'STRENGTH', 'BODYWEIGHT', 'chest,triceps', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-00000000000e', NULL, 'Dip', 'dip', 'STRENGTH', 'BODYWEIGHT', 'chest,triceps', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-00000000000f', NULL, 'Plank', 'plank', 'STRENGTH', 'BODYWEIGHT', 'core', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000010', NULL, 'Treadmill run', 'treadmill run', 'CARDIO', 'NONE', 'legs', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000011', NULL, 'Rowing machine', 'rowing machine', 'CARDIO', 'NONE', 'back,legs', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('0c2f5b20-0000-4000-8000-000000000012', NULL, 'Stationary bike', 'stationary bike', 'CARDIO', 'NONE', 'legs', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

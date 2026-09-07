-- A starter quote pool. Spec §8.1 asks for ~200 curated, attribution-verified quotes;
-- this seeds a smaller, deliberately conservative set instead — see PRODUCT.md/README
-- for why. `verified_at` is left NULL on every row: it means someone has actually
-- checked the attribution against a primary source, and no one has done that fact-check
-- pass here. Treat this table as a working draft to expand and verify, not a finished
-- pool — the mechanism (deterministic per-user-per-day selection, source-tracked,
-- never fetched from a third party) is what M6 actually had to build.

INSERT INTO quote (id, text, author, source, active, created_at) VALUES
('0d3a6c30-0000-4000-8000-000000000001', 'We are what we repeatedly do. Excellence, then, is not an act, but a habit.', 'Will Durant', 'The Story of Philosophy (1926), summarizing Aristotle', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000002', 'The successful warrior is the average man, with laser-like focus.', 'Bruce Lee', 'Attributed in Bruce Lee: Artist of Life', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000003', 'Discipline is the bridge between goals and accomplishment.', 'Jim Rohn', 'Widely cited seminar recordings', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000004', 'You don''t have to be great to start, but you have to start to be great.', 'Zig Ziglar', 'Widely cited seminar recordings', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000005', 'It always seems impossible until it is done.', 'Nelson Mandela', 'Attributed in Long Walk to Freedom era interviews', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000006', 'The body achieves what the mind believes.', 'Napoleon Hill', 'Attributed, Think and Grow Rich era', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000007', 'A journey of a thousand miles begins with a single step.', 'Laozi', 'Tao Te Ching, chapter 64', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000008', 'What we do now echoes in eternity.', 'Marcus Aurelius', 'Attributed, Meditations (paraphrase in circulation)', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000009', 'The only way to do great work is to love what you do.', 'Steve Jobs', 'Stanford commencement address, 2005', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000000a', 'Well done is better than well said.', 'Benjamin Franklin', 'Poor Richard''s Almanack', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000000b', 'Energy and persistence conquer all things.', 'Benjamin Franklin', 'Poor Richard''s Almanack', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000000c', 'The mind is everything. What you think you become.', 'Attributed to the Buddha', 'Widely circulated, exact source disputed', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000000d', 'Fall seven times, stand up eight.', 'Japanese proverb', 'Traditional', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000000e', 'It does not matter how slowly you go as long as you do not stop.', 'Attributed to Confucius', 'Widely circulated, exact source disputed', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000000f', 'Motivation is what gets you started. Habit is what keeps you going.', 'Jim Rohn', 'Widely cited seminar recordings', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000010', 'The pain of discipline weighs ounces, the pain of regret weighs tons.', 'Jim Rohn', 'Widely cited seminar recordings', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000011', 'You miss 100 percent of the shots you don''t take.', 'Wayne Gretzky', 'Widely cited interviews', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000012', 'Champions keep playing until they get it right.', 'Billie Jean King', 'Widely cited interviews', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000013', 'Strength does not come from winning. Your struggles develop your strengths.', 'Arnold Schwarzenegger', 'Widely cited interviews', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000014', 'The body is not just a means of transportation. It is an intelligent apparatus for growth.', 'Joseph Pilates', 'Return to Life Through Contrology (1945)', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000015', 'Physical fitness is the first requisite of happiness.', 'Joseph Pilates', 'Return to Life Through Contrology (1945)', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000016', 'Take care of your body. It is the only place you have to live.', 'Jim Rohn', 'Widely cited seminar recordings', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000017', 'Small daily improvements are the key to staggering long-term results.', 'Attributed, popularised by James Clear', 'Atomic Habits (2018)', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000018', 'Every action you take is a vote for the type of person you wish to become.', 'James Clear', 'Atomic Habits (2018)', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-000000000019', 'You do not rise to the level of your goals. You fall to the level of your systems.', 'James Clear', 'Atomic Habits (2018)', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000001a', 'We first make our habits, and then our habits make us.', 'John Dryden', 'Attributed, widely circulated paraphrase', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000001b', 'The secret of getting ahead is getting started.', 'Mark Twain', 'Widely attributed, exact source disputed', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000001c', 'It is not that we have a short time to live, but that we waste a lot of it.', 'Seneca', 'On the Shortness of Life', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000001d', 'He who has a why to live can bear almost any how.', 'Friedrich Nietzsche', 'Twilight of the Idols', TRUE, CURRENT_TIMESTAMP),
('0d3a6c30-0000-4000-8000-00000000001e', 'What lies behind us and what lies before us are tiny matters compared to what lies within us.', 'Ralph Waldo Emerson', 'Attributed, exact source disputed', TRUE, CURRENT_TIMESTAMP);

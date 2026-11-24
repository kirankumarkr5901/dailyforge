package com.dailyforge.skill.domain;

import java.util.List;
import java.util.Optional;

/**
 * The three skills from the Cut Block plan and their levels. Fixed content, kept in
 * code: it changes with the app, not per user. A user's own state (current level,
 * days done) lives in the database.
 */
public final class SkillCatalog {

    private SkillCatalog() {}

    public record Level(String title, List<String> drills, String goal) {}

    public record Skill(String code, String name, String when, String safety, List<Level> levels) {}

    public static final List<Skill> SKILLS = List.of(
            new Skill(
                    "HANDSTAND",
                    "Handstand",
                    "Push and upper days · before pressing",
                    null,
                    List.of(
                            new Level(
                                    "Foundations",
                                    List.of(
                                            "Wrist prep: circles, rocks, palm lifts · 1 min",
                                            "Pike hold on a box, hips over hands · 3 × 20 s",
                                            "Wall walk-up, belly facing the wall, as high as feels safe · 3 × 15 s",
                                            "Hollow body hold · 3 × 20 s"),
                                    "Move on when you can wall walk until your nose almost touches the wall and hold for 30 s."),
                            new Level(
                                    "Wall work",
                                    List.of(
                                            "Wrist prep · 1 min",
                                            "Chest-to-wall handstand · 4 × 30 s",
                                            "Wall shoulder taps · 3 × 6 / side",
                                            "Kick-up to wall, back facing the wall · 6 tries",
                                            "Hollow hold · 3 × 30 s"),
                                    "Move on when you can hold 60 s chest-to-wall and kick up consistently."),
                            new Level(
                                    "Freestanding",
                                    List.of(
                                            "Wrist prep · 1 min",
                                            "Practise bailing out: cartwheel out of the handstand · 5 reps",
                                            "Heel pulls off the wall · 5 × 10 s",
                                            "Freestanding kick-ups · 10 tries",
                                            "Wall line drill · 3 × 45 s"),
                                    "Goal: a 10 s freestanding hold."))),
            new Skill(
                    "CROW_LSIT",
                    "Crow + L-sit",
                    "Pull days · before pulling",
                    null,
                    List.of(
                            new Level(
                                    "Foundations",
                                    List.of(
                                            "Wrist prep · 1 min",
                                            "Crow pose with feet on a yoga block · 5 × 10 s",
                                            "Tuck L-sit on dip bars or parallettes · 4 × 10 s",
                                            "Scapula pull-ups · 2 × 8"),
                                    "Move on when you can hold crow for 15 s and a tuck L-sit for 15 s."),
                            new Level(
                                    "Building",
                                    List.of(
                                            "Wrist prep · 1 min",
                                            "Crow from the floor · 5 × 20 s",
                                            "One-leg L-sit, alternating legs · 4 × 10 s",
                                            "Hanging tuck hold · 3 × 15 s"),
                                    "Move on when you can hold crow for 30 s."),
                            new Level(
                                    "Advanced",
                                    List.of(
                                            "Crow to tripod headstand transition · 5 reps",
                                            "Full L-sit on bars · 4 × 10 s",
                                            "Headstand hold against a wall · 3 × 30 s"),
                                    "Goal: a 15 s full L-sit."))),
            new Skill(
                    "FLIP",
                    "Flip prep",
                    "Leg days · before squats",
                    "Never try a backflip alone on a hard floor. Do the first real flips on a crash mat or "
                            + "trampoline with a coach or trained spotter (a parkour or gymnastics class is ideal).",
                    List.of(
                            new Level(
                                    "Power + rolls",
                                    List.of(
                                            "Tuck jumps, knees to chest · 3 × 6",
                                            "Broad jumps, stick the landing · 3 × 3",
                                            "Backward roll on a mat · 5 reps",
                                            "Bridge hold · 3 × 15 s"),
                                    "Move on when tuck jumps feel fast and your backward rolls are smooth."),
                            new Level(
                                    "Air awareness",
                                    List.of(
                                            "Box jumps · 4 × 3",
                                            "Backward roll through to push-up position · 5 reps",
                                            "Jump and fall back onto a crash mat (spotted) · 6 reps",
                                            "Bridge push-ups · 3 × 5"),
                                    "Move on when you jump straight up, not backwards, during the mat drill."),
                            new Level(
                                    "Spotted flips",
                                    List.of(
                                            "Spotted backflips on a trampoline or crash mat, with a coach · 5–8 tries",
                                            "Front tuck drills into a foam pit or onto a mat · 5 tries",
                                            "Finish with 3 sticky landings from a box"),
                                    "Goal: a clean, spotted standing back tuck."))));

    public static Optional<Skill> find(String code) {
        return SKILLS.stream().filter(s -> s.code().equals(code)).findFirst();
    }
}

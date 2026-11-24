package com.dailyforge.mobility.domain;

import java.util.List;
import java.util.Optional;

/** The four guided flows from the Cut Block plan: each move, how long, and what to feel. */
public final class MobilityCatalog {

    private MobilityCatalog() {}

    public record Move(String name, int seconds, String cue) {}

    public record Flow(String code, String name, String when, List<Move> moves) {
        public int totalSeconds() {
            return moves.stream().mapToInt(Move::seconds).sum();
        }
    }

    public static final List<Flow> FLOWS = List.of(
            new Flow("WARMUP", "Daily Warm-up", "Before every session", List.of(
                    new Move("Cat-cow", 45, "Move slowly through each segment of your spine. Breathe in as you arch, out as you round."),
                    new Move("World's greatest stretch · L", 30, "Lunge forward, left hand down, rotate your right arm up toward the ceiling."),
                    new Move("World's greatest stretch · R", 30, "Switch sides. Let your eyes follow your top hand."),
                    new Move("Hip circles", 40, "Hands on hips, big slow circles, then switch direction halfway."),
                    new Move("Leg swings · front-back", 40, "Hold a wall. Swing loosely and let the range grow each rep. Switch legs halfway."),
                    new Move("Leg swings · side-side", 40, "Swing across your body and out. Switch legs halfway."),
                    new Move("Ankle rocks", 40, "Half-kneel, drive your knee forward over your toes while the heel stays down."),
                    new Move("Glute bridge", 40, "Squeeze your glutes at the top for 2 seconds on each rep."),
                    new Move("Band pull-aparts / arm circles", 40, "Open your chest and wake up your upper back."),
                    new Move("Paused bodyweight squat", 45, "Sit to the bottom, hold for 3 seconds, stand up. Smooth reps."))),
            new Flow("POSTRUN", "Post-run Reset", "After every run", List.of(
                    new Move("Calf stretch · L", 30, "Hands on a wall, back leg straight, heel pressed down."),
                    new Move("Calf stretch · R", 30, "Switch legs."),
                    new Move("Soleus stretch", 45, "Same position with the back knee bent. You should feel it low, near the Achilles."),
                    new Move("Hip flexor · L", 30, "Half-kneel, tuck your pelvis under, squeeze the glute of the back leg."),
                    new Move("Hip flexor · R", 30, "Switch sides. Reach the same-side arm overhead."),
                    new Move("90/90 hip switches", 60, "Sit tall and rotate both knees side to side. Go slow."),
                    new Move("Pigeon · L", 30, "Square your hips, fold forward over the front shin."),
                    new Move("Pigeon · R", 30, "Switch sides."),
                    new Move("Hamstring floss", 45, "Lie on your back, hold one leg up, straighten and bend it gently. Switch at halftime."),
                    new Move("Child's pose", 45, "Breathe into your back and let your heart rate settle."))),
            new Flow("HIPS", "Hips & Ankles Deep Flow", "Sunday or any rest evening", List.of(
                    new Move("Deep squat hold", 60, "Hold a post if you need to. Heels down, chest up, push your knees out."),
                    new Move("Cossack squat", 60, "Shift side to side slowly, keeping the straight leg's toes up."),
                    new Move("90/90 with lift-offs", 60, "In the 90/90 position, lift the back knee off the floor for 2 seconds each rep."),
                    new Move("Frog stretch", 60, "Knees wide, rock your hips back slowly."),
                    new Move("Couch stretch · L", 45, "Back knee against a wall or couch, stay tall. The quad and hip flexor stretch is intense."),
                    new Move("Couch stretch · R", 45, "Switch sides."),
                    new Move("Knee-to-wall ankle · L", 30, "Foot a few cm from the wall, push the knee to the wall while the heel stays down."),
                    new Move("Knee-to-wall ankle · R", 30, "Switch sides. Move the foot back as you get more mobile."),
                    new Move("Spiderman lunge + reach", 60, "Step into a deep lunge, rotate your arm up, alternate sides."),
                    new Move("Thread the needle", 60, "On all fours, slide one arm under your body, then open it to the sky. Switch halfway."),
                    new Move("Dead hang", 30, "Hang from a bar and let your shoulders and spine lengthen. Skip it if you have no bar."))),
            new Flow("DESK", "Desk Reset", "Mid-workday, between meetings", List.of(
                    new Move("Seated T-spine extension", 45, "Hands behind your head, arch over the top of the chair back."),
                    new Move("Open book · L", 30, "Lie on your side, knees stacked, open your top arm across to the floor behind you."),
                    new Move("Open book · R", 30, "Switch sides."),
                    new Move("Chin tucks", 30, "Make a double chin, hold for 2 seconds. Do this for laptop neck."),
                    new Move("Doorway pec stretch", 45, "Forearm on the door frame, step through gently."),
                    new Move("Wrist & forearm stretch", 30, "Palm up, then palm down, gently pulled back with the other hand."),
                    new Move("Standing hip flexor", 60, "Step back, tuck your pelvis under, squeeze the glute. Switch at halftime."))));

    public static Optional<Flow> find(String code) {
        return FLOWS.stream().filter(f -> f.code().equals(code)).findFirst();
    }
}

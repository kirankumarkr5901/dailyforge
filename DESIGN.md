---
name: DailyForge
description: A cold steel workshop where warmth is a payout, never a palette.
colors:
  surface: "#f1f3f5"
  surface-raised: "#ffffff"
  surface-sunken: "#e4e8eb"
  surface-inset: "#dae0e5"
  ink: "#14171a"
  ink-muted: "#5a626b"
  ink-faint: "#79818b"
  ink-inverse: "#f1f3f5"
  ink-on-solid: "#ffffff"
  line: "#d6dbe0"
  line-strong: "#b9c1c9"
  heat-1: "#ffc24a"
  heat-2: "#f5811f"
  heat-3: "#e0521b"
  heat-4: "#b32d12"
  heat-ink-1: "#7a4f00"
  heat-ink-2: "#8a4008"
  heat-ink-3: "#8a2408"
  heat-ink-4: "#7a0d06"
  done: "#2e9e6b"
  done-ink: "#1a6b46"
  penalty: "#a8324a"
  penalty-ink: "#8c2036"
  info: "#3a6ea5"
  info-ink: "#2c5580"
  focus: "#4c9aff"
  equip-barbell: "#4a5a78"
  equip-dumbbell: "#4f6f63"
  equip-machine: "#6b5a78"
  equip-bodyweight: "#5e6b72"
  equip-cardio: "#3f6c7a"
typography:
  display:
    fontFamily: "Archivo Variable, Archivo, Helvetica Neue, Arial, sans-serif"
    fontSize: "2.125rem"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 125, 'wght' 700"
  headline:
    fontFamily: "Archivo Variable, Archivo, Helvetica Neue, Arial, sans-serif"
    fontSize: "1.625rem"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 125, 'wght' 700"
  title:
    fontFamily: "Archivo Variable, Archivo, Helvetica Neue, Arial, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 600
    lineHeight: 1.15
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 125, 'wght' 600"
  body:
    fontFamily: "IBM Plex Sans Variable, IBM Plex Sans, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  label:
    fontFamily: "IBM Plex Sans Variable, IBM Plex Sans, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 500
    lineHeight: 1.5
    letterSpacing: "normal"
  caption:
    fontFamily: "IBM Plex Sans Variable, IBM Plex Sans, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: 1.5
    letterSpacing: "normal"
  metric:
    fontFamily: "Archivo Variable, Archivo, Helvetica Neue, Arial, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.02em"
    fontVariation: "'wdth' 125, 'wght' 700"
    fontFeature: "'tnum' 1"
rounded:
  control: "4px"
  card: "10px"
  sheet: "20px"
  pill: "999px"
spacing:
  space-1: "0.25rem"
  space-2: "0.5rem"
  space-3: "0.75rem"
  space-4: "1rem"
  space-5: "1.5rem"
  space-6: "2rem"
  space-7: "3rem"
  space-8: "4rem"
components:
  button-primary:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    padding: "0 {spacing.space-4}"
    height: "44px"
  button-primary-hover:
    backgroundColor: "color-mix(in oklab, #14171a 88%, #f1f3f5)"
    textColor: "{colors.surface}"
  button-secondary:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 {spacing.space-4}"
    height: "44px"
  button-secondary-hover:
    backgroundColor: "{colors.surface-sunken}"
  button-ghost:
    backgroundColor: "transparent"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.control}"
    padding: "0 {spacing.space-4}"
    height: "44px"
  button-ghost-hover:
    backgroundColor: "{colors.surface-sunken}"
    textColor: "{colors.ink}"
  button-danger:
    backgroundColor: "{colors.penalty}"
    textColor: "{colors.ink-on-solid}"
    rounded: "{rounded.control}"
    padding: "0 {spacing.space-4}"
    height: "44px"
  icon-button:
    backgroundColor: "transparent"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.control}"
    size: "44px"
  card:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "{spacing.space-4}"
  chip:
    backgroundColor: "{colors.surface-sunken}"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.control}"
    padding: "2px {spacing.space-2}"
    typography: "{typography.caption}"
  chip-earned:
    backgroundColor: "color-mix(in oklab, #f5811f 20%, transparent)"
    textColor: "{colors.heat-ink-2}"
    rounded: "{rounded.control}"
    padding: "2px {spacing.space-2}"
  chip-done:
    backgroundColor: "rgba(46 158 107 / 0.12)"
    textColor: "{colors.done-ink}"
    rounded: "{rounded.control}"
  chip-equipment:
    backgroundColor: "transparent"
    textColor: "{colors.equip-barbell}"
    rounded: "{rounded.control}"
  input-field:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "0 {spacing.space-3}"
    height: "44px"
  score-pill:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.heat-ink-2}"
    rounded: "{rounded.pill}"
    padding: "{spacing.space-1} {spacing.space-3}"
  toast:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.card}"
    padding: "{spacing.space-3}"
  sheet-panel:
    backgroundColor: "{colors.surface-raised}"
    textColor: "{colors.ink}"
    rounded: "{rounded.sheet}"
    padding: "{spacing.space-4}"
    width: "560px"
  empty-state:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink-muted}"
    rounded: "{rounded.card}"
    padding: "{spacing.space-7} {spacing.space-4}"
---

# Design System: DailyForge

## Overview

**Creative North Star: "The Cold Workshop"**

DailyForge looks like a well-kept steel bench: cool greys across three surface depths, hairline rules instead of shadows, tabular figures wherever a number is a measurement, and almost no colour at all. The whole interface is deliberately underheated so that the rare warm object reads as a payout rather than as branding. A reviewer scanning a screen should see a workshop of cold parts and one glowing thing, and understand without being told that the glow was earned.

The system encodes that rule mechanically rather than trusting anyone to remember it. The token layer splits into a cold set (`--surface-*`, `--ink-*`, `--line*`, `--equip-*`) that anything may use, and an earned-value set (`--heat-*`, `--heat-ink-*`, `--heat-fill-*`, `--earned-*`) that only a component rendering a quantity the user earned may reach for. There is no warm "primary" or "brand" colour in the file — the strongest control in the app, the primary button, is filled with `--ink`, cold steel. A component with nothing earned on screen has no warm token available to it.

Warmth is also a quantity, not a switch. The four heat tokens form a ramp, and an earned value picks its step from its own magnitude: a twelve-point tick and a hundred-and-fifty-point milestone must not arrive at the same temperature. Density is compact and mobile-first — a single 560px column, 44px minimum targets, a 4px spacing grid — and the two themes are equal citizens rather than a light design with a dark skin bolted on.

**Key Characteristics:**
- Cold by default; warm only where something was earned.
- Three surface depths and 1px rules carry structure; shadows are reserved for floating layers.
- Four-step heat ramp driven by magnitude, with one bloom animation owned by a single component.
- Archivo at `wdth 125` for display, IBM Plex Sans for body, tabular numerals on every figure.
- 4px spacing scale, 44px minimum tap target, 560px content column.
- Reduced motion is a token-level switch, not a per-animation exception.

## Colors

A cool steel palette with a single warm family that is rationed by rule rather than by taste.

### Primary
- **Steel Ink** (`{colors.ink}`): The default text colour and the fill of the primary button. This is the system's "strong" colour; it is deliberately cold, so the loudest control on a screen is still not warm.

### Secondary
The earned-heat family. These are the only warm values in the system and they are gated: a component may use them only while rendering a quantity the user earned (points, a PR, a streak bonus, a heatmap cell with points in it).

- **Spark** (`{colors.heat-1}`): Step 1 — small gains, low heatmap cells. The coolest earned state.
- **Ember** (`{colors.heat-2}`): Step 2 — normal gains. The resting temperature of the score pill.
- **Forge** (`{colors.heat-3}`): Step 3 — big gains and personal records.
- **Crucible** (`{colors.heat-4}`): Step 4 — top heatmap step, milestones.
- **Heat as readable text** (`{colors.heat-ink-1}` … `{colors.heat-ink-4}`): The display hues fail contrast on a light ground, so heat that must be *read* uses the parallel ink ramp. On light, hotter reads deeper and redder; on dark, the ramp inverts and hotter reads brighter. Every step clears 4.5:1 (light 5.54 / 5.16 / 5.38 / 5.44; dark 12.20 / 9.22 / 7.20 / 6.12).

### Tertiary
Cool semantics that describe a *state*, never an amount, so they never borrow heat.

- **Workshop Green** (`{colors.done}` / `{colors.done-ink}`): A habit completed. Completion is a state, not a payout, so it is green and never orange.
- **Cool Crimson** (`{colors.penalty}` / `{colors.penalty-ink}`): Penalties and destructive actions. Deliberately shifted cool so it can never be mistaken for heat.
- **Slate Blue** (`{colors.info}` / `{colors.info-ink}`): Links, informational notes, the "back to today" affordance.
- **Signal Blue** (`{colors.focus}`): The focus ring and the caret. Never used decoratively.
- **Equipment greys** (`{colors.equip-barbell}`, `{colors.equip-dumbbell}`, `{colors.equip-machine}`, `{colors.equip-bodyweight}`, `{colors.equip-cardio}`): Five desaturated, cool-shifted hues that identify equipment type. Muted on purpose so a row of them never competes with an earned value.

### Neutral
- **Cool Paper** (`{colors.surface}`): The page ground. Cool grey, never cream.
- **Bench White** (`{colors.surface-raised}`): Cards, fields, sheets, toasts — anything sitting on the page.
- **Sunken Steel** (`{colors.surface-sunken}`): Hover fills, unearned heatmap cells, disabled field grounds.
- **Pressed Well** (`{colors.surface-inset}`): Track backgrounds and pressed states.
- **Muted Ink** (`{colors.ink-muted}`) / **Faint Ink** (`{colors.ink-faint}`): Secondary text; faint is non-text only (rules, disabled glyphs).
- **Rule** (`{colors.line}`) / **Strong Rule** (`{colors.line-strong}`): The 1px hairlines that do the structural work shadows would otherwise do. Strong rule is also the scrollbar thumb.

All text/background pairings clear WCAG AA 4.5:1 in both themes (light 4.71–11.12:1, dark 5.09–12.20:1).

### Named Rules

**The Earned Heat Rule.** Warm colour is a payout. A surface may use a heat token only while it renders a quantity the user earned. If you are reaching for heat and nothing on screen was earned, the answer is a cold token — and there is deliberately no warm "brand" or "primary" colour to fall back on.

**The Temperature Is Magnitude Rule.** An earned value picks its ramp step from its own size, not from its component. The score pill owns the thresholds (`[0, 25, 100, 300]`, lower bounds by absolute amount, display thresholds rather than point values); the earned chip takes a `step`, the toast takes an optional `heatStep`. Same amount, same temperature, wherever it appears.

**The Opposite Fills Rule.** Earned fills saturate as the step climbs in light (14 / 20 / 26 / 32% alpha) and stay low in dark (10 / 13 / 17 / 22%). A rising fill on a dark ground lightens the field and washes out the bright ink, so the dark theme climbs in ink and holds in fill. Never copy the light alphas into dark.

**The Cool Failure Rule.** Penalty red is cool-shifted so it can never be read as heat, and green marks completion rather than reward. State colours and earned colours are different vocabularies.

## Typography

**Display Font:** Archivo Variable (fallback Archivo, Helvetica Neue, Arial)
**Body Font:** IBM Plex Sans Variable (fallback IBM Plex Sans, system-ui)

**Character:** Archivo pinned to the top of its real width axis (`'wdth' 125`, which renders 26.2% wider than the default — genuine expanded metrics from a variable axis, not a faux stretch) gives the display voice an industrial, stencilled-on-the-machine quality. IBM Plex Sans underneath is plain, technical and unfussy: the labels on the bench, not the sign above the door. Both are self-hosted woff2, never a CDN.

### Hierarchy
- **Display** (700, 34px / 2.125rem, 1.15, `-0.02em`, `wdth 125`): Page titles and `h1`. Balanced wrapping is on by default.
- **Headline** (700, 26px / 1.625rem, 1.15, `wdth 125`): `h2`, section headers.
- **Title** (600, 20px / 1.25rem, 1.15, `wdth 125`): `h3`, sheet headings, empty-state titles, the date stepper's current-day label.
- **Body** (400, 16px, 1.5): All prose and control labels. Reading measure is capped by the 560px column; supporting copy caps at 40ch.
- **Label** (500, 14px, 1.5): Field labels, toast messages, unit suffixes.
- **Caption** (400, 12px, 1.5): Hints, errors, chips.
- **Metric** (700, `wdth 125`, tabular figures): Any number the user reads as a measurement.

### Named Rules

**The Tabular Numeral Rule.** Every number the user reads is a measurement. Figures carry `font-variant-numeric: tabular-nums` and `'tnum' 1`, applied by role (`.df-numeric`, `.df-metric`) rather than per component, so numerals never jitter while a value counts up and columns of figures line up.

**The Sentence Case Rule.** Sentence case everywhere. No all-caps labels, no letter-spaced eyebrows, no kickers above headings. The display face carries emphasis through width and weight, not through case.

## Layout

Mobile-first single column. Content sits in a 560px max-width container (`--content-max`) centred with a 16px gutter that expands to clear the device safe areas (`padding-inline: max(var(--gutter), var(--safe-left), var(--safe-right))`); the safe-area insets are wired in from the start so the Capacitor wrap is not a retrofit.

All spacing comes from a 4px scale (4 / 8 / 12 / 16 / 24 / 32 / 48 / 64), with 16px as the standard gutter and card padding. Interactive targets are at least 44px (`--tap-min`); buttons, icon buttons, steppers and the switch row all inherit that floor. The app header is 56px. Only one breakpoint exists in the primitive layer: at 720px the bottom sheet stops being a bottom sheet and becomes a centred panel with its drag grip removed, because a full-width bottom sheet on a wide screen is a phone gesture in the wrong place.

Stacking is two named layers: sheets at `z-index: 100`, toasts at `200`. Toasts sit above the thumb zone at 24px plus the bottom safe area, capped to the content width.

**Motion.** Three durations (fast 120ms, base 220ms, slow 420ms) and two easings — `cubic-bezier(0.16, 1, 0.3, 1)` as the default exponential settle, `cubic-bezier(0.65, 0, 0.35, 1)` for two-way moves only. Every duration is computed from `--motion-scale`, so setting the scale to zero collapses all motion at once; `base.css` carries a global backstop for anything with a literal duration.

**The One Motion Switch Rule.** Reduced motion is a token-level switch (`--motion-scale`), never a per-animation media query. Build durations from the tokens and the switch handles you for free. Celebrations still change state under reduced motion — the number and its temperature still move, the light just stops travelling.

## Elevation & Depth

The system is flat by default. Depth is carried by three surface tones and 1px rules, not by shadow: a card is `{colors.surface-raised}` inside a 1px `{colors.line}` border and casts nothing. Shadow is reserved for things that genuinely float above the page, and the shadow values are re-tuned per theme because a shadow tuned for light does not register at all on a dark ground.

### Shadow Vocabulary
- **Flat** (`box-shadow: none`): The default for every surface. Cards, chips, inputs, sections.
- **Raised** (light: `0 1px 2px rgba(10 14 18 / 0.06), 0 2px 6px rgba(10 14 18 / 0.05)`; dark: `0 1px 2px rgba(0 0 0 / 0.4), 0 2px 6px rgba(0 0 0 / 0.32)`): The opt-in card variant and the switch thumb. A slight lift, never a drop.
- **Floating** (light: `0 2px 4px rgba(10 14 18 / 0.1), 0 12px 28px rgba(10 14 18 / 0.16)`; dark: `0 2px 4px rgba(0 0 0 / 0.5), 0 12px 28px rgba(0 0 0 / 0.55)`): Sheets, toasts and the score pill. Nothing else.

The sheet scrim is a flat `rgba(6 9 12 / 0.5)` wash — no blur, no tint.

### Named Rules

**The Three Floaters Rule.** Only sheets, toasts and the score pill float. If a new surface wants a shadow, the answer is a rule and a surface tone instead.

**The Rule Before The Shadow Rule.** Structure is drawn with a 1px hairline (`{colors.line}` inside content, `{colors.line-strong}` around controls). Reach for a border before reaching for elevation.

## Shapes

Corner radius is a vocabulary, not a constant, and the size of the radius names the class of object: controls are 4px — buttons, icon buttons, inputs, selects, steppers, chips, checkbox boxes, the focus ring; containers are 10px — cards, toasts, empty states; sheets are 20px, top corners only in the bottom position and all four when centred; and 999px is reserved for genuinely capsule objects — the score pill, the switch track and thumb, the sheet grip, the scrollbar thumb, the button spinner.

Borders are always 1px. The empty state is the one dashed border in the system, and it earns it: a dashed outline says "nothing here yet" without needing a word. Equipment chips carry a 1px coloured rule and no fill, so a row of them reads as a set of labels instead of a row of competing blocks.

Icons are Lucide, one weight, rendered as inline SVG at 20px by default and 16px inside small controls. Never an icon font, never a glyph character.

## Components

Fifteen primitives, each a folder of three files (`.ts`, `.html`, `.scss`). Every one styles its own host through `:host` / `:host(.modifier)`.

**The Host Selector Rule.** Under Angular's emulated encapsulation a bare `.df-*` class in a component's own stylesheet silently does not apply to its host element. Style the host with `:host` and its variants with `:host(.modifier)` / `:host([data-attr])`, always. This cost the build a full inspection round; do not re-learn it.

### Buttons
- **Shape:** Softly squared control corners (4px), 1px border always present — transparent when the variant has no visible edge, so nothing shifts size between variants.
- **Primary:** Filled steel — `{colors.ink}` ground, `{colors.surface}` label, 16px horizontal padding, 44px minimum height, 600 weight, `-0.01em` tracking. The large variant is 52px with 24px padding and 20px text.
- **Hover / Focus:** The ground mixes 12% of the surface into the ink over 120ms with the exponential ease; active nudges 1px down. Focus is the global 2px `{colors.focus}` ring at 2px offset, keyboard only.
- **Secondary:** Raised surface with a strong 1px rule, hovering to sunken. **Ghost:** transparent with muted ink, hovering to a sunken fill and full ink. **Danger:** solid `{colors.penalty}` with a white label.
- **Loading:** The label stays in place at 70% opacity and a 1em ring spinner is added, so the button never resizes under the pointer. Under reduced motion the ring stops turning and holds as a partial arc rather than vanishing; `aria-busy` carries it for screen readers.

### Chips
- **Style:** 12px caption on a sunken fill with muted ink, 4px corners, 2px/8px padding, never wrapping.
- **State:** `done` and `penalty` take their wash and ink from the cool semantics. `earned` is the only warm chip and takes a `step` input which resolves through `data-heat` onto the four-step ink and fill ramp; it also switches on tabular figures. `equipment` drops the fill entirely and draws a 1px `currentColor` rule in the equipment hue.

### Cards / Containers
- **Corner Style:** 10px.
- **Background:** `{colors.surface-raised}` on the `{colors.surface}` page.
- **Shadow Strategy:** Flat by default; the `raised` modifier opts into the raised shadow. Most cards do not.
- **Border:** 1px `{colors.line}`, always.
- **Internal Padding:** 16px, or zero with the `flush` modifier for full-bleed content.

### Inputs / Fields
- **Style:** A 14px/500 muted label above a raised field with a strong 1px rule and 4px corners, 44px minimum height, 12px inner padding. Unit suffixes sit inside the field in muted 14px with tabular figures.
- **Focus:** The *field* owns the focus treatment — the border shifts to `{colors.focus}` plus a 3px focus-ring halo — and the inner control has its own outline suppressed, so there is never a double ring.
- **Error / Disabled:** Invalid shifts the border to `{colors.penalty}` and swaps the hint for a 12px `{colors.penalty-ink}` message wired through `aria-describedby` / `aria-invalid`. Disabled sinks the field ground and mutes the text.
- **Select** follows the same anatomy with `appearance: none` and a Lucide chevron pinned right. **Stepper input** puts 44px nudge buttons either side of a centred 20px/600 value field.
- **Switch / Checkbox:** The real control is visually replaced but never removed from the accessibility tree or the tab order — 1px offscreen, opacity zero, still focusable, with the ring drawn on the track or box. The switch track fills with `{colors.ink}` when on (cold, because on is a setting, not a reward); the checkbox fills with `{colors.done}` and draws its tick by animating a stroke-dashoffset.

### Score Pill (signature)
The one component that is warm at rest, because the only thing it ever shows is an earned quantity. A capsule on a raised ground with the floating shadow and a border mixed 30% from its current heat step, the value set in 20px heat ink with tabular figures. Its step follows the running total until an award is in flight, then follows the award instead, so a big gain reads hotter than a small one at the moment it lands. On award the number counts up over 700ms on an exponential settle and a warm bloom sweeps once across the pill and leaves it exactly as it was — heat arrives with the points instead of accumulating as decoration. Bloom intensity comes from the same step as the value. `pending` (a local delta the server has not confirmed) drops the value to muted ink: nothing is earned until the server says so. Under reduced motion the value and its temperature snap to their new state and the light does not travel.

### Toast
A raised card at 10px with the floating shadow, docked bottom-centre above the thumb zone, capped to the content width, three at a time with the oldest leaving. Message in 14px, an optional action button and a 32px close. The tone lives entirely on the border: neutral hairline, `penalty` mixed 35% crimson, and `earned` mixed with its heat step at rising strength (35 / 45 / 55 / 65%). Default dwell is 6s, which is the undo window.

### Sheet
A bottom sheet on a flat 50% scrim: raised panel, 20px top corners, capped at 560px wide and 88svh tall, with a 36px grip, a ruled header carrying a 20px title and a close target, a scrolling body, and a ruled footer that collapses when empty. Bottom padding clears the safe area and the page behind is scroll-locked. Above 720px it centres, rounds all four corners, drops to 80svh and hides the grip.

### Empty State & Skeleton
The empty state is a dashed-rule panel on the page ground with a faint Lucide icon, a 20px display-face title, body copy capped at 40ch, and an optional action. The skeleton is a sunken block with a single light sweep across it — one pass, not a pulse, so it reads as "working" without pulling the eye back; the sweep is a transform on a pseudo-element so it stays on the compositor.

## Do's and Don'ts

### Do:
- **Do** reach for a cold token by default. Heat tokens are only available to a surface rendering something the user earned.
- **Do** derive an earned surface's temperature from the amount, using the shared thresholds (`[0, 25, 100, 300]`); pass `step` / `heatStep` rather than hard-coding a hue.
- **Do** take every gap, pad and inset from the 4px scale, and keep interactive targets at 44px or more.
- **Do** style component hosts with `:host` / `:host(.modifier)` — never a bare `.df-*` class in the component's own stylesheet.
- **Do** build every transition from `--duration-*` and `--ease-*`, so the reduced-motion switch collapses it without a per-component rule.
- **Do** put tabular figures on every number the user reads, via `.df-numeric` or `.df-metric`.
- **Do** ship each primitive as three files — `.ts`, `.html`, `.scss` — with no inline template or style block.
- **Do** theme the browser's own surfaces: selection, caret, scrollbar, focus ring, link underline offset.
- **Do** re-tune shadow opacity per theme; the light values are invisible on the dark ground.

### Don't:
- **Don't** introduce a warm "primary" or "brand" colour. The strongest control in the app is cold steel, and that is the point.
- **Don't** use heat for a state — completion is `{colors.done}`, failure is the cool-shifted `{colors.penalty}`, information is `{colors.info}`.
- **Don't** give a surface a shadow unless it is a sheet, a toast or the score pill; use a 1px rule and a surface tone instead.
- **Don't** copy the light theme's rising fill alphas into dark — there the fill holds low and the ink brightens instead.
- **Don't** write a per-animation `prefers-reduced-motion` block when the duration already comes from `--motion-scale`.
- **Don't** set type in all caps, add a kicker or eyebrow above a heading, or letter-space a label; the system is sentence case throughout.
- **Don't** use icon fonts or glyph characters; icons are Lucide SVG at one weight.
- **Don't** apply one radius everywhere — 4px means control, 10px means container, 20px means sheet, 999px means capsule.
- **Don't** load fonts from a CDN. Everything is self-hosted woff2, because the app must render its score in a basement with no signal.

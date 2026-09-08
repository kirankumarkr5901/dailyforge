---
version: 1
slug: "frontend-src-app-dev-ui"
primary_target: "frontend/src/app/dev/ui"
related_targets: ["frontend/src/styles"]
---

# M0 foundations — design system and dev gallery

Scope: token layer, theming, the 15 UI primitives, and the `/dev/ui` gallery that proves them. Visitor mode: Operate.

Audience: the owner at milestone review, and every later session that builds on this vocabulary. Task: confirm in 90 seconds that both themes, reduced motion, keyboard focus and 360px all hold. Constraint: no feature code, no points logic, no hex values inside component styles.

Unresolved: whether Archivo's expanded width axis delivers §9's industrial display intent at real display sizes — verify in the build, report rather than substitute silently.

## Direction contract

THESIS: The token system encodes the heat rule so it cannot be broken by accident — heat tokens are earned-value tokens, and a component not showing an earned quantity has none to reach for. Refuses the neutral component-library kit where every colour is available everywhere.

OWN-WORLD: Spec §9 pinned. Cool steel greys across three surface depths, 1px rules, no shadow except on floating layers, 4px spacing scale, three elevation levels, tabular numerals everywhere numbers appear. Four heat tokens as a five-step earned ramp, plus one bloom owned solely by the score pill. Lucide icons, one weight. Archivo Expanded display, IBM Plex Sans body.

STORY: The reviewer sees a workshop of cold parts, notices almost no warm colour, and understands that warmth is a payout rather than a palette.

FIRST VIEWPORT: Sticky control bar — theme, motion, width — above a single scrolling column of labelled primitive sections, each showing every state side by side. The score pill sits in the bar, the only warm object on screen, counting up on demand.

FORM: No roll — direction brief-pinned by DAILYFORGE_SPEC.md §9, which beats the deal. Gallery composition is a workbench, first on the ordered list of three considered.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

-- The quote pool (spec §8.1, §6 "insight"). Every row must trace to a real speaker or
-- work — the plan is explicit that a third-party quote API is not an option here
-- because misattribution is the norm in those datasets (spec §13.10's own reasoning).

CREATE TABLE quote (
    id          UUID          NOT NULL,
    text        VARCHAR(500)  NOT NULL,
    author      VARCHAR(120)  NOT NULL,
    source      VARCHAR(200),
    verified_at TIMESTAMP WITH TIME ZONE,
    active      BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_quote PRIMARY KEY (id)
);

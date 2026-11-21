-- Users, refresh tokens and invites. Secrets are never stored: passwords as Argon2id hashes,
-- refresh tokens and invite codes as SHA-256 hashes.

CREATE TABLE users (
    username                 text PRIMARY KEY,
    role                     text        NOT NULL,
    password_hash            text        NOT NULL,
    password_change_required boolean     NOT NULL,
    disabled                 boolean     NOT NULL DEFAULT false,
    created_at               timestamptz NOT NULL
);

-- Every login starts a family; each refresh spends one token and adds the next. Reusing a spent token
-- revokes the whole family, since only a copy of the token can be used twice.
CREATE TABLE refresh_tokens (
    token_hash text PRIMARY KEY,
    family     text        NOT NULL,
    username   text        NOT NULL REFERENCES users (username) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    used_at    timestamptz,
    revoked_at timestamptz
);

CREATE INDEX refresh_tokens_family ON refresh_tokens (family);
CREATE INDEX refresh_tokens_username ON refresh_tokens (username);

CREATE TABLE invites (
    id          text PRIMARY KEY,
    code_hash   text        NOT NULL UNIQUE,
    username    text        NOT NULL REFERENCES users (username) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL,
    expires_at  timestamptz NOT NULL,
    redeemed_at timestamptz
);

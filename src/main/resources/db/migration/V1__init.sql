CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- user
CREATE TABLE users (
                       id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       username           VARCHAR(20)  NOT NULL,
                       email              VARCHAR(255) NOT NULL,
                       password_hash      VARCHAR(100) NOT NULL,
                       display_name       VARCHAR(40)  NOT NULL,
                       bio                VARCHAR(100) NOT NULL DEFAULT '',
                       contact            VARCHAR(100) NOT NULL DEFAULT '',
                       terms_agreed_at    TIMESTAMPTZ  NOT NULL,
                       mail_opt_in        BOOLEAN      NOT NULL DEFAULT FALSE,
                       failed_login_count INTEGER      NOT NULL DEFAULT 0,
                       locked_until       TIMESTAMPTZ,
                       created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       CONSTRAINT uq_users_username UNIQUE (username),
                       CONSTRAINT ck_users_username CHECK (username ~ '^[a-z0-9]{3,20}$'),
    CONSTRAINT ck_users_display_name CHECK (display_name <> ''),
    CONSTRAINT ck_users_failed_login_count CHECK (failed_login_count >= 0)
);
CREATE UNIQUE INDEX uq_users_email ON users (lower(email));

-- auth
CREATE TABLE refresh_tokens (
                                id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                family_id  UUID        NOT NULL,
                                token_hash VARCHAR(64) NOT NULL,
                                persistent BOOLEAN     NOT NULL,
                                expires_at TIMESTAMPTZ NOT NULL,
                                rotated_at TIMESTAMPTZ,
                                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                                CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_family     ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user       ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- folder
CREATE TABLE folders (
                         id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                         owner_id   BIGINT       NOT NULL REFERENCES users (id),
                         parent_id  BIGINT,
                         name       VARCHAR(50)  NOT NULL,
                         path       VARCHAR(600) NOT NULL,
                         created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                         CONSTRAINT uq_folders_id_owner    UNIQUE (id, owner_id),
                         CONSTRAINT uq_folders_parent_name UNIQUE (parent_id, name),
                         CONSTRAINT uq_folders_owner_path  UNIQUE (owner_id, path),
                         CONSTRAINT fk_folders_parent FOREIGN KEY (parent_id, owner_id) REFERENCES folders (id, owner_id),
                         CONSTRAINT ck_folders_root CHECK (
                             (parent_id IS NULL     AND name = ''  AND path = '')
                                 OR (parent_id IS NOT NULL AND name <> '' AND path <> '')
                             ),
                         CONSTRAINT ck_folders_name CHECK (
                             name !~ '[/[:space:][:cntrl:]]' AND name !~ '^\.' AND name !~* '\.md$'
)
    );
CREATE UNIQUE INDEX uq_folders_root     ON folders (owner_id) WHERE parent_id IS NULL;
CREATE INDEX        ix_folders_path_trgm ON folders USING gin (path gin_trgm_ops);

-- post
CREATE TABLE posts (
                       id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                       owner_id   BIGINT       NOT NULL REFERENCES users (id),
                       folder_id  BIGINT       NOT NULL,
                       file_name  VARCHAR(100) NOT NULL,
                       title      VARCHAR(200) NOT NULL DEFAULT '',
                       content    TEXT         NOT NULL DEFAULT '',
                       mode       INTEGER      NOT NULL DEFAULT 644,
                       char_count INTEGER      NOT NULL DEFAULT 0,
                       version    INTEGER      NOT NULL DEFAULT 0,
                       created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                       deleted_at TIMESTAMPTZ,
                       CONSTRAINT fk_posts_folder FOREIGN KEY (folder_id, owner_id) REFERENCES folders (id, owner_id),
                       CONSTRAINT ck_posts_file_name CHECK (
                           file_name ~ '^[^/.[:space:][:cntrl:]][^/[:space:][:cntrl:]]*\.md$'
),
    CONSTRAINT ck_posts_mode CHECK (mode IN (644, 640, 600)),
    CONSTRAINT ck_posts_content_size CHECK (octet_length(content) <= 1048576)
);
CREATE UNIQUE INDEX uq_posts_folder_file_name ON posts (folder_id, file_name) WHERE deleted_at IS NULL;
CREATE INDEX ix_posts_folder         ON posts (folder_id);
CREATE INDEX ix_posts_owner_updated  ON posts (owner_id, updated_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_posts_owner_deleted  ON posts (owner_id, deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_posts_content_trgm   ON posts USING gin (content gin_trgm_ops);
CREATE INDEX ix_posts_file_name_trgm ON posts USING gin (file_name gin_trgm_ops);

-- tag
CREATE TABLE tags (
                      id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                      name       VARCHAR(30) NOT NULL,
                      created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                      CONSTRAINT ck_tags_name CHECK (name ~ '^[^#/[:space:][:cntrl:]]+$')
    );
CREATE UNIQUE INDEX uq_tags_name        ON tags (lower(name));
CREATE INDEX        ix_tags_name_prefix ON tags (lower(name) text_pattern_ops);

CREATE TABLE post_tags (
                           post_id    BIGINT  NOT NULL REFERENCES posts (id) ON DELETE CASCADE,
                           tag_id     BIGINT  NOT NULL REFERENCES tags (id),
                           sort_order INTEGER NOT NULL,
                           PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX ix_post_tags_tag ON post_tags (tag_id, post_id);
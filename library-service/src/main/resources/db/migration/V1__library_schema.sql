-- Defaults cover native inserts, including BookEditionRepository.insertIfAbsent.
-- JPA auditing maintains updated_at for entity updates; SQL updates must set it explicitly.
CREATE TABLE borrowers
(
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(100) NOT NULL CHECK (length(trim(name)) > 0),
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_borrower_email UNIQUE (email),
    CONSTRAINT ck_email_normalized CHECK (email = lower(trim(email)))
);

CREATE TABLE book_editions
(
    isbn   VARCHAR(13) PRIMARY KEY,
    title  VARCHAR(255) NOT NULL CHECK (length(trim(title)) > 0),
    author VARCHAR(255) NOT NULL CHECK (length(trim(author)) > 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_isbn_format CHECK (isbn ~ '^([0-9]{9}[0-9X]|[0-9]{13})$')
);

CREATE TABLE books
(
    id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    isbn VARCHAR(13) NOT NULL REFERENCES book_editions (isbn),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loans
(
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    borrower_id BIGINT                   NOT NULL REFERENCES borrowers (id),
    book_id     BIGINT                   NOT NULL REFERENCES books (id),
    borrowed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    due_at      TIMESTAMP WITH TIME ZONE,
    returned_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_loan_dates CHECK (returned_at IS NULL OR returned_at >= borrowed_at),
    CONSTRAINT ck_loan_due_at CHECK (due_at IS NULL OR due_at > borrowed_at)
);

-- Primary keys and the unique borrower email constraint already create their own indexes.
-- Index referencing columns for edition/copy and borrower/book loan lookups and FK checks.
CREATE INDEX ix_books_isbn ON books (isbn);
CREATE INDEX ix_loans_borrower ON loans (borrower_id);
CREATE INDEX ix_loans_book ON loans (book_id);

-- Enforce one active loan per copy and cover the ID used by availability/existence queries.
CREATE UNIQUE INDEX uq_active_loan_per_book ON loans (book_id) INCLUDE (id) WHERE returned_at IS NULL;

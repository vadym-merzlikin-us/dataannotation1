-- Placeholder rows so both paginators have something to page through
-- (25 authors > one page of 20, and 26 books for the first author > one page).
-- Safe to delete this migration and reset the volume once real data exists.

INSERT INTO authors (first_name, second_name, description)
SELECT 'Author' || i, 'Placeholder' || i, 'Demo author #' || i || ' - replace with real data.'
FROM generate_series(1, 25) AS i;

INSERT INTO books (name, description, author_id)
SELECT 'Demo book ' || b, 'Short description for demo book ' || b || '.', (SELECT MIN(id) FROM authors)
FROM generate_series(1, 26) AS b;

INSERT INTO books (name, description, author_id)
SELECT 'Single title by ' || a.first_name, 'Short description.', a.id
FROM authors a
WHERE a.id > (SELECT MIN(id) FROM authors);

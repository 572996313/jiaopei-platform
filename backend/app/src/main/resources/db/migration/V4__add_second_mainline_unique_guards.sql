ALTER TABLE jp_term
    ADD UNIQUE KEY uk_jp_term_name (institution_id, term_name);

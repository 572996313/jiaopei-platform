ALTER TABLE schedule_lesson
    ADD COLUMN change_type VARCHAR(32) NULL AFTER lesson_status,
    ADD COLUMN related_lesson_id BIGINT NULL AFTER change_type,
    ADD KEY idx_schedule_lesson_related (related_lesson_id),
    ADD CONSTRAINT fk_schedule_lesson_related FOREIGN KEY (related_lesson_id) REFERENCES schedule_lesson (id);

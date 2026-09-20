ALTER TABLE jp_attendance
    ADD COLUMN lesson_id BIGINT NULL AFTER class_id;

ALTER TABLE jp_attendance
    DROP INDEX uk_jp_attendance_student_date,
    ADD UNIQUE KEY uk_jp_attendance_lesson_student (lesson_id, student_id),
    ADD KEY idx_jp_attendance_class_date (class_id, attendance_date),
    ADD KEY idx_jp_attendance_lesson (lesson_id),
    ADD CONSTRAINT fk_jp_attendance_lesson FOREIGN KEY (lesson_id) REFERENCES schedule_lesson (id);

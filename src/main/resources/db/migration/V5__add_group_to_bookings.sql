ALTER TABLE bookings ADD COLUMN group_id BIGINT REFERENCES study_groups(id);

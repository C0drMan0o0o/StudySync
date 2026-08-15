-- Index on bookings foreign keys for fast joins and user/group booking lookups
CREATE INDEX idx_bookings_user_id ON bookings(user_id);
CREATE INDEX idx_bookings_group_id ON bookings(group_id);

-- Index on group_members user_id for reverse join lookup (finding groups a user belongs to)
CREATE INDEX idx_group_members_user_id ON group_members(user_id);

-- Standard B-Tree index for room_id in bookings to speed up simple room joins (avoiding GiST overhead)
CREATE INDEX idx_bookings_room_id ON bookings(room_id);

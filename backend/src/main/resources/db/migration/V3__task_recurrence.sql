-- AI Command Center :: recurring tasks
-- Portable SQL: runs on H2 (MODE=PostgreSQL) and on PostgreSQL.
--
-- A recurrence turns a task into a series: completing one occurrence queues the next with its due
-- date advanced by the rule. The column is NOT NULL with a default so existing rows stay valid.

ALTER TABLE tasks ADD COLUMN recurrence VARCHAR(20) NOT NULL DEFAULT 'NONE';
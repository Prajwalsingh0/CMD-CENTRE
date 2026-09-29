-- AI Command Center :: goal hierarchy
-- Portable SQL: runs on H2 (MODE=PostgreSQL) and on PostgreSQL.
--
-- Goals form a shallow tree. The depth is capped in the service (three levels), and the foreign
-- key is ON DELETE SET NULL as a safety net: the service refuses to delete a goal that still has
-- sub-goals, so no orphan should ever rely on it.

ALTER TABLE goals ADD COLUMN parent_goal_id BIGINT;
ALTER TABLE goals ADD CONSTRAINT fk_goal_parent FOREIGN KEY (parent_goal_id) REFERENCES goals (id) ON DELETE SET NULL;
CREATE INDEX ix_goal_parent ON goals (parent_goal_id);
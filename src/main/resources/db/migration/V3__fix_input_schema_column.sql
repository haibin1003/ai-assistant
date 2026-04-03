-- Fix input_schema column to use LONGTEXT for larger tool schemas
ALTER TABLE t_tool_cache MODIFY COLUMN input_schema LONGTEXT NOT NULL COMMENT '输入Schema(JSON)';

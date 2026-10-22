ALTER TABLE agent_user_memory_event ADD COLUMN version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE agent_user_memory_event ADD FULLTEXT KEY ft_memory_content(content) WITH PARSER ngram;

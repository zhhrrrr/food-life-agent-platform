ALTER TABLE agent_session_message
    ADD COLUMN context_json JSON NULL COMMENT 'Displayed entity lists in answer order, not live facts';

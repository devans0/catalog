DROP TABLE IF EXISTS file_entries;

CREATE TABLE file_entries (
	id SERIAL PRIMARY KEY,
	file_name VARCHAR(255) NOT NULL,
	peer_id VARCHAR(50) NOT NULL,
	owner_ip VARCHAR(50) NOT NULL,
	owner_port INTEGER NOT NULL,
	last_seen TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
	UNIQUE(file_name, owner_ip, owner_port)
);

CREATE INDEX idx_file_name_search ON file_entries(file_name);
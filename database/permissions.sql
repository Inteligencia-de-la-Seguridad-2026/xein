CREATE USER IF NOT EXISTS 'xein'@'%' IDENTIFIED BY 'xein-lab-password';
GRANT SELECT, INSERT, UPDATE, DELETE ON xein.* TO 'xein'@'%';

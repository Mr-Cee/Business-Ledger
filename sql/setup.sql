-- Run as a PostgreSQL administrator. Choose a strong password before running.
CREATE ROLE ledger_user LOGIN PASSWORD 'REPLACE_WITH_YOUR_PASSWORD';
CREATE DATABASE business_ledger OWNER ledger_user;

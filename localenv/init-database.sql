-- noinspection SqlNoDataSourceInspectionForFile

-- Create user and database
CREATE USER secret_service WITH PASSWORD 'secret_service';
CREATE DATABASE secret_service;
GRANT ALL PRIVILEGES ON DATABASE secret_service TO secret_service;

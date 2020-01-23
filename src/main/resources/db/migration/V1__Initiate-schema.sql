-- noinspection SqlNoDataSourceInspectionForFile

CREATE TABLE secret
(
    id       varchar(100) PRIMARY KEY,
    document jsonb
);

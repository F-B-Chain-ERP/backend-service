--liquibase formatted sql

--changeset erp:allow-uncounted-inventory-counts
ALTER TABLE stock_count_item
    ALTER COLUMN counted_quantity DROP NOT NULL;

ALTER TABLE stock_count_item
    ALTER COLUMN variance_quantity DROP NOT NULL;

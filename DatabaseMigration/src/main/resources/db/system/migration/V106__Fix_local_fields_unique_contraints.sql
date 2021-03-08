ALTER TABLE local_fields DROP CONSTRAINT local_fields_unique;
ALTER TABLE local_fields ADD CONSTRAINT local_fields_unique UNIQUE (locale_id,field_name);

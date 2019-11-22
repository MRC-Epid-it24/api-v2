create table nutrient_table_records_fields
(
    nutrient_table_record_id varchar(32) not null,
    nutrient_table_id varchar(32) not null,
    field_name varchar(32) not null,
    field_value varchar(512) not null,
    constraint nutrient_table_records_fields_pk
        primary key (nutrient_table_record_id, nutrient_table_id, field_name),
    constraint nutrient_table_records_fields_record_fk
        foreign key (nutrient_table_record_id, nutrient_table_id) references nutrient_table_records
            on update cascade on delete cascade
);

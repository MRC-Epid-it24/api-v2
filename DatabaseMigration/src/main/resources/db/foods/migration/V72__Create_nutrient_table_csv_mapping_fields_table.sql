alter table nutrient_table_csv_mapping_columns rename to nutrient_table_csv_mapping_nutrient_columns;

create table nutrient_table_csv_mapping_field_columns
(
    id serial not null constraint nutrient_table_csv_mapping_field_columns_pkey primary key,
    nutrient_table_id varchar(32) not null
        constraint nutrient_table_csv_mapping_field_columns_nutrient_table_id_fk
            references nutrient_tables
            on update cascade on delete cascade,
    field_name varchar(32) not null,
    column_offset integer not null
);

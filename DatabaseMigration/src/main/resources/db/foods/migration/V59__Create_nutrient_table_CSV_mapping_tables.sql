create table nutrient_table_csv_mapping
(
    nutrient_table_id               varchar(32) primary key references nutrient_tables (id) on update cascade on delete restrict,
    row_offset                      integer not null,
    id_column_offset                integer not null,
    description_column_offset       integer not null,
    local_description_column_offset integer
);

create table nutrient_table_csv_mapping_columns
(
    id                serial primary key,
    nutrient_table_id varchar(32) not null references nutrient_tables (id) on update cascade on delete restrict,
    nutrient_type_id  integer     not null references nutrient_types (id) on update cascade on delete restrict,
    column_offset     integer     not null
);

alter table nutrient_table_csv_mapping_columns
    drop constraint nutrient_table_csv_mapping_columns_nutrient_table_id_fkey;

alter table nutrient_table_csv_mapping_columns
    add constraint nutrient_table_csv_mapping_columns_nutrient_table_id_fkey
        foreign key (nutrient_table_id) references nutrient_tables
            on update cascade on delete cascade;

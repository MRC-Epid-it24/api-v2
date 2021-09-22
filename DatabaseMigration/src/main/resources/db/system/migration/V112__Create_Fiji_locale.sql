insert into locales values ('en_FJ', 'Fiji', 'Fiji', 'en_gb', 'en', 'fj', 'en_GB');

insert into local_fields (locale_id, field_name, description) select 'en_FJ', field_name, description from local_fields where locale_id = 'en_GB';

insert into local_nutrient_types (locale_id, nutrient_type_id) select 'en_FJ', nutrient_type_id from local_nutrient_types where locale_id = 'en_GB';


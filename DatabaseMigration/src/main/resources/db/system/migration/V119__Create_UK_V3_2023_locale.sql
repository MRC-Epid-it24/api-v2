insert into locales values('UK_V3_2023', 'United Kingdom V3_2023', 'United Kingdom V3_2023', 'en_GB', 'en', 'gb', null);

insert into local_fields (locale_id, field_name, description) select 'UK_V3_2023', field_name, description from local_fields where locale_id = 'UK_current';

insert into local_nutrient_types (locale_id, nutrient_type_id) select 'UK_V3_2023', nutrient_type_id from local_nutrient_types where locale_id = 'UK_current';

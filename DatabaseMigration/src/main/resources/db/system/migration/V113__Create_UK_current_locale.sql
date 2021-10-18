insert into locales values('UK_current', 'United Kingdom (current)', 'United Kingdom (current)', 'en_GB', 'en', 'gb', null);

insert into local_fields (locale_id, field_name, description) select 'UK_current', field_name, description from local_fields where locale_id = 'NDNSv1';

insert into local_nutrient_types (locale_id, nutrient_type_id) select 'UK_current', nutrient_type_id from local_nutrient_types where locale_id = 'NDNSv1';

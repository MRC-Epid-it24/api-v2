INSERT INTO local_fields (locale_id, field_name, description) SELECT 'UKSAv2', field_name, description FROM local_fields where locale_id = 'NDNSv1';
INSERT INTO local_nutrient_types (locale_id, nutrient_type_id) SELECT 'UKSAv2', nutrient_type_id FROM local_nutrient_types where locale_id = 'NDNSv1';

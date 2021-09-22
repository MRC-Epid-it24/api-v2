insert into locales values ('en_FJ', 'Fiji', 'Fiji', 'en_GB', 'en', 'fj', 'en_GB');

insert into foods_local_lists(locale_id, food_code) select 'en_FJ', food_code from foods_local_lists where locale_id = 'en_GB';

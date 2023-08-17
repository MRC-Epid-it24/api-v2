insert into locales values('UK_V3_2023', 'United Kingdom V3_2023', 'United Kingdom V3_2023', 'en_GB', 'en', 'gb', null);

INSERT INTO split_list(locale_id, first_word, words) SELECT 'UK_V3_2023', first_word, words FROM split_list WHERE locale_id = 'UK_current';

INSERT INTO split_words(locale_id, words) SELECT 'UK_V3_2023', words FROM split_words WHERE locale_id = 'UK_current';

INSERT INTO synonym_sets(locale_id,synonyms) SELECT 'UK_V3_2023', synonyms FROM synonym_sets WHERE locale_id = 'UK_current';


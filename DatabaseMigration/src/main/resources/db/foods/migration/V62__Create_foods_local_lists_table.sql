create table foods_local_lists
(
    locale_id varchar(16) references locales (id),
    food_code varchar(8) references foods (code),
    constraint foods_locale_inclusion_pk primary key (locale_id, food_code)
);

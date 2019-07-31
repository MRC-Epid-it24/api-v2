create table foods_local_lists
(
    locale_id varchar(16) references locales (id) on update cascade on delete cascade,
    food_code varchar(8) references foods (code) on update cascade on delete cascade,
    constraint foods_locale_inclusion_pk primary key (locale_id, food_code)
);

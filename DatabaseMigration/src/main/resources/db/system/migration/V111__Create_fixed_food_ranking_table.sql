create table fixed_food_ranking
(
    id        serial      not null primary key,
    locale_id varchar(16) not null,
    food_code varchar(8)  not null,
    rank      integer     not null,

    constraint fixed_food_ranking_locale_fk foreign key (locale_id) references locales(id) on update cascade on delete cascade
);

create index fixed_food_ranking_index on fixed_food_ranking (locale_id, food_code);

create table local_fields
(
    id serial not null constraint local_fields_pk  primary key,
    locale_id varchar(16) not null constraint local_fields_locale_fk references locales on update cascade on delete cascade,
    field_name varchar(32) not null constraint local_fields_unique unique,
    description varchar(256) not null
);

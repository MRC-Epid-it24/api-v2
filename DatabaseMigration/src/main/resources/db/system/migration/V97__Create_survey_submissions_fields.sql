create table survey_submission_fields
(
    id serial not null constraint survey_submission_fields_pk primary key,
    food_id integer not null constraint survey_submission_fields_food_id_fk references survey_submission_foods
            on update cascade on delete cascade,
    field_name varchar(32) not null,
    value varchar(512) not null
);

create index survey_submission_fields_food_index
    on survey_submission_fields (food_id);

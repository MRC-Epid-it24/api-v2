create table pairwise_associations_state
(
    id integer primary key not null default 1,
    last_submission_time timestamp with time zone not null default '1970-01-01 00:00:00+00'
);

insert into pairwise_associations_state default values;

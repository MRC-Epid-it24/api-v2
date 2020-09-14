alter table surveys add column maximum_daily_submissions integer not null default 3;
alter table surveys add column minimum_submission_interval integer not null default 600;
alter table surveys add constraint surveys_maximum_daily_submissions_at_least_one check ( maximum_daily_submissions > 0 );

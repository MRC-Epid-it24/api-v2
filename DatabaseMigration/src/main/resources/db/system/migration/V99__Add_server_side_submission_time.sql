alter table survey_submissions add column submission_time timestamp with time zone;
update survey_submissions set submission_time = end_time;
alter table survey_submissions alter column submission_time set not null;

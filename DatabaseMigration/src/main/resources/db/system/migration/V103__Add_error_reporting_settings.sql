alter table surveys add column client_error_report_state boolean not null default true;
alter table surveys add column client_error_report_stack_trace boolean not null default true;

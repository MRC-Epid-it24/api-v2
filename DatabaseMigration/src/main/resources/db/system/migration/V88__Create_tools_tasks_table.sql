create table tools_tasks
(
  id serial primary key,
  type varchar(64) not null,
  user_id integer not null
    constraint tools_tasks_user_id_fk
    references users on update cascade on delete restrict,
  created_at timestamp with time zone not null,
  started_at timestamp with time zone,
  completed_at timestamp with time zone,
  download_url varchar(1024),
  download_url_expires_at timestamp with time zone,
  progress real,
  successful boolean,
  stack_trace varchar(2048)
);

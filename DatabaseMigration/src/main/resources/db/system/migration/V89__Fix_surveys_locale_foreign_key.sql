alter table surveys add constraint surveys_locale_fk foreign key(locale) references locales(id) on delete restrict on update cascade;
-- Dangerous assumption that only the default food group feedback (red meat) exists that we can restore later
delete from food_groups_feedback_group_ids;

alter table food_groups_feedback_group_ids drop constraint food_groups_feedback_group_ids_food_group_id_fkey;

alter table food_groups_feedback_group_ids rename column food_group_id to nutrient_id;
alter table food_groups_feedback_group_ids rename to food_groups_feedback_nutrient_ids;

alter table food_groups_feedback_nutrient_ids
    add constraint food_groups_feedback_group_ids_food_group_id_fkey
        foreign key (nutrient_id) references nutrient_types(id);

insert into food_groups_feedback_nutrient_ids(food_groups_feedback_id, nutrient_id) values (1, 266), (1, 267) on conflict do nothing;

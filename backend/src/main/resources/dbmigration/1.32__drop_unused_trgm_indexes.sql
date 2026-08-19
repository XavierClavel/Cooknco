-- These indexes were never usable: search filters call similarity()/word_similarity()
-- on unaccent(<column>), and the planner can only use a trigram index for the
-- %/<% operators applied to the exact indexed expression (here: the raw column).
DROP INDEX IF EXISTS idx_recipe_name_trgm;
DROP INDEX IF EXISTS idx_ingredient_name_trgm;

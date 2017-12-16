class NamestringsFUnaccent < ActiveRecord::Migration
  def self.up
    execute "CREATE OR REPLACE FUNCTION f_unaccent(text)
               RETURNS text AS
             $func$
             SELECT public.unaccent('public.unaccent', $1)
             $func$  LANGUAGE sql IMMUTABLE"
    execute 'CREATE INDEX name_strings__name_unaccent
               ON name_strings (f_unaccent(name) text_pattern_ops);'
  end

  def self.down
    execute 'DROP INDEX name_strings__name_unaccent'
    execute 'DROP FUNCTION f_unaccent(text)'
  end
end

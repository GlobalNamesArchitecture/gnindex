class NamestringsIndicesCreateGinIndex < ActiveRecord::Migration
  def self.up
    execute 'CREATE INDEX namestrings_name__gin_index
             ON name_strings
             USING gin(name gin_trgm_ops)'
    execute 'CREATE INDEX namestrings_canonical__gin_index
             ON name_strings
             USING gin(canonical gin_trgm_ops)'
    execute 'CREATE INDEX ns_author_words__gin_index
             ON name_strings__author_words
             USING gin(author_word gin_trgm_ops)'
    execute 'CREATE INDEX ns_genus__gin_index
             ON name_strings__genus
             USING gin(genus gin_trgm_ops)'
    execute 'CREATE INDEX ns_species__gin_index
             ON name_strings__species
             USING gin(species gin_trgm_ops)'
    execute 'CREATE INDEX ns_subspecies__gin_index
             ON name_strings__subspecies
             USING gin(subspecies gin_trgm_ops)'
    execute 'CREATE INDEX ns_uninomial__gin_index
             ON name_strings__uninomial
             USING gin(uninomial gin_trgm_ops)'
    execute 'CREATE INDEX ns_year__gin_index
             ON name_strings__year
             USING gin(year gin_trgm_ops)'
  end
  def self.down
    execute 'DROP INDEX namestrings_name__gin_index'
    execute 'DROP INDEX namestrings_canonical__gin_index'
    execute 'DROP INDEX ns_author_words__gin_index'
    execute 'DROP INDEX ns_genus__gin_index'
    execute 'DROP INDEX ns_species__gin_index'
    execute 'DROP INDEX ns_subspecies__gin_index'
    execute 'DROP INDEX ns_uninomial__gin_index'
    execute 'DROP INDEX ns_year__gin_index'
  end
end

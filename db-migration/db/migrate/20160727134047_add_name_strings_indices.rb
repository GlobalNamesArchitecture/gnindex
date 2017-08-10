class AddNameStringsIndices < ActiveRecord::Migration
  def up
    execute 'CREATE INDEX canonical_name_index ON name_strings(canonical text_pattern_ops)'
  end
  def down
    execute 'DROP INDEX IF EXISTS canonical_name_index'
  end
end

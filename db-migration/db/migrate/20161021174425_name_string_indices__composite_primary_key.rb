class NameStringIndicesCompositePrimaryKey < ActiveRecord::Migration
  def up
    execute 'ALTER TABLE name_string_indices
             ADD PRIMARY KEY (name_string_id, data_source_id, taxon_id)'
  end
  def down
    execute 'ALTER TABLE name_string_indices DROP CONSTRAINT name_string_indices_pkey'
  end
end

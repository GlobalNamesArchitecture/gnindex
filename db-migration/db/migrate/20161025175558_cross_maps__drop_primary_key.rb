class CrossMapsDropPrimaryKey < ActiveRecord::Migration
  def up
    execute 'ALTER TABLE cross_maps DROP CONSTRAINT cross_maps_pkey'
  end
  def down
    execute 'ALTER TABLE cross_maps
             ADD PRIMARY KEY (data_source_id, name_string_id, taxon_id)'
  end
end

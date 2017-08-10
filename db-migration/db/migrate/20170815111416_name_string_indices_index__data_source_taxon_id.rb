class NameStringIndicesIndexDataSourceTaxonId < ActiveRecord::Migration
  def change
    add_index :name_string_indices, [:data_source_id, :taxon_id], using: 'btree'
  end
end

class CrossMapsAddIndices < ActiveRecord::Migration
  def change
    add_index :cross_maps, [:data_source_id, :name_string_id, :taxon_id], using: 'btree',
              name: 'index__nsid_dsid_tid'
    add_index :cross_maps, [:cm_data_source_id, :cm_local_id], using: 'btree',
              name: 'index__cmdsid_clid'
    remove_index :cross_maps, :data_source_id
    remove_index :cross_maps, :name_string_id
  end
end

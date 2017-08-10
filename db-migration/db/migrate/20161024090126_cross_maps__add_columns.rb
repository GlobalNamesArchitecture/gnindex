class CrossMapsAddColumns < ActiveRecord::Migration
  def change
    change_table :cross_maps do |t|
      t.integer :cm_data_source_id, null: false
      t.string :taxon_id, limit: 255, null: false
    end
    rename_column :cross_maps, :local_id, :cm_local_id
  end
end

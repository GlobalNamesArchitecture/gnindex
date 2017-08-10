class CreateCrossMap < ActiveRecord::Migration
  def change
    create_table :cross_maps, id: false do |t|
      t.integer :data_source_id, null: false
      t.uuid :name_string_id, null: false
      t.string :local_id, null: false, limit: 50
    end
    add_index :cross_maps, :data_source_id, using: 'btree'
    add_index :cross_maps, :name_string_id, using: 'btree'
  end
end

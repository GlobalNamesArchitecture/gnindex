class CreateNameStringIndices < ActiveRecord::Migration
  def change
    create_table :name_string_indices, id: false do |t|
      t.integer :data_source_id, null: false
      t.uuid :name_string_id, null: false
      t.string :url, limit: 255
    end
    add_index :name_string_indices, :data_source_id, using: 'btree'
    add_index :name_string_indices, :name_string_id, using: 'btree'
  end
end

class CreateVernacularsTables < ActiveRecord::Migration
  def change
    create_table :vernacular_strings, id: false do |t|
      t.uuid :id, null: false, primary_key: true
      t.string :name, null: false, limit: 255
    end

    create_table :vernacular_string_indices, id: false do |t|
      t.integer :data_source_id, null: false
      t.string :taxon_id, limit: 255, null: false
      t.uuid :vernacular_string_id, null: false
      t.string :language, limit: 255, null: false
      t.string :locality, limit: 255, null: false
      t.string :country_code, limit: 255, null: false
    end

    add_index :vernacular_string_indices, [:data_source_id, :taxon_id],
              using: 'btree', name: 'index__dsid_tid'
    add_index :vernacular_string_indices, :vernacular_string_id,
              using: 'btree', name: 'index__vsid'
  end
end

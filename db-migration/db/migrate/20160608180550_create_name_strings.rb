
class CreateNameStrings < ActiveRecord::Migration
  def change
    create_table :name_strings, id: false do |t|
      t.uuid :id, null: false, primary_key: true
      t.uuid :id_mysql, null: false
      t.string :name, null: false, limit: 255
      t.uuid :canonical_uuid
      t.string :canonical, limit: 255
      t.timestamps
    end
    add_index :name_strings, :canonical_uuid, using: 'btree'
  end
end

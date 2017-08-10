class CreateNameStringIndexWords < ActiveRecord::Migration
  def change
    create_table :name_strings__author_words, id: false do |t|
      t.string :author_word, null: false, limit: 100
      t.uuid :name_uuid, null: false
    end
    create_table :name_strings__year, id: false do |t|
      t.string :year, null: false, limit: 8
      t.uuid :name_uuid, null: false
    end
    create_table :name_strings__genus, id: false do |t|
      t.string :genus, null: false, limit: 50
      t.uuid :name_uuid, null: false
    end
    create_table :name_strings__uninomial, id: false do |t|
      t.string :uninomial, null: false, limit: 50
      t.uuid :name_uuid, null: false
    end
    create_table :name_strings__species, id: false do |t|
      t.string :species, null: false, limit: 50
      t.uuid :name_uuid, null: false
    end
    create_table :name_strings__subspecies, id: false do |t|
      t.string :subspecies, null: false, limit: 50
      t.uuid :name_uuid, null: false
    end
    add_index :name_strings__author_words, :author_word, using: 'btree'
    add_index :name_strings__year, :year, using: 'btree'
    add_index :name_strings__genus, :genus, using: 'btree'
    add_index :name_strings__uninomial, :uninomial, using: 'btree'
    add_index :name_strings__species, :species, using: 'btree'
    add_index :name_strings__subspecies, :subspecies, using: 'btree'
  end
end

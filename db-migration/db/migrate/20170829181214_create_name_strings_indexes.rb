class CreateNameStringsIndexes < ActiveRecord::Migration
  def change
    add_index :name_strings__author_words, :name_uuid, using: 'btree'
    add_index :name_strings__genus, :name_uuid, using: 'btree'
    add_index :name_strings__species, :name_uuid, using: 'btree'
    add_index :name_strings__subspecies, :name_uuid, using: 'btree'
    add_index :name_strings__uninomial, :name_uuid, using: 'btree'
    add_index :name_strings__year, :name_uuid, using: 'btree'
  end
end

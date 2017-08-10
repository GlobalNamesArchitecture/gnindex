class NameStringIndicesAddColums < ActiveRecord::Migration
  def change
    change_table :name_string_indices do |t|
      t.string :taxon_id, limit: 255, null: false
      t.string :global_id, limit: 255
      t.string :local_id, limit: 255
      t.integer :nomenclatural_code_id
      t.string :rank, limit: 255
      t.string :accepted_taxon_id, limit: 255
      t.text :classification_path
      t.text :classification_path_ids
      t.text :classification_path_ranks
    end
  end
end

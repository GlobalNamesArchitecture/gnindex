class DatasourcesQuality < ActiveRecord::Migration
  def change
    change_table :data_sources do |t|
      t.boolean :is_curated
      t.boolean :is_auto_curated
      t.integer :record_count
    end
  end
end

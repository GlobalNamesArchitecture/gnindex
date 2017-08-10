class CreateDataSources < ActiveRecord::Migration
  def change
    create_table :data_sources do |t|
      t.string :title, null: false, limit: 255
      t.text :description
      t.string :logo_url, limit: 255
      t.string :web_site_url, limit: 255
      t.string :data_url, limit: 255
      t.integer :refresh_period_days, default: 14
      t.integer :name_strings_count, default: 0
      t.string :data_hash, limit: 40
      t.integer :unique_names_count, default: 0
      t.timestamps
    end
  end
end

class NamestringsAddParsingQuality < ActiveRecord::Migration
  def change
    change_table :name_strings do |t|
      t.integer :parsing_quality, :limit => 1
    end
  end
end

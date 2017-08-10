class NameStringsAddSurrogate < ActiveRecord::Migration
  def change
    change_table :name_strings do |t|
      t.boolean :surrogate
    end
  end
end

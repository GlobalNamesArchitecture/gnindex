class NameStringsDropRedundant < ActiveRecord::Migration
  def change
    change_table :name_strings do |t|
      t.remove :id_mysql
      t.remove_timestamps
    end
  end
end

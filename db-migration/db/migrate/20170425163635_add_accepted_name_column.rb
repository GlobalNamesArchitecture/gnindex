class AddAcceptedNameColumn < ActiveRecord::Migration
  def change
    change_table :name_string_indices do |t|
      t.uuid :accepted_name_uuid
      t.string :accepted_name, limit: 255
    end
  end
end

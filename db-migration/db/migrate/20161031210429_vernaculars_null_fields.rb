class VernacularsNullFields < ActiveRecord::Migration
  def change
    change_column_null :vernacular_string_indices, :language, true
    change_column_null :vernacular_string_indices, :locality, true
    change_column_null :vernacular_string_indices, :country_code, true
  end
end

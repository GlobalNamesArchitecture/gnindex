class NamestringsAddCanonicalRanked < ActiveRecord::Migration
  def change
    change_table :name_strings do |t|
      t.string :canonical_ranked, limit: 255
    end
  end
end

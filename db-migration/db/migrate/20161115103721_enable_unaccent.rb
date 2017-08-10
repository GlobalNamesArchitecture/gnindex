class EnableUnaccent < ActiveRecord::Migration
  def self.up
    enable_extension "unaccent"
  end
  def self.down
    disable_extension "unaccent"
  end
end

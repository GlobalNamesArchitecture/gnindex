class DatasourcesColumnsToNotnullable < ActiveRecord::Migration
  def change
    change_column_null :data_sources, :is_curated, false
    change_column_null :data_sources, :is_auto_curated, false
    change_column_null :data_sources, :record_count, false
  end
end

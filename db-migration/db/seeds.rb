require 'csv'

ENV['SPEC_NAME'] ||= '.'

unless [:development, :test_api, :test_resolver, :production].include? ENV['RACK_ENV'].to_sym
  puts 'Use: bundle exec rake db:seed RACK_ENV=[test_api|test_resolver|development|production]'
  exit
end

class Seeder
  attr :spec_dir, :env_dir, :common_dir

  def initialize
    @db = ActiveRecord::Base.connection
    @common_dir = File.join(__dir__, 'seed')
    @env_dir = File.join(@common_dir, ENV['RACK_ENV'])
    @spec_dir = File.join(@env_dir, ENV['SPEC_NAME'])
    @path = @columns = nil
  end

  def truncate_all
    @db.tables.each do |table_name|
      if table_name != 'schema_migrations'
        puts 'truncate table %s' % table_name
        @db.execute('truncate table %s;' % table_name)
      end
    end
  end

  def walk_path(path)
    @path = path
    files = Dir.entries(@path).map { |e| e.to_s }.select { |e| e.match /csv$/ }
    puts("Files: #{files}")
    files.each do |file|
      add_seeds(file)
    end
  rescue ActiveRecord::StatementInvalid
    fail "\nBefore adding seeds run:\n" \
           "bundle exec rake db:migrate RACK_ENV=...\n\n"
  end

  private

  def add_seeds(file)
    table = file.gsub(/\.csv/, '')
    data_slice_for table, file do |data|
      @db.execute('insert into %s values %s on conflict do nothing;' % [table, data]) if data
    end
  end

  def data_slice_for(table, file)
    all_data = collect_data(file, table)
    all_data.each_slice(1_000) do |s|
      data = s.empty? ? nil : "(#{s.join('), (')})"
      yield data
    end
  end

  def collect_data(file, table)
    @columns = @db.columns(table).map(&:name)
    csv_args = { col_sep: "\t", quote_char: 'щ', encoding: 'utf-8' }
    puts '*' * 80
    puts file
    CSV.open(File.join(@path, file), csv_args).map do |row|
      row = get_row(row, table)
      (@columns.size - row.size).times { row << 'null' }
      row.join(',')
    end
  end

  def get_row(row, table)
    row.each_with_object([]) do |field, ary|
      value = (field == "\\N") ? 'null' : @db.quote(field)
      ary << value
    end
  end
end

s = Seeder.new
s.truncate_all
s.walk_path(s.common_dir)
s.walk_path(s.env_dir)
s.walk_path(s.spec_dir)
puts 'You added seeds data to %s tables' % ENV['RACK_ENV'].upcase

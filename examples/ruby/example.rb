#!/usr/bin/env ruby

# Delete schema.json if schema changed

require 'graphlient'

GRAPHQL_URL = 'https://index.globalnames.org/api/graphql'

schema_path = File.join(__dir__, 'schema.json')

names = [
  'Tanygnathus gramineus (Gmelin, 1788)',
  'Myiornis atricapillus (Lawrence, 1875)',
  'Eremomela pusilla Hartlaub, 1857',
  'Garrulax ngoclinhensis Eames, Le Trong Trai & Nguyen Cu, 1999',
  'Celeus castaneus (Wagler, 1829)',
  'Pitta angolensis Vieillot, 1816',
  'Calamanthus cautus',
  'Apalis goslingi Alexander, 1908',
  'Amazona arausiaca (Müller, 1776)',
  'Oporornis tolmiei (Townsend, 1839)',
  'Harpactes reinwardtii (Temminck, 1822)',
  'Micropsitta bruijnii (Salvadori, 1875)',
  'Percnostola arenarum Isler, Alvarez Alonso, Isler & Whitney, 2001',
  'Strix aluco Linnaeus, 1758',
  'Urochroa bougueri (Bourcier, 1851)',
  'Amaurospiza concolor Cabanis, 1861',
  'Calcarius ornatus (Townsend, 1837)',
  'Coracina temminckii (Müller, 1843)',
  'Lagopus leucurus'
]

variables = { sources: [1, 4, 19, 11, 179],
              names: names.map { |n| { value: n } }
            }

client = Graphlient::Client.new(GRAPHQL_URL, schema_path: schema_path)

client.schema.dump! unless File.exist?(schema_path)

query = <<-QUERY
 query($names: [name!]!, $sources: [Int!]!) {
   nameResolver(names: $names, dataSourceIds: $sources) {
     responses {
       total
       suppliedInput
       results {
         name {value}
         dataSource{id title}
         vernaculars {
           name
           language
         }
       }
     }
   }
 }
QUERY

res = client.query(query, variables)

puts res.errors.blank? ? "\nQuery was successful\n\n" : "\nQuery failed\n\n"

puts "Results can be accessed using following methods:\n\n"

puts res.public_methods(false)

puts "\n\nAn example of traversing data:\n\n"

data = []
res.data.name_resolver.responses.each do |resp|
  next if resp.total.zero?

  datum = { name: nil, common: [] }

  resp.results.each do |rr|
    datum[:name] = rr.name.value
    rr.vernaculars.each do |vern|
      datum[:common] << { value: vern.name, lang: vern.language }
    end
  end
  data << datum
end

data.each do |d|
  puts d[:name]
  d[:common].each do |c|
    puts "  #{c[:value]}(#{c[:lang]})"
  end
end




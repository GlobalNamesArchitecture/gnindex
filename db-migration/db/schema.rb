# encoding: UTF-8
# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# Note that this schema.rb definition is the authoritative source for your
# database schema. If you need to create the application database on another
# system, you should be using db:schema:load, not running all the migrations
# from scratch. The latter is a flawed and unsustainable approach (the more migrations
# you'll amass, the slower it'll run and the greater likelihood for issues).
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema.define(version: 20170425163635) do

  # These are extensions that must be enabled in order to support this database
  enable_extension "plpgsql"
  enable_extension "unaccent"

  create_table "cross_maps", id: false, force: :cascade do |t|
    t.integer "data_source_id",                null: false
    t.uuid    "name_string_id",                null: false
    t.string  "cm_local_id",       limit: 50,  null: false
    t.integer "cm_data_source_id",             null: false
    t.string  "taxon_id",          limit: 255, null: false
  end

  add_index "cross_maps", ["cm_data_source_id", "cm_local_id"], name: "index__cmdsid_clid", using: :btree
  add_index "cross_maps", ["data_source_id", "name_string_id", "taxon_id"], name: "index__nsid_dsid_tid", using: :btree

  create_table "data_sources", force: :cascade do |t|
    t.string   "title",               limit: 255,              null: false
    t.text     "description"
    t.string   "logo_url",            limit: 255
    t.string   "web_site_url",        limit: 255
    t.string   "data_url",            limit: 255
    t.integer  "refresh_period_days",             default: 14
    t.integer  "name_strings_count",              default: 0
    t.string   "data_hash",           limit: 40
    t.integer  "unique_names_count",              default: 0
    t.datetime "created_at"
    t.datetime "updated_at"
  end

  create_table "name_string_indices", id: false, force: :cascade do |t|
    t.integer "data_source_id",                        null: false
    t.uuid    "name_string_id",                        null: false
    t.string  "url",                       limit: 255
    t.string  "taxon_id",                  limit: 255, null: false
    t.string  "global_id",                 limit: 255
    t.string  "local_id",                  limit: 255
    t.integer "nomenclatural_code_id"
    t.string  "rank",                      limit: 255
    t.string  "accepted_taxon_id",         limit: 255
    t.text    "classification_path"
    t.text    "classification_path_ids"
    t.text    "classification_path_ranks"
    t.uuid    "accepted_name_uuid"
    t.string  "accepted_name",             limit: 255
  end

  add_index "name_string_indices", ["data_source_id"], name: "index_name_string_indices_on_data_source_id", using: :btree
  add_index "name_string_indices", ["name_string_id"], name: "index_name_string_indices_on_name_string_id", using: :btree

  create_table "name_strings", id: :uuid, default: nil, force: :cascade do |t|
    t.string  "name",           limit: 255, null: false
    t.uuid    "canonical_uuid"
    t.string  "canonical",      limit: 255
    t.boolean "surrogate"
  end

  add_index "name_strings", ["canonical"], name: "canonical_name_index", using: :btree
  add_index "name_strings", ["canonical_uuid"], name: "index_name_strings_on_canonical_uuid", using: :btree

  create_table "name_strings__author_words", id: false, force: :cascade do |t|
    t.string "author_word", limit: 100, null: false
    t.uuid   "name_uuid",               null: false
  end

  add_index "name_strings__author_words", ["author_word"], name: "index_name_strings__author_words_on_author_word", using: :btree

  create_table "name_strings__genus", id: false, force: :cascade do |t|
    t.string "genus",     limit: 50, null: false
    t.uuid   "name_uuid",            null: false
  end

  add_index "name_strings__genus", ["genus"], name: "index_name_strings__genus_on_genus", using: :btree

  create_table "name_strings__species", id: false, force: :cascade do |t|
    t.string "species",   limit: 50, null: false
    t.uuid   "name_uuid",            null: false
  end

  add_index "name_strings__species", ["species"], name: "index_name_strings__species_on_species", using: :btree

  create_table "name_strings__subspecies", id: false, force: :cascade do |t|
    t.string "subspecies", limit: 50, null: false
    t.uuid   "name_uuid",             null: false
  end

  add_index "name_strings__subspecies", ["subspecies"], name: "index_name_strings__subspecies_on_subspecies", using: :btree

  create_table "name_strings__uninomial", id: false, force: :cascade do |t|
    t.string "uninomial", limit: 50, null: false
    t.uuid   "name_uuid",            null: false
  end

  add_index "name_strings__uninomial", ["uninomial"], name: "index_name_strings__uninomial_on_uninomial", using: :btree

  create_table "name_strings__year", id: false, force: :cascade do |t|
    t.string "year",      limit: 8, null: false
    t.uuid   "name_uuid",           null: false
  end

  add_index "name_strings__year", ["year"], name: "index_name_strings__year_on_year", using: :btree

  create_table "vernacular_string_indices", id: false, force: :cascade do |t|
    t.integer "data_source_id",                   null: false
    t.string  "taxon_id",             limit: 255, null: false
    t.uuid    "vernacular_string_id",             null: false
    t.string  "language",             limit: 255
    t.string  "locality",             limit: 255
    t.string  "country_code",         limit: 255
  end

  add_index "vernacular_string_indices", ["data_source_id", "taxon_id"], name: "index__dsid_tid", using: :btree
  add_index "vernacular_string_indices", ["vernacular_string_id"], name: "index__vsid", using: :btree

  create_table "vernacular_strings", id: :uuid, default: nil, force: :cascade do |t|
    t.string "name", limit: 255, null: false
  end

end

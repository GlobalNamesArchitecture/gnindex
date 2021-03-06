--
-- PostgreSQL database dump
--

-- Dumped from database version 9.6.8 (Homebrew petere/postgresql)
-- Dumped by pg_dump version 9.6.8 (Homebrew petere/postgresql)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: plpgsql; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS plpgsql WITH SCHEMA pg_catalog;


--
-- Name: EXTENSION plpgsql; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION plpgsql IS 'PL/pgSQL procedural language';


--
-- Name: pg_trgm; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;


--
-- Name: EXTENSION pg_trgm; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION pg_trgm IS 'text similarity measurement and index searching based on trigrams';


--
-- Name: unaccent; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;


--
-- Name: EXTENSION unaccent; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON EXTENSION unaccent IS 'text search dictionary that removes accents';


--
-- Name: f_unaccent(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $_$
             SELECT public.unaccent('public.unaccent', $1)
             $_$;


SET default_tablespace = '';

SET default_with_oids = false;

--
-- Name: cross_maps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cross_maps (
    data_source_id integer NOT NULL,
    name_string_id uuid NOT NULL,
    cm_local_id character varying(50) NOT NULL,
    cm_data_source_id integer NOT NULL,
    taxon_id character varying(255) NOT NULL
);


--
-- Name: data_sources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.data_sources (
    id integer NOT NULL,
    title character varying(255) NOT NULL,
    description text,
    logo_url character varying(255),
    web_site_url character varying(255),
    data_url character varying(255),
    refresh_period_days integer DEFAULT 14,
    name_strings_count integer DEFAULT 0,
    data_hash character varying(40),
    unique_names_count integer DEFAULT 0,
    created_at timestamp without time zone,
    updated_at timestamp without time zone,
    is_curated boolean NOT NULL,
    is_auto_curated boolean NOT NULL,
    record_count integer NOT NULL
);


--
-- Name: data_sources_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.data_sources_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: data_sources_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.data_sources_id_seq OWNED BY public.data_sources.id;


--
-- Name: name_string_indices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_string_indices (
    data_source_id integer NOT NULL,
    name_string_id uuid NOT NULL,
    url character varying(255),
    taxon_id character varying(255) NOT NULL,
    global_id character varying(255),
    local_id character varying(255),
    nomenclatural_code_id integer,
    rank character varying(255),
    accepted_taxon_id character varying(255),
    classification_path text,
    classification_path_ids text,
    classification_path_ranks text,
    accepted_name_uuid uuid,
    accepted_name character varying(255)
);


--
-- Name: name_strings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    canonical_uuid uuid,
    canonical character varying(255),
    surrogate boolean,
    canonical_ranked character varying(255)
);


--
-- Name: name_strings__author_words; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings__author_words (
    author_word character varying(100) NOT NULL,
    name_uuid uuid NOT NULL
);


--
-- Name: name_strings__genus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings__genus (
    genus character varying(50) NOT NULL,
    name_uuid uuid NOT NULL
);


--
-- Name: name_strings__species; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings__species (
    species character varying(50) NOT NULL,
    name_uuid uuid NOT NULL
);


--
-- Name: name_strings__subspecies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings__subspecies (
    subspecies character varying(50) NOT NULL,
    name_uuid uuid NOT NULL
);


--
-- Name: name_strings__uninomial; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings__uninomial (
    uninomial character varying(50) NOT NULL,
    name_uuid uuid NOT NULL
);


--
-- Name: name_strings__year; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.name_strings__year (
    year character varying(8) NOT NULL,
    name_uuid uuid NOT NULL
);


--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_migrations (
    version character varying NOT NULL
);


--
-- Name: vernacular_string_indices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vernacular_string_indices (
    data_source_id integer NOT NULL,
    taxon_id character varying(255) NOT NULL,
    vernacular_string_id uuid NOT NULL,
    language character varying(255),
    locality character varying(255),
    country_code character varying(255)
);


--
-- Name: vernacular_strings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vernacular_strings (
    id uuid NOT NULL,
    name character varying(255) NOT NULL
);


--
-- Name: data_sources id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_sources ALTER COLUMN id SET DEFAULT nextval('public.data_sources_id_seq'::regclass);


--
-- Name: data_sources data_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.data_sources
    ADD CONSTRAINT data_sources_pkey PRIMARY KEY (id);


--
-- Name: name_string_indices name_string_indices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.name_string_indices
    ADD CONSTRAINT name_string_indices_pkey PRIMARY KEY (name_string_id, data_source_id, taxon_id);


--
-- Name: name_strings name_strings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.name_strings
    ADD CONSTRAINT name_strings_pkey PRIMARY KEY (id);


--
-- Name: vernacular_strings vernacular_strings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vernacular_strings
    ADD CONSTRAINT vernacular_strings_pkey PRIMARY KEY (id);


--
-- Name: canonical_name_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX canonical_name_index ON public.name_strings USING btree (canonical text_pattern_ops);


--
-- Name: index__cmdsid_clid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index__cmdsid_clid ON public.cross_maps USING btree (cm_data_source_id, cm_local_id);


--
-- Name: index__dsid_tid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index__dsid_tid ON public.vernacular_string_indices USING btree (data_source_id, taxon_id);


--
-- Name: index__nsid_dsid_tid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index__nsid_dsid_tid ON public.cross_maps USING btree (data_source_id, name_string_id, taxon_id);


--
-- Name: index__vsid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index__vsid ON public.vernacular_string_indices USING btree (vernacular_string_id);


--
-- Name: index_name_string_indices_on_data_source_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_string_indices_on_data_source_id ON public.name_string_indices USING btree (data_source_id);


--
-- Name: index_name_string_indices_on_data_source_id_and_taxon_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_string_indices_on_data_source_id_and_taxon_id ON public.name_string_indices USING btree (data_source_id, taxon_id);


--
-- Name: index_name_string_indices_on_name_string_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_string_indices_on_name_string_id ON public.name_string_indices USING btree (name_string_id);


--
-- Name: index_name_strings__author_words_on_author_word; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__author_words_on_author_word ON public.name_strings__author_words USING btree (author_word);


--
-- Name: index_name_strings__author_words_on_name_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__author_words_on_name_uuid ON public.name_strings__author_words USING btree (name_uuid);


--
-- Name: index_name_strings__genus_on_genus; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__genus_on_genus ON public.name_strings__genus USING btree (genus);


--
-- Name: index_name_strings__genus_on_name_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__genus_on_name_uuid ON public.name_strings__genus USING btree (name_uuid);


--
-- Name: index_name_strings__species_on_name_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__species_on_name_uuid ON public.name_strings__species USING btree (name_uuid);


--
-- Name: index_name_strings__species_on_species; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__species_on_species ON public.name_strings__species USING btree (species);


--
-- Name: index_name_strings__subspecies_on_name_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__subspecies_on_name_uuid ON public.name_strings__subspecies USING btree (name_uuid);


--
-- Name: index_name_strings__subspecies_on_subspecies; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__subspecies_on_subspecies ON public.name_strings__subspecies USING btree (subspecies);


--
-- Name: index_name_strings__uninomial_on_name_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__uninomial_on_name_uuid ON public.name_strings__uninomial USING btree (name_uuid);


--
-- Name: index_name_strings__uninomial_on_uninomial; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__uninomial_on_uninomial ON public.name_strings__uninomial USING btree (uninomial);


--
-- Name: index_name_strings__year_on_name_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__year_on_name_uuid ON public.name_strings__year USING btree (name_uuid);


--
-- Name: index_name_strings__year_on_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings__year_on_year ON public.name_strings__year USING btree (year);


--
-- Name: index_name_strings_on_canonical_uuid; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX index_name_strings_on_canonical_uuid ON public.name_strings USING btree (canonical_uuid);


--
-- Name: name_strings__name_unaccent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX name_strings__name_unaccent ON public.name_strings USING btree (public.f_unaccent((name)::text) text_pattern_ops);


--
-- Name: namestrings_canonical__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX namestrings_canonical__gin_index ON public.name_strings USING gin (canonical public.gin_trgm_ops);


--
-- Name: namestrings_name__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX namestrings_name__gin_index ON public.name_strings USING gin (name public.gin_trgm_ops);


--
-- Name: ns_author_words__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ns_author_words__gin_index ON public.name_strings__author_words USING gin (author_word public.gin_trgm_ops);


--
-- Name: ns_genus__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ns_genus__gin_index ON public.name_strings__genus USING gin (genus public.gin_trgm_ops);


--
-- Name: ns_species__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ns_species__gin_index ON public.name_strings__species USING gin (species public.gin_trgm_ops);


--
-- Name: ns_subspecies__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ns_subspecies__gin_index ON public.name_strings__subspecies USING gin (subspecies public.gin_trgm_ops);


--
-- Name: ns_uninomial__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ns_uninomial__gin_index ON public.name_strings__uninomial USING gin (uninomial public.gin_trgm_ops);


--
-- Name: ns_year__gin_index; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ns_year__gin_index ON public.name_strings__year USING gin (year public.gin_trgm_ops);


--
-- Name: unique_schema_migrations; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX unique_schema_migrations ON public.schema_migrations USING btree (version);


--
-- PostgreSQL database dump complete
--

SET search_path TO "$user", public;

INSERT INTO schema_migrations (version) VALUES ('20160608180550');

INSERT INTO schema_migrations (version) VALUES ('20160609145003');

INSERT INTO schema_migrations (version) VALUES ('20160609164952');

INSERT INTO schema_migrations (version) VALUES ('20160609173359');

INSERT INTO schema_migrations (version) VALUES ('20160609175652');

INSERT INTO schema_migrations (version) VALUES ('20160727134047');

INSERT INTO schema_migrations (version) VALUES ('20160802125615');

INSERT INTO schema_migrations (version) VALUES ('20161017094344');

INSERT INTO schema_migrations (version) VALUES ('20161021174425');

INSERT INTO schema_migrations (version) VALUES ('20161024090126');

INSERT INTO schema_migrations (version) VALUES ('20161024090718');

INSERT INTO schema_migrations (version) VALUES ('20161025092539');

INSERT INTO schema_migrations (version) VALUES ('20161025175558');

INSERT INTO schema_migrations (version) VALUES ('20161025175634');

INSERT INTO schema_migrations (version) VALUES ('20161026175634');

INSERT INTO schema_migrations (version) VALUES ('20161031210429');

INSERT INTO schema_migrations (version) VALUES ('20161115103721');

INSERT INTO schema_migrations (version) VALUES ('20170425163635');

INSERT INTO schema_migrations (version) VALUES ('20170815111416');

INSERT INTO schema_migrations (version) VALUES ('20170829181214');

INSERT INTO schema_migrations (version) VALUES ('20170912134714');

INSERT INTO schema_migrations (version) VALUES ('20170916090813');

INSERT INTO schema_migrations (version) VALUES ('20170916133024');

INSERT INTO schema_migrations (version) VALUES ('20171002094757');

INSERT INTO schema_migrations (version) VALUES ('20171002165405');

INSERT INTO schema_migrations (version) VALUES ('20171204101828');


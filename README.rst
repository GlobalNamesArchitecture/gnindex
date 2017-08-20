gnindex
=======

Brief Info
----------

``gnindex`` is a search engine over collection of strings (combinations of
characters) that have been used as names for organisms. ``gnindex`` contains
many examples of names spelled in slightly different ways. It indexes
occurances of biological scientific names in the world, normalizes and
reconsiles lexical and taxonomic variants of the name strings.

``gnindex`` handles two type of queries over indexed name strings: resolution
and faceted search.

Faceted search accepts part of name string (canonical part, author word(s),
genus word(s), species word(s), year) possibly wildcarded or their logically
combined composition. In response it returns all results that satisfy given
request.

Resolution accepts a set of whole name strings with resolution parameters. Parameters
includes:

- raw string supplied to name input ("a label")
- data source IDs where ``gnindex`` should perform resolution
- should names be resolved in respect to context
- should ``gnindex`` resolve a name with vernacular

Installation
------------

``gnindex`` is dockerized:

.. code:: bash

    docker-compose up

``docker-compose`` pulls all necessary Docker containers (Postgres database engine,
``gnindex`` matcher, nameResolver, facetedSearcher, api and front services), initializes
them, and listens for HTTP connections over 80 port with paths as follows:

- ``/`` path returns user-friendly HTML interface
- ``/api`` returns `GraphiQL <https://github.com/graphql/graphiql>`_ interactive
  in-browser environment to interact with ``gnindex`` API.

Project Structure
-----------------

The projects consists of 6 subprojects:
- ``common``
- ``matcher``
- ``nameResolver``
- ``facetedSearcher``
- ``api``
- ``front``

common
~~~~~~

Contains Thrift data structures that are shared between microservices.

matcher
~~~~~~~

The Thrift microservice expects canonical names of type ``Seq[String]`` and data sources IDs of
type ``Seq[Int]``. It tries to fuzzy match through all known canonical names and those stems 
(according to Latin stemming) with Levenstein algorithm of edit distance 1.

It returns list of lists of found fuzzy matches UUIDs: one list per provided canonical name. Note
that it returns UUIDs only as it has no connection to database.

nameResolver
~~~~~~~~~~~~

The Thrift microservice expects complex requests of type ``thrift.nameresolver.Request``. It passes
through stages for every provided name request:

1. Two UUIDv5 values are computed: first one is for provided name string, second one is for parsed
   canonical name. Then microservice tries to find records according to those two UUIDs
2. If nothing is found for exact match and name is not parsable, then empty result is returned
3. If provided name is parsable, then canonical form of the name goes to ``matcher`` microservice.
   Final results are formed based on database results matched by those UUIDs.

facetedSearcher
~~~~~~~~~~~~~~~

Performs faceted search.

api
~~~~

The ``api`` microservice is connected with ``nameresolver`` and ``facetedSearcher`` microservices.
It provides ``GraphQL`` interface to the user. ``GraphQL`` requests are then translated to
microservices requests.



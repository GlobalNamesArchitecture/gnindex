gnindex
=======

The projects consists of 6 subprojects:
- ``common``
- ``matcher``
- ``nameResolver``
- ``facetedSearcher``
- ``api``
- ``front``


common
------

Contains Thrift data structures that are shared between microservices.

matcher
-------

The Thrift microservice expects canonical names of type ``Seq[String]`` and data sources IDs of
type ``Seq[Int]``. It tries to fuzzy match through all known canonical names and those stems 
(according to Latin stemming) with Levenstein algorithm of edit distance 1.

It returns list of lists of found fuzzy matches UUIDs: one list per provided canonical name. Note
that it returns UUIDs only as it has no connection to database.

nameResolver
------------

The Thrift microservice expects complex requests of type ``thrift.nameresolver.Request``. It passes
through stages for every provided name request:

1. Two UUIDv5 values are computed: first one is for provided name string, second one is for parsed
   canonical name. Then microservice tries to find records according to those two UUIDs
2. If nothing is found for exact match and name is not parsable, then empty result is returned
3. If provided name is parsable, then canonical form of the name goes to ``matcher`` microservice.
   Final results are formed based on database results matched by those UUIDs.

facetedSearcher
---------------

Performs faceted search.

api
---

The ``api`` microservice is connected with ``nameresolver`` and ``facetedSearcher`` microservices.
It provides ``GraphQL`` interface to the user. ``GraphQL`` requests are then translated to
microservices requests.



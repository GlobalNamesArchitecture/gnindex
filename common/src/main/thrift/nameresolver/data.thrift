#@namespace scala org.globalnames.index.thrift.nameresolver

include "finatra-thrift/finatra_thrift_exceptions.thrift"
include "../data.thrift"

const i32 nameStringsMaxCount = 1000

struct NameInput {
    1: string value
    2: optional string suppliedId
}

struct Request {
    1: list<NameInput> names
    2: list<i32> dataSourceIds = []
    3: list<i32> preferredDataSourceIds = []
    4: i32 page = 0
    5: i32 perPage = nameStringsMaxCount
    6: bool withSurrogates = false
    7: bool withVernaculars = false
}

struct AuthorScore {
    1: string authorshipInput
    2: string authorshipMatch
    3: double value
}

struct Score {
    1: optional i32 nameType
    2: AuthorScore authorScore
    3: i32 parsingQuality
    4: optional double value
    5: optional string message
}

struct Classification {
    1: optional string path
    2: optional string pathIds
    3: optional string pathRanks
}

struct DataSource {
    1: i32 id
    2: string title
}

struct AcceptedName {
    1: data.Name name
    2: optional data.CanonicalName canonicalName
    3: string taxonId
    4: i32 dataSourceId
}

struct Result {
    1: data.Name name
    2: optional data.CanonicalName canonicalName
    3: bool synonym
    4: data.MatchType matchType
    5: string taxonId
    6: Classification classification
    7: DataSource dataSource
    8: optional string acceptedTaxonId
    9: optional AcceptedName acceptedName
}

struct ResultScored {
    1: Result result
    2: Score score
}

struct Response {
    1: i32 total
    2: optional string suppliedInput
    3: optional string suppliedId
    4: list<ResultScored> results
    5: list<ResultScored> preferredResults
}

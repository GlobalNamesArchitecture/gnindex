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
    3: i32 page = 0
    4: i32 perPage = nameStringsMaxCount
    5: bool withSurrogates = false
    6: bool withVernaculars = false
}

struct Name {
    1: data.Uuid uuid
    2: string value
}

enum MatchKind {
    UUIDLookup,
    ExactNameMatchByUUID,
    ExactNameMatchByString,
    ExactCanonicalNameMatchByUUID,
    ExactCanonicalNameMatchByString,
    FuzzyCanonicalMatch,
    FuzzyPartialMatch,
    ExactMatchPartialByGenus,
    ExactPartialMatch,
    Unknown
}

struct MatchType {
    1: MatchKind kind
    2: i32 editDistance
    3: i32 score
}

struct Classification {
    1: optional string path
    2: optional string pathIds
    3: optional string pathRanks
}

struct Result {
    1: Name name
    2: optional Name canonicalName
    3: bool synonym
    4: MatchType matchType
    5: string taxonId
    6: Classification classification
}

struct Response {
    1: i32 total
    2: optional string suppliedInput
    3: optional string suppliedId
    4: list<Result> results
}

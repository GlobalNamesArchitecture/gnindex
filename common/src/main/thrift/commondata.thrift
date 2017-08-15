#@namespace scala org.globalnames.index.thrift

struct Uuid {
  1: i64 leastSignificantBits
  2: i64 mostSignificantBits
}

struct Name {
    1: Uuid uuid
    2: string value
}

struct CanonicalName {
    1: Uuid uuid
    2: string value
    3: string valueRanked
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
    1: Name name
    2: optional CanonicalName canonicalName
    3: string taxonId
    4: i32 dataSourceId
}

struct Context {
    1: DataSource dataSource
    2: string clade
}

struct Result {
    1: Name name
    2: optional CanonicalName canonicalName
    3: bool synonym
    4: MatchType matchType
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

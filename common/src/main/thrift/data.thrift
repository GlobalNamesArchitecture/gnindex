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

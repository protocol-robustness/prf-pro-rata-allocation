# Changelog

## Candidate compatibility note

- Allocation row normalization and largest-remainder tie-breaking now follow
  the documented unsigned canonical-encoding byte order in both Clojure and
  Rust. Requests with row IDs whose typed-value order differs from encoded-byte
  order can produce different row order, tie winners, and committed roots.
  Consumers that persisted roots produced by the prior implementation must
  treat this correction as a compatibility change and qualify regenerated
  artifacts before adoption.

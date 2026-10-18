# PRF pro-rata allocation

This is a standalone repository candidate for the deterministic pro-rata allocation package. It contains the Clojure and Rust implementations, package-owned fixtures, the frozen portable V1 allocation contract, and a snapshot of the generic canonical codec required to build it.

The candidate is local and has no configured remote. Its canonical dependency is an explicit bundled source snapshot, not a published or independently versioned dependency. The package can be consumed locally from this repository; PRF should switch from its monorepo path only after dependency versions and integration parity are agreed.

## Scope

This repository owns allocation arithmetic and its versioned request, policy, result, witness, outcome, and realized-statement projections. It does not own protocol replay, Shortfall policy, custody, capital activation, withdrawals, or deployment authority. Host-specific execution and context derivation remain adapters in consuming projects.

V1 and successor allocation profiles must keep their existing schema versions and hash domains. A successor profile does not rewrite V1 identities.

## Layout

- `clj/`: Clojure API, implementation, fixtures, and conformance runner.
- `rust/`: Rust allocation and identity crate.
- `canonical/`: pinned source snapshot of the generic Clojure and Rust canonical codec.
- `SPEC.md`, `contract.edn`, and `ALLOCATION_V1.md`: frozen portable contract and allocation profile.
- `CANONICAL_HASH_SPEC_V1_BINARY_ENCODING_ABI.md`: canonical byte encoding contract.
- `PROTECTED_CAPITAL_PRO_RATA_V2_SEMANTICS.md`: upstream application semantics reference; it does not make V2 custody semantics part of this allocation package.

## Local verification commands

From the repository root:

```sh
(cd clj && clojure -M:conformance)
cargo test --workspace --locked --offline
```

The root Cargo workspace makes the Rust crate addressable as a Git dependency
at a pinned repository revision while retaining the canonical codec as an
explicit workspace member.

The checked-in release metadata records partial overall conformance. Rust V1 allocation tests are qualified in the source candidate; additional proof-verifier and production Solidity profiles remain outside this package qualification.

## PRF integration

PRF consumes the Clojure package as a local dependency during development. Its executor budgets, progress callbacks, context/lifecycle construction, evidence binding, and compatibility APIs stay in PRF. The allocation package remains the semantic owner of the arithmetic and frozen allocation projections.

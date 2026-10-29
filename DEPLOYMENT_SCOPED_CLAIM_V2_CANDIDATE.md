# Deployment-scoped entitlement claim V2 — candidate

**Status: candidate, not adopted.** This profile is an interoperability proposal
for the independent `prf-pro-rata-evm` implementation. It does not modify the
frozen allocation V1 identities or claim that the supplied chain-instance
identity is authenticated. Adoption requires semantic-owner review, a decision
on chain-instance authentication, and approval of these exact preimages and
vectors.

## Encoding

The current candidate uses Ethereum `keccak256(abi.encode(...))`. All fields
below are static ABI words. Domain strings are converted to `bytes32` with
`keccak256(UTF-8(domain))`. Addresses are left-padded to 32 bytes; unsigned
integers are 32-byte big-endian ABI words. Values outside `uint256` and proof
modes other than `0` (Requested) or `1` (Required) are rejected.

The table leaf preimage, in order, is:

1. `keccak256("prf.pro-rata-entitlement.deployment-scoped.v2")`
2. `keccak256("prf.pro-rata-entitlement.table-leaf.v2")`
3. chain ID
4. canonical `chain-instance-genesis.v1` root (SHA-256, 32 bytes)
5. effective allocator address
6. asset address
7. operation ID
8. allocation binding root
9. entitlement index
10. claim identity
11. beneficiary
12. amount in base units
13. policy profile
14. immutable minimum proof mode
15. opening salt

The allocation-claim leaf uses the same ordered fields and the
`prf.pro-rata-entitlement.claim-leaf.v2` domain, but excludes the opening salt.
This distinction is intentional: membership opening and claim identity are
separate commitments.

Merkle nodes are `keccak256(node-domain || left || right)` over three 32-byte
words. Child order is significant; an odd final node is paired with itself.
Table and allocation-claim roots wrap the member root, entitlement count and
total amount with their respective `table-root.v2` and `claim-root.v2` domains.

## Trust boundary still unresolved

The selected chain value is specifically PRF's `chain-instance-genesis.v1`
root, whose canonical preimage includes the protocol root, execution and
settlement chain IDs, control-plane and authority-adapter addresses and code
hashes, initial authority state, and initial configuration root. The
standalone contract accepts that root as an opaque constructor value; it does
not authenticate its source. A deployment adapter must independently derive
the canonical root, verify it against the sealed genesis contract and bind the
same value into the operation. The local EVM candidate binds its actual
`address(this)` at execution, while the cross-language fixture uses an
explicit allocator value to test the preimage. Matching vectors alone do not
qualify a deployment.

## Candidate vector and reproduction

The machine-readable vector shared by Clojure and Rust tests is
`clj/test/fixtures/deployment-scoped-entitlement-v2.candidate.properties`.
The EVM candidate keeps its corresponding JSON input fixture. All three
implementations independently derive the three-member leaves, ordered pair,
duplicated final node, member roots, root envelopes and index-2 sibling paths,
and compare them with the same golden values. The candidate fixture is test
material only and is excluded from the existing allocation release manifest.
This reproduces the proposed encoding; it does not itself adopt the profile or
authenticate the genesis-root input.

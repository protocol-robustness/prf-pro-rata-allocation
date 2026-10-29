(ns prf.pro-rata.deployment-scoped-claim
  "Candidate EVM deployment-scoped entitlement commitments.

  This additive profile is not adopted and does not alter existing allocation
  identities. Its encoding is Solidity ABI static-word encoding with Keccak-256."
  (:import [java.math BigInteger]
           [org.bouncycastle.crypto.digests KeccakDigest]))

(def profile-domain "prf.pro-rata-entitlement.deployment-scoped.v2")
(def table-leaf-domain "prf.pro-rata-entitlement.table-leaf.v2")
(def table-node-domain "prf.pro-rata-entitlement.table-node.v2")
(def table-root-domain "prf.pro-rata-entitlement.table-root.v2")
(def claim-leaf-domain "prf.pro-rata-entitlement.claim-leaf.v2")
(def claim-node-domain "prf.pro-rata-entitlement.claim-node.v2")
(def claim-root-domain "prf.pro-rata-entitlement.claim-root.v2")

(defn hex-bytes [value]
  (let [value (str value)
        value (if (.startsWith value "0x") (subs value 2) value)]
    (when (or (odd? (count value)) (not (re-matches #"[0-9a-fA-F]*" value)))
      (throw (ex-info "Invalid hexadecimal bytes" {:reason :invalid-hex})))
    (byte-array (map #(unchecked-byte (Integer/parseInt % 16))
                     (re-seq #".." value)))))

(defn- hex [^bytes bytes]
  (apply str (map #(format "%02x" (bit-and 0xff %)) bytes)))

(defn- keccak256 ^bytes [^bytes input]
  (let [digest (KeccakDigest. 256)
        output (byte-array 32)]
    (.update digest input 0 (alength input))
    (.doFinal digest output 0)
    output))

(defn- concat-bytes ^bytes [parts]
  (let [size (reduce + (map alength parts))
        out (byte-array size)]
    (loop [offset 0, remaining parts]
      (if-let [part (first remaining)]
        (do (System/arraycopy part 0 out offset (alength part))
            (recur (+ offset (alength part)) (next remaining)))
        out))))

(defn- bytes32 [value field]
  (let [bytes (if (string? value) (hex-bytes value) value)]
    (when-not (and (instance? (Class/forName "[B") bytes) (= 32 (alength ^bytes bytes)))
      (throw (ex-info "Expected bytes32" {:reason :invalid-bytes32 :field field})))
    bytes))

(defn- address-word [value field]
  (let [address (if (string? value) (hex-bytes value) value)]
    (when-not (and (instance? (Class/forName "[B") address) (= 20 (alength ^bytes address)))
      (throw (ex-info "Expected 20-byte address" {:reason :invalid-address :field field})))
    (concat-bytes [(byte-array 12) address])))

(defn- uint256-word [value field]
  (let [^BigInteger n (cond
                        (instance? BigInteger value) value
                        (integer? value) (biginteger value)
                        :else (throw (ex-info "Expected integer" {:reason :invalid-uint256 :field field})))]
    (when (or (neg? (.signum n)) (> (.bitLength n) 256))
      (throw (ex-info "Integer outside uint256" {:reason :uint256-out-of-range :field field})))
    (let [raw (.toByteArray n)
          raw (if (and (> (alength raw) 1) (zero? (aget raw 0)))
                (java.util.Arrays/copyOfRange raw 1 (alength raw)) raw)
          word (byte-array 32)]
      (System/arraycopy raw 0 word (- 32 (alength raw)) (alength raw))
      word)))

(defn- word-hash [^bytes bytes]
  (keccak256 bytes))

(defn- context-words [context opening leaf-domain]
  (let [mode (:immutable-minimum-proof-mode context)]
    (when-not (contains? #{0 1} mode)
      (throw (ex-info "Unsupported proof mode" {:reason :unsupported-proof-mode})))
    [(word-hash (.getBytes profile-domain java.nio.charset.StandardCharsets/UTF_8))
     (word-hash (.getBytes leaf-domain java.nio.charset.StandardCharsets/UTF_8))
     (uint256-word (:chain-id context) :chain-id)
     (bytes32 (:chain-instance-identity context) :chain-instance-identity)
     (address-word (:allocator context) :allocator)
     (address-word (:asset context) :asset)
     (bytes32 (:operation-id context) :operation-id)
     (bytes32 (:allocation-binding-root context) :allocation-binding-root)
     (uint256-word (:index opening) :index)
     (bytes32 (:claim-identity opening) :claim-identity)
     (address-word (:beneficiary opening) :beneficiary)
     (uint256-word (:amount-base-units opening) :amount-base-units)
     (bytes32 (:policy-profile context) :policy-profile)
     (uint256-word mode :immutable-minimum-proof-mode)]))

(defn entitlement-table-leaf-v2 [context opening]
  (word-hash (concat-bytes (conj (context-words context opening table-leaf-domain)
                                 (bytes32 (:opening-salt opening) :opening-salt)))))

(defn allocation-claim-leaf-v2 [context opening]
  (word-hash (concat-bytes (context-words context opening claim-leaf-domain))))

(defn ordered-tree-root-v2 [leaves node-domain]
  (when (empty? leaves)
    (throw (ex-info "Merkle tree must contain at least one leaf" {:reason :empty-tree})))
  (loop [level (vec (map #(bytes32 % :leaf) leaves))]
    (if (= 1 (count level))
      (first level)
      (recur (mapv (fn [pair]
                     (let [left (first pair), right (or (second pair) left)]
                       (word-hash (concat-bytes
                                   [(word-hash (.getBytes node-domain java.nio.charset.StandardCharsets/UTF_8))
                                    left right]))))
                   (partition-all 2 level))))))

(defn- root-envelope-v2 [domain members-root count total-amount]
  (when (zero? count)
    (throw (ex-info "Entitlement count must be positive" {:reason :zero-entitlement-count})))
  (word-hash (concat-bytes [(word-hash (.getBytes domain java.nio.charset.StandardCharsets/UTF_8))
                            (bytes32 members-root :members-root)
                            (uint256-word count :count)
                            (uint256-word total-amount :total-amount)])))

(defn entitlement-table-root-v2 [members-root count total-amount]
  (root-envelope-v2 table-root-domain members-root count total-amount))

(defn allocation-claim-root-v2 [members-root count total-amount]
  (root-envelope-v2 claim-root-domain members-root count total-amount))

(defn bytes->hex [^bytes value]
  (str "0x" (hex value)))

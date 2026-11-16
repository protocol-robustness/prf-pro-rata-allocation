(ns prf.pro-rata.kernel
  "Pure exact-integer quota allocation kernel shared by PRF adapters.")

(defn non-negative-integer
  "Coerce a value to a non-negative bigint; nil becomes 0 and fractional or
   negative-hostile values fail explicitly."
  [x]
  (let [x (or x 0)]
    (if (integer? x)
      (max 0 (bigint x))
      (throw (ex-info "Expected an integer amount" {:value x})))))

(defn pro-rata-requests
  [amount prepared total-weight rounding ordering-policy]
  (let [floors (mapv (fn [{:keys [weight]}]
                       (quot (* amount weight) total-weight))
                     prepared)]
    (case rounding
      :floor
      floors

      :floor-with-largest-remainder
      (let [allocated (reduce +' 0 floors)
            shortage (- amount allocated)
            remainders (mapv (fn [{:keys [idx id weight ordering-key]}]
                               {:idx idx
                                :id id
                                :ordering-key ordering-key
                                :remainder (mod (* amount weight) total-weight)})
                             prepared)
            tie-key (case ordering-policy
                      :input-order :idx
                      :canonical-id :ordering-key)
            remainder-order (->> remainders
                                 (sort-by (juxt (comp - :remainder) tie-key))
                                 (map :idx)
                                 (take shortage)
                                 set)]
        (mapv (fn [idx allocated]
                (if (contains? remainder-order idx)
                  (inc allocated)
                  allocated))
              (range) floors)))))


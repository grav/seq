(ns seq.util)

(defn js-maplike->map [m]
  (->> (.keys js/Object m)
       (map (fn [k] [k (aget m k)]))
       (into {})))


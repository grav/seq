(ns seq.util)

(defn js-maplike->map [m]
  (->> (.keys js/Object m)
       (map (fn [k] [k (aget m k)]))
       (into {})))

(defn tracks [sequences outputs]
  (->> outputs
       (map (fn [o] {:name     (.-name o)
                     :id       (.-id o)
                     :device   o
                     :sequence (get sequences (.-id o))}))
       (sort-by :name)))
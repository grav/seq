(ns seq.util)

(defn js-iterable->vec [i]
  (-> (js/Array.from i)
      (js->clj)))

(defn js-maplike->map [m]
  (->> (.keys js/Object m)
       (map (fn [k] [k (aget m k)]))
       (into {})))


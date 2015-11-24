(ns seq.util)

(defn js-iterable->vec [i]
  (-> (js/Array.from i)
      (js->clj)))

(defn js-maplike->map [m]
  (let [keys (js-iterable->vec (.keys m))]
    (->> (map (fn [k] [k (.get m k)]) keys)
        (into {}))))


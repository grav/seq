(ns seq.scale)

(def penta
  '(0 3 5 7 10))

(def ionian
  '(0 2 4 5 7 9 11))

(def aeolian
  '(0 2 3 5 7 8 10))

(def half-tone
  (->> (range)
       (take 12)))

(def whole
  '(0 2 4 6 8 10))

(defn inf-scale [s]
  (-> (map (fn [i] (map #(+ % (* i 12)) s)) (range))
      (flatten)))


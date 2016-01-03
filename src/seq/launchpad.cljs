(ns seq.launchpad)

(defn is-launchpad? [d]
  "check if device is a launchpad"
  (and (= "Launchpad"
          (.-name d))
       (= "Novation DMS Ltd"
          (.-manufacturer d))))

(defn- inverse [n]
  (->> (* 8 (inc (int (/ n 8))))
       (- 64)
       (+ (mod n 8))))

(defn pad->midi [n]
  (let [m (inverse n)]
    (-> m
        (/ 8)
        (int)
        (* 8)
        (+ m))))

(def clear-all [176 0 0])

(def navigation
  {[176, 104, 127] [:y inc]                                 ;; up
   [176, 105, 127] [:y dec]                                 ;; down
   [176, 106, 127] [:x dec]                                 ;; left
   [176, 107, 127] [:x inc]                                 ;; right
   })
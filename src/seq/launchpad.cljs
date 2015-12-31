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
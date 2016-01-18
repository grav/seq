(ns seq.launchpad
  (:require [seq.core]
            [seq.scale]))

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



(defn crop-data
  ([data]
   (crop-data data 8 8))
  ([data width height]
   (->> data
        (take height)
        (map #(take width %)))))

(defn offset-data [x y data]
  (->> data
       (drop y)
       (map #(drop x %))))

(defn render [state lp data]
  (let [now (/ (.now (.-performance js/window)) 1000)
        diff (->> data
                  (map (fn [a b] (when (not= a b) b)) (or @state (repeat false))))]
    (when (nil? @state)
      (.send lp (clj->js clear-all) now))
    (reset! state data)
    (doseq [[i v] (map vector (range) diff)]
      (when (true? v)
        (.send lp #js [144, (pad->midi i), 0x30] now))
      (when (false? v)
        (.send lp #js [144, (pad->midi i), 0x00] now)))))


(defn sequence->lp-data [sequence]
  (map #(map (fn [v] (contains? (set v) %)) sequence)
       (seq.scale/inf-scale seq.scale/penta)))

(defn output-to-lp [app-state]
  (let [lp (first (filter is-launchpad? (:outputs (:midi @app-state))))
        lp-in (first (filter is-launchpad? (:inputs (:midi @app-state))))
        render (partial render (atom))]

    (set! lp-in.onmidimessage (fn [e] (when-let [[k f] (->> e.data
                                                            (js/Array.from)
                                                            (js->clj)
                                                            (get navigation))]
                                        (let [old-val (or (get-in @app-state [:launchpad k])
                                                          0)
                                              new-val (max 0 (f old-val))]
                                          (swap! app-state assoc-in [:launchpad k] new-val)))))

    (js/setInterval
      (fn [_]
        (let [{:keys [x y]
               :or   {x 0
                      y 0}} (:launchpad @app-state)]
          (render lp
                  (->> (:sequences @app-state)
                       (vals)
                       (first)
                       (vals)
                       (first)
                       (sequence->lp-data)
                       (offset-data x y)
                       (crop-data)
                       (flatten)))))
      100)))

(comment

  )

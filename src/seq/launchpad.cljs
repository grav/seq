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

(defn midi->pad [note]
  (-> (+ (mod note 16)
         (* 8 (int (/ note 16))))
      (inverse)))

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

(defn right-side-arrow [[a b c]]
  (when (and (= a 144)
             (= c 127)
             (zero? (mod (- b 8) 16)))
    (/ (- b 8) 16)))

(defn crop-data
  ([data]
   (crop-data data 8 8))
  ([data width height]
   (->> data
        (take height)
        (map #(take width %)))))

(defn offset-data [data x y]
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
       (range) #_(seq.scale/inf-scale seq.scale/penta)))

(defn pad-note [[a b c]]
  (when (and (= a 144)
             (>= b 0)
             (<= b 119)
             (< (mod b 16) 8)
             (= c 127))
    b))

(defn output-to-lp [app-state]
  (let [lp (first (filter is-launchpad? (:outputs (:midi @app-state))))
        lp-in (first (filter is-launchpad? (:inputs (:midi @app-state))))
        lp-state (atom)
        render (partial render lp-state)]

    (prn "lp:" lp "lp-in:" lp-in)

    (set! lp-in.onmidimessage (fn [e]
                                ;; navigation
                                (let [midi-msg (->> e.data
                                                    (js/Array.from)
                                                    (js->clj))]
                                  (when-let [[k f] (get navigation midi-msg)]
                                    (let [old-val (or (get-in @app-state [:launchpad k])
                                                      0)
                                          new-val (max 0 (f old-val))]
                                      (swap! app-state assoc-in [:launchpad k] new-val)))

                                  ;; right-side arrows
                                  (when-let [index (right-side-arrow midi-msg)]
                                    (swap! app-state assoc-in [:launchpad :index] index))

                                  (when-let [note (pad-note midi-msg)]
                                    (let [pad-note (midi->pad note)
                                          step-number (mod pad-note 8)
                                          k (int (/ pad-note 8))
                                          {:keys [sequences launchpad]} @app-state
                                          {:keys [x y index]
                                           :or {x 0 y 0 index 0}} launchpad
                                          idx (min index (dec (count sequences)))
                                          outputs (->> sequences
                                                       (sort-by key)
                                                       (map first)
                                                       (vec))]
                                      (when-let [output (get outputs idx)]
                                        (seq.core/step-clicked output (+ x step-number) (+ y k))))))))

    (js/setInterval
      (fn [_]
        (let [{:keys [x y index]
               :or   {x     0
                      y     0
                      index 0}} (:launchpad @app-state)
              sequences (->> (:sequences @app-state)
                             (sort-by key)
                             (map second)
                             (map :sequence)
                             (vec))
              sequence (->> (min index (dec (count sequences)))
                            (get sequences))]
          (render lp
                  (-> sequence
                      (sequence->lp-data)
                      (offset-data x y)
                      (crop-data)
                      (flatten)))))
      100)))


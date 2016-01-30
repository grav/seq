(ns seq.core
  (:require [seq.midi :as m]
            [reagent.core :as r]
            [seq.launchpad :as lp]))

(defn tracks [{:keys [midi sequences]}]
  (->> (:outputs midi)
       (remove lp/is-launchpad?)
       (map (fn [o] {:name     (.-name o)
                     :id       (.-id o)
                     :device   o
                     :sequence (get sequences (.-id o))}))
       (sort-by :name)))

;; TODO - how do we do this in a node environment
(defonce app-state (r/atom {:bpm       120
                            :sustain   0.12
                            :sequences {}}))
(def latency 1)

(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))

(defn ding
  [out channel v start t]
  (let [t1 (* 1000 start)
        t2 (* 1000 (+ start t))]
    (.send out #js [(+ channel 144), v, 0x30] t1)
    (.send out #js [(+ channel 128), v, 0x00] t2)))


(defn- next-notes [{:keys [sequence transpose] :or {transpose 0}} p now time spt]
  (->> sequence
       (map (fn [notes] (map #(+ transpose %) notes)))
       (repeat)
       (apply concat)
       (map vector (range))
       (drop p)
       (map (fn [[i v]] [(- i p) v]))
       (map (fn [[i v]] [(* spt i) v]))
       (map (fn [[i v]] [(+ time i) v]))
       (take-while (fn [[i _]] (< i (+ now (* 1.5 latency)))))))

(defn play-sequence! [beat time]
  (let [p (mod beat 16)
        spt (secs-per-tick (:bpm @app-state))
        now (/ (.now (.-performance js/window)) 1000)
        new-notes (for [{:keys [device sequence]} (tracks @app-state)
                        :when (:sequence sequence)]
                    {:device     device
                     :next-notes (next-notes sequence p now time spt)
                     :channel    (or (:channel sequence) 0)})]
    (swap! app-state assoc :position p)

    (doseq [{:keys [device next-notes channel]} new-notes]
      (doseq [[i vs] next-notes]
        (doseq [v vs]
          (ding device channel (+ 0x24 v) i (:sustain @app-state)))))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (or (->> (map count (map :next-notes new-notes))
                                             (apply max))
                                        0))
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(seq.core/play-sequence! beat' time') (* latency 1000)))))



(defn handle-midi-select [state-key selection-key]
  (fn [val]
    (let [selected (->> (get-in @app-state [:midi state-key])
                        (filter #(= (.-id %) val))
                        (first))]
      (swap! app-state assoc-in [:midi selection-key] selected))))

(defn handle-val-change [output key val]
  (swap! app-state assoc-in [:sequences output key] val))

(defn step-clicked [output step-number key]

  (let [seq (or (get-in @app-state [:sequences output :sequence])
                (vec (repeat 16 [])))
        keys (get seq step-number)
        new-keys (if (contains? (set keys) key)
                   (remove #(= % key) keys)
                   (cons key keys))
        new-seq (assoc seq step-number new-keys)]
    (swap! app-state assoc-in [:sequences output :sequence] new-seq)))

(defn update-launchpad [l]
  (swap! app-state assoc :launchpad l))

(defn setup-midi! []
  (let [save-devices! (fn [ma]
                        (let [{:keys [launchpad]} @app-state
                              midi {:inputs  (-> (.values ma.inputs)
                                                 es6-iterator-seq)
                                    :outputs (-> (.values ma.outputs)
                                                 es6-iterator-seq)}]
                          (swap! app-state assoc :midi midi)
                          (js/clearInterval (:render-callback-id launchpad))
                          (let [lp-in (first (filter seq.launchpad/is-launchpad? (:inputs midi)))
                                lp-out (first (filter seq.launchpad/is-launchpad? (:outputs midi)))]
                            (when (and lp-in lp-out)
                              (->> (seq.launchpad/init #(tracks @app-state) #(:launchpad @app-state) update-launchpad lp-in lp-out seq.core/step-clicked)
                                   (swap! app-state assoc-in [:launchpad :render-callback-id]))))))]
    (-> (js/navigator.requestMIDIAccess)
        (.then (fn [ma]
                 (save-devices! ma)
                 ;; Update devices continously                
                 (set! (.-onstatechange ma) #(save-devices! ma)))))))

(defn nudge [id v]
  (when-let [seq (get-in @app-state [:sequences id :sequence])]
    (swap! app-state assoc-in [:sequences id :sequence] (->> seq
                                                             (repeat)
                                                             (apply concat)
                                                             (drop (- 16 v))
                                                             (take 16)
                                                             vec))))


(defonce go
         (play-sequence! 0 0))


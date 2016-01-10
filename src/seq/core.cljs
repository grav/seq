(ns seq.core
  (:require [seq.midi :as m]
            [seq.launchpad :as lp]
            [reagent.core :as r]))



;; TODO - how do we do this in a node environment
(defonce app-state (r/atom {:bpm       120
                            :sustain   0.12
                            :sequences {}}))
(def latency 0.1)

(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))

(defn ding
  [out v start t]
  (let [{:keys [channel transpose]
         :or   {channel 0 transpose 0}} (get-in @app-state [:sequences (.-id out)])
        t1 (* 1000 start)
        t2 (* 1000 (+ start t))]
    (.send out #js [(+ channel 144), (+ v transpose), 0x30] t1)
    (.send out #js [(+ channel 128), (+ v transpose), 0x00] t2)))


(defn play-sequence! [beat time]
  #_(swap! app-state assoc :pointer (mod beat 16))
  (let [lp (first (->> (get-in @app-state [:midi :outputs])
                       (filter lp/is-launchpad?)))
        p (mod beat 16)
        spt (secs-per-tick (:bpm @app-state))
        now (/ (.now (.-performance js/window)) 1000)
        new-notes (for [[k {:keys [sequence]}] (->> (get-in @app-state [:midi :outputs])
                                                    (map #(.-id %))
                                                    (select-keys (get-in @app-state [:sequences])))
                        :when sequence]
                    [k (->> sequence
                            (repeat)
                            (apply concat)
                            (map vector (range))
                            (drop p)
                            (map (fn [[i v]] [(- i p) v]))
                            (map (fn [[i v]] [(* spt i) v]))
                            (map (fn [[i v]] [(+ time i) v]))
                            (take-while (fn [[i _]] (< i (+ now (* 1.5 latency))))))])]
    (swap! app-state assoc :position p)

    #_(when lp
        (ding lp (lp/pad->midi p) now 0.1))

    (doseq [[k s] new-notes]
      (when-let [out (-> (get-in @app-state [:midi :outputs])
                         (m/get-output k))]
        (doseq [[i vs] s]
          (doseq [v vs]
            (ding out (+ 0x24 v) i (:sustain @app-state))))))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (or (->> (map count (map second new-notes))
                                             (apply max))
                                        0))
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(seq.core/play-sequence! beat' time') (* latency 1000)))))

(defn setup-midi! []
  (let [save-devices! (fn [ma]
                        (swap! app-state update-in [:midi] merge {:inputs  (-> (.values ma.inputs)
                                                                               es6-iterator-seq)
                                                                  :outputs (-> (.values ma.outputs)
                                                                               es6-iterator-seq)}))]
    (-> (js/navigator.requestMIDIAccess)
        (.then (fn [ma]
                 (save-devices! ma)
                 ;; Update devices continously
                 (set! (.-onstatechange ma) #(save-devices! ma)))))))

(defn handle-midi-select [state-key selection-key]
  (fn [val]
    (let [selected (->> (get-in @app-state [:midi state-key])
                        (filter #(= (.-id %) val))
                        (first))]
      (swap! app-state assoc-in [:midi selection-key] selected))))

(defn handle-val-change [output key val]
  (swap! app-state assoc-in [:sequences output key] val))

(defn step-clicked [output step-number key selected?]
  (let [seq (or (get-in @app-state [:sequences output :sequence])
                (vec (repeat 16 [])))
        keys (->> (get seq step-number)
                  (cons key)
                  (remove #(when selected? (= % key))))
        new-seq (assoc seq step-number keys)]
    (swap! app-state assoc-in [:sequences output :sequence] new-seq)))

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


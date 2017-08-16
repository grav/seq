(ns seq.core
  (:require [seq.midi :as m]
            [seq.launchpad :as lp]
            [seq.util :as util]))


(defn- next-notes [latency {:keys [sequence transpose] :or {transpose 0}} beat now time spt]
  (->> sequence
       ;; TODO - doesn't belong here - it's for transposing?
       (map (fn [notes] (->> notes
                             (map (fn [{:keys [note]
                                        :as   v}]
                                    (assoc v :note (+ transpose note)))))))
       (repeat)
       (apply concat)
       (drop (mod beat (count sequence)))
       (map-indexed (fn [i v] [(+ time (* i spt)) v]))
       (take-while (fn [[t _]] (< t (+ now (* 1.5 latency)))))))

(defn play-sequences! [latency bpm app-state now beat time]
  ":notes - notes to be immediately queued up
   :beat :time - pointers to next "
  (let [{:keys [midi sequences]} app-state
        spt (util/secs-per-tick bpm)
        sequences (for [{:keys [device sequence]} (->> (:outputs midi)
                                                       (remove lp/is-launchpad?)
                                                       (util/tracks sequences))
                        :when (:sequence sequence)]
                    {:device  device
                     :notes   (next-notes latency sequence beat now time spt)
                     :channel (or (:channel sequence) 0)})

        diff (- now time)
        c (max (int (/ diff spt)) (or (->> (map count (map :notes sequences))
                                           (apply max))
                                      0))
        beat' (+ c beat)
        time' (+ (* spt c) time)]
    {:beat      beat'
     :time      time'
     :sequences sequences}))



(defn handle-midi-select [app-state state-key selection-key]
  (fn [val]
    (let [selected (->> (get-in @app-state [:midi state-key])
                        (filter #(= (.-id %) val))
                        (first))]
      (swap! app-state assoc-in [:midi selection-key] selected))))

(defn handle-val-change [app-state output key val]
  (swap! app-state assoc-in [:sequences output key] val))

(defn step-clicked [app-state output step-number k a s]
  (let [seq (or (get-in @app-state [:sequences output :sequence])
                (vec (repeat 16 [])))
        notes (get seq step-number)
        new-keys (cond->> (remove (fn [{:keys [note]}] (= note k)) notes)
                          (not= a :off) (cons {:note    k
                                               :sustain s}))
        new-seq (assoc seq step-number new-keys)]
    (prn "k" k)
    (swap! app-state assoc-in [:sequences output :sequence] new-seq)))

(defn setup-midi! [app-state requestMIDIAccess now-fn]
  (reset! app-state {:bpm       120
                     :sustain   0.12
                     :sequences {}})
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
                              (->> (seq.launchpad/init app-state now-fn lp-in lp-out (partial seq.core/step-clicked app-state))
                                   (swap! app-state assoc-in [:launchpad :render-callback-id]))))))]
    (-> (requestMIDIAccess)
        (.then (fn [ma]
                 (save-devices! ma)
                 ;; Update devices continously                
                 (set! (.-onstatechange ma) #(save-devices! ma)))))))

(defn nudge [app-state id v]
  (when-let [seq (get-in @app-state [:sequences id :sequence])]
    (swap! app-state assoc-in [:sequences id :sequence] (->> seq
                                                             (repeat)
                                                             (apply concat)
                                                             (drop (- 16 v))
                                                             (take 16)
                                                             vec))))



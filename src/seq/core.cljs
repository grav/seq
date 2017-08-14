(ns seq.core
  (:require [seq.midi :as m]
            [seq.launchpad :as lp]
            [seq.util :as util]))


(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))

(defn- next-notes [latency {:keys [sequence transpose] :or {transpose 0}} p now time spt]
  (->> sequence
       ;; TODO - doesn't belong here - it's for transposing?
       (map (fn [notes] (->> notes
                             (map (fn [{:keys [note]
                                        :as   v}]
                                    (assoc v :note (+ transpose note)))))))
       (repeat)
       (apply concat)
       (drop p)
       (map-indexed (fn [i v] [(+ time (* i spt)) v]))
       (take-while (fn [[t _]] (< t (+ now (* 1.5 latency)))))))

(defn play-sequence! [latency app-state now beat time]
  ":notes - notes to be immediately queued up
   :beat :time - pointers to next "
  (let [{:keys [bpm midi sequences]} app-state
        p (mod beat 16)
        spt (secs-per-tick bpm)
        new-notes (for [{:keys [device sequence]} (->> (:outputs midi)
                                                       (remove lp/is-launchpad?)
                                                       (util/tracks sequences))
                        :when (:sequence sequence)]
                    {:device     device
                     :next-notes (next-notes latency sequence p now time spt)
                     :channel    (or (:channel sequence) 0)})
        notes (for [{:keys [device next-notes channel]} new-notes
                    [i vs] next-notes
                    {:keys [note sustain]} vs]
                [device channel (+ 0x24 note) i sustain])
        diff (- now time)
        c (max (int (/ diff spt)) (or (->> (map count (map :next-notes new-notes))
                                           (apply max))
                                      0))
        beat' (+ c beat)
        time' (+ (* spt c) time)]
    {:beat     beat'
     :time     time'
     :position p
     :notes    notes}))



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



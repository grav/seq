(ns seq.core
  (:require [seq.midi :as m]
            [seq.launchpad :as lp]
            [seq.util :as util]))

(def latency 0.3)

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
       (map (fn [notes] (->> notes
                             (map (fn [{:keys [note]
                                        :as   v}] (assoc v :note (+ transpose note)))))))
       (repeat)
       (apply concat)
       (map vector (range))
       (drop p)
       (map (fn [[i v]] [(- i p) v]))
       (map (fn [[i v]] [(* spt i) v]))
       (map (fn [[i v]] [(+ time i) v]))
       (take-while (fn [[i _]] (< i (+ now (* 1.5 latency)))))))

(defn play-sequence! [app-state now-fn beat time]
  (let [{:keys [bpm midi sequences]} @app-state
        p (mod beat 16)
        spt (secs-per-tick bpm)
        now (/ (now-fn) 1000)
        new-notes (for [{:keys [device sequence]} (->> (:outputs midi)
                                                       (remove lp/is-launchpad?)
                                                       (util/tracks sequences))
                        :when (:sequence sequence)]
                    {:device     device
                     :next-notes (next-notes sequence p now time spt)
                     :channel    (or (:channel sequence) 0)})]
    (swap! app-state assoc :position p)

    (doseq [{:keys [device next-notes channel]} new-notes
            [i vs] next-notes
            {:keys [note sustain]} vs]
      (ding device channel (+ 0x24 note) i sustain))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (or (->> (map count (map :next-notes new-notes))
                                             (apply max))
                                        0))
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(seq.core/play-sequence! app-state now-fn beat' time') (* latency 1000)))))



(defn handle-midi-select [app-state state-key selection-key]
  (fn [val]
    (let [selected (->> (get-in @app-state [:midi state-key])
                        (filter #(= (.-id %) val))
                        (first))]
      (swap! app-state assoc-in [:midi selection-key] selected))))

(defn handle-val-change [app-state output key val]
  (swap! app-state assoc-in [:sequences output key] val))

(defn step-clicked [app-state output step-number k]
  (let [seq (or (get-in @app-state [:sequences output :sequence])
                (vec (repeat 16 [])))
        notes (get seq step-number)
        new-keys (if ((set (map :note notes)) k)
                   (remove (fn [{:keys [note]}] (= note k)) notes)
                   (cons {:note    k
                          :sustain 0.3} notes))
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



(ns seq.transform
  (:require [seq.util :as util]
            [leipzig.melody :as mel]
            [leipzig.chord :as chord]
            [leipzig.scale :as scale]))

(defn set-s [app-state idx sequence]
  (let [seq-id (nth (->> (:outputs (:midi @app-state))
                         (map #(.-id %))
                         vec) idx)]
    (swap! app-state assoc-in [:sequences seq-id :sequence] (vec sequence))))

(defn foo->
  [notes]
  "example: [0 * * * 0 * * * 0 * * * 0 * * * ]
   or:      [[0 4] * * 1 *]"
  (->> notes
       (map #(if (vector? %) % (vector %)))
       (map #(for [n %
                   :when (number? n)]
               {:note n}))))

(comment
  (->> (seq.transform/foo-> [0 * * * 0 * * * 0 * * * 0 * * *])
       (seq.transform/set-s seq.web/app-state 3)))

(defn leip-> [l & [{:keys [length bpm]
                    :or   {length (->> l
                                       (map (fn [{:keys [time duration]}]
                                              (+ time duration)))
                                       (apply max)
                                       (* 4))
                           bpm    120}
                    :as   opts}]]
  "example: ({:pitch 65 :time 0 :duration 1} {:pitch 69 :time 0 :duration 1})"
  (let [steps (->> (for [{:keys [pitch time duration]} l]
                     {:note    (- pitch 48)
                      :step    (* 4 time)
                      :sustain (-> (util/secs-per-tick bpm)
                                   (* duration)
                                   (* 3.9))})
                   (group-by :step))]
    (for [i (range length)]
      (or (get steps i) '()))))


(comment
  (let [phrase (->> (mel/phrase
                      [1 1]
                      [(-> chord/triad (chord/root 3))
                       (-> chord/triad (chord/inversion 2) (chord/root 4))])
                    (mel/then
                      (->> (mel/phrase [1]
                                       [(-> chord/triad (chord/inversion 1) (chord/root 0))])
                           (mel/where :pitch (partial + 7))))
                    (mel/where :pitch (comp scale/C scale/major))
                    )]
    (->> (leip-> phrase {:length 16})
         (set-s seq.web/app-state 2))))
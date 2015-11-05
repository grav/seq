(ns ^:figwheel-always seq.core
    (:require))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)


(defonce context (js/AudioContext.))

(defonce gain (.createGain context))

(.connect gain (.-destination context))

(set! (.. gain -gain -value) 0.3)

(def bpm 140)

(def secs-per-tick #(/ (/ 1 (/ bpm 60)) 4))

(defn inf-seq [sequence]
  (-> (map (fn [l i] (map #(+ % (* 16 i)) l)) (repeatedly (fn [_] sequence)) (range))
      (flatten)))

(defn beep
  ([f]
    (beep f 0.05))
  ([f t]
    (beep f (.-currentTime context) t))
  ([f start t]
   (let [osc (.createOscillator context)]
     (.connect osc gain)
     (set! (.. osc -frequency -value) f)
     (.start osc start)
     (.stop osc (+ start t)))))

(defn play-sequence! [beat time sequence]
  (let [spt (secs-per-tick)
        now (.-currentTime context)
        new-notes (->> sequence
                       (map #(- % beat))
                       (map #(* spt %))
                       (map #(+ time %))
                       (take-while #(< % (+ now 0.75))))]

    (doseq [n new-notes]
      (beep 400 n 0.05))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (count new-notes))

          _ (prn "diff" diff)
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(play-sequence! beat' time' (drop (count new-notes) sequence)) 500))))


(def s (atom [0 3 6 10 12]))



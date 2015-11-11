(ns ^:figwheel-always seq.core
    (:require [reagent.core :as r]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)

(defonce app-state (r/atom {:bpm 120
                          :sequence [0 3 6 10 12]
                          :title "Hello"}))

(defonce context (js/AudioContext.))

(defonce gain (.createGain context))

(.connect gain (.-destination context))

(set! (.. gain -gain -value) 0.3)

(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))

(defn inf-seq [f]
  (let [length 16
        f' #(filter (partial > length) (f))]
    (-> (map (fn [l i] (map #(+ % (* length i)) l)) (repeatedly f') (range))
       (flatten))))

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
  (swap! app-state assoc :pointer (mod beat 16))
  (let [spt (secs-per-tick (:bpm @app-state))
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


(defn step [{:keys [selected? playing?]}]
  [:div {:style {:background-color (if playing? "yellow" "black")
                 :height           46
                 :width            46
                 :padding          2}}
   [:div {:style {:background-color (if selected? "red" "white")
                  :height           "100%"
                  :width            "100%"}}]])

(defn header [{:keys [title]}]
  [:div title])

(defn root [_]
  (let [{:keys [title sequence pointer]} @app-state]
    [:div
    [header {:title title}]
    [:div {:style {:display "flex"}}
     (let [bool-seq (map #(contains? (set sequence) %) (range 16))]
       (map (fn [s i] [:div {:key i} [step {:selected? s :playing? (= i pointer) }]]) bool-seq (range)))]]))

(r/render [root] (js/document.getElementById "app"))

(comment
  (play-sequence! 0 0 (inf-seq #(:sequence @app-state))))
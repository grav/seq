(ns ^:figwheel-always seq.core
  (:require [reagent.core :as r]
            [goog.object :as g]
            [seq.util :as u]
            [seq.midi :as m]
            [seq.ui :as ui]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defonce app-state (r/atom {:bpm      120
                            :sequence [[0] [] [] [0] [] [] [0] [] [] [] [0] [] [0] [] [] []]
                            :title    "Hello"}))




(defn secs-per-tick
  [bpm]
  (/ (/ 1 (/ bpm 60)) 4))


(defn ding
  [v start t]
  (when-let [out (get-in @app-state [:midi :out])]
    (.send out #js [0x90, v, 0x30] (* 1000 start))
    (.send out #js [0x89, v, 0x30] (* 1000 (+ start t)))))


(defn play-sequence! [beat time]
  #_(swap! app-state assoc :pointer (mod beat 16))
  (let [p (mod beat 16)
        spt (secs-per-tick (:bpm @app-state))
        now (/ (.now (.-performance js/window)) 1000)
        new-notes (->> (:sequence @app-state)
                       (repeat)
                       (apply concat)
                       (map vector (range))
                       (drop p)
                       (map (fn [[i v]] [(- i p) v]))
                       (map (fn [[i v]] [(* spt i) v]))
                       (map (fn [[i v]] [(+ time i) v]))
                       (take-while (fn [[i v]] (< i (+ now 0.75)))))]
    (doseq [[i vs] new-notes]
      (doseq [v vs]
        (ding (+ 0x30 v) i 0.12)))
    (let [diff (- now time)
          c (max (int (/ diff spt)) (count new-notes))
          _ 42#_(prn "diff" diff)
          beat' (+ c beat)
          time' (+ (* spt c) time)]
      (js/setTimeout #(play-sequence! beat' time') 500))))

(defn setup-midi! []
  (let [save-devices! (fn [ma]
                        (swap! app-state update-in [:midi] merge {:inputs  (m/inputs ma)
                                                                  :outputs (m/outputs ma)}))]
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

(defn step-clicked [step-number key selected?]
  (let [keys (->> (get-in @app-state [:sequence step-number])
                  (cons key)
                  (remove #(when selected? (= % key))))]
    (swap! app-state assoc-in [:sequence step-number] keys)))



(r/render [(ui/create-root app-state
                           {:did-mount     setup-midi!
                            :step-clicked  step-clicked
                            :handle-select handle-midi-select})] (js/document.getElementById "app"))

(defonce go
         (play-sequence! 0 0))

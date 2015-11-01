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

(defonce my-seq [0 3 6 10 12])

(def bpm 140)

(def secs-per-tick (/ (/ 1 (/ bpm 60)) 4))

(defonce state (atom {:time 0
                      :beat 0
                      :steps [0 3 6 10 12]
                      :notes []}))


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



(defn update-time! []
  (let [now (.-currentTime context)]
    (let [{:keys [beat time]} @state
          next-time (+ time secs-per-tick)]
      (let [delta-beat (-> (- now time)
                          (/ secs-per-tick)
                          (int))]
        (when (> delta-beat 0)
          (swap! state assoc :time next-time :beat (+ beat delta-beat)))))))

(defonce update-time-cb (js/setInterval update-time! 100))

(defn debug [a]
  (prn "debug" a)
  a)


(defn play! []
  (let [{:keys [beat time notes]} @state
        play-notes (->> (map #(- % beat) notes)
                        (debug)
                        (map #(* secs-per-tick %))
                        (take-while #(< % 0.15))
                        (map #(+ time %)))]
    (when (not (empty? play-notes))
      (doseq [n play-notes]
        (beep 400 n 0.05))
      (swap! state assoc :notes (drop (count play-notes) notes)))))

#_(defonce play-cb (js/setInterval play! 100))


(defn update-sequence! []
  (let [{:keys [beat time steps]} @state
        iteration (-> (/ beat 16)
                      (int))
        new-notes (->> (concat steps (map #(+ 16 %) steps))
                   (map #(+ % (* 16 iteration)))
                   (filter #(> % beat))
                   (map #(- % beat))
                   (map #(* secs-per-tick %))
                   (take-while #(< % 0.75))
                   (map #(+ time %)))]

    (doseq [n new-notes]
      #_(beep 400 n 0.05))
    #_(swap! state  (fn [{:keys[notes] :as state}]
                    (debug (assoc state :notes (concat notes new-notes)))))))

(defonce update-cb (js/setInterval update-sequence! 500))


;(defn start []
;  (.connect osc gain)
;  (.connect gain (.-destination context))
;  (set! (.. osc -frequency -value) 440)
;
;  (.start osc 0))
;
;

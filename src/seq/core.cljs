(ns ^:figwheel-always seq.core
  (:require [om.core :as om]
            [om.dom :as dom]))

(enable-console-print!)

(println "Edits to this text should show up in your developer console.")

;; define your app data so that it doesn't get over-written on reload

(defn on-js-reload []
  ;; optionally touch your app-state to force rerendering depending on
  ;; your application
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )

(defonce app-state (atom {:bpm      120
                          :sequence [0 3 6 10 12]}))

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

(defn widget [{:keys [sequence foo]} owner]
  (reify
    om/IRender
    (render [this]
      (prn "render")
      (let [anim-name (str "blink" (mod (:anim-id (om/get-state owner)) 2))]
        (dom/div nil
                 (dom/div (clj->js {:style {:animationName           anim-name
                                            :animationDuration       "1s"
                                            :animationIterationCount 1}})
                          "Sequence:"
                          (apply dom/div nil sequence))
                 (dom/div nil
                          "Foo: "
                          (str foo)))))

    om/IWillReceiveProps
    (will-receive-props [this next-props]
      (prn "will-update")
      (when (not= sequence (:sequence next-props))
        (let [anim-id (inc (:anim-id (om/get-state owner)))]
          (om/set-state! owner {:anim-id anim-id}))))))


(om/root widget app-state
         {:target (. js/document (getElementById "app"))})
